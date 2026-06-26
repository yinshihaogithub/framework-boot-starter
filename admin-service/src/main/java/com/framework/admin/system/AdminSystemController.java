package com.framework.admin.system;

import com.framework.admin.audit.AdminAuditService;
import com.framework.admin.system.AdminSystemModels.AdminUser;
import com.framework.admin.system.AdminSystemModels.ConfigItem;
import com.framework.admin.system.AdminSystemModels.ConfigRequest;
import com.framework.admin.system.AdminSystemModels.DictItem;
import com.framework.admin.system.AdminSystemModels.DictItemRequest;
import com.framework.admin.system.AdminSystemModels.DictType;
import com.framework.admin.system.AdminSystemModels.DictTypeRequest;
import com.framework.admin.system.AdminSystemModels.Dept;
import com.framework.admin.system.AdminSystemModels.DeptRequest;
import com.framework.admin.system.AdminSystemModels.Menu;
import com.framework.admin.system.AdminSystemModels.MenuRequest;
import com.framework.admin.system.AdminSystemModels.ResetPasswordRequest;
import com.framework.admin.system.AdminSystemModels.Role;
import com.framework.admin.system.AdminSystemModels.RoleRequest;
import com.framework.admin.system.AdminSystemModels.RoleMenuRequest;
import com.framework.admin.system.AdminSystemModels.Tenant;
import com.framework.admin.system.AdminSystemModels.TenantRequest;
import com.framework.admin.system.AdminSystemModels.UserCreateRequest;
import com.framework.admin.system.AdminSystemModels.UserStatusRequest;
import com.framework.admin.system.AdminSystemModels.UserUpdateRequest;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.crypto.util.PasswordUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/system")
@Tag(name = "系统管理", description = "用户、角色、菜单、字典和参数")
public class AdminSystemController {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final AdminSystemRepository repository;
    private final AdminAuditService auditService;

    public AdminSystemController(AdminSystemRepository repository, AdminAuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Operation(summary = "租户列表")
    @GetMapping("/tenants")
    public Result<List<Tenant>> tenants() {
        return Result.success(repository.listTenants());
    }

    @Operation(summary = "新增租户")
    @PostMapping("/tenants")
    public Result<Long> createTenant(@RequestBody TenantRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateTenant(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        Long tenantId = repository.createTenant(request);
        auditService.success(servletRequest, "系统管理", "新增租户", "INSERT",
                auditService.params("tenantId", tenantId, "tenantCode", request.getTenantCode()));
        return Result.success(tenantId);
    }

    @Operation(summary = "更新租户")
    @PutMapping("/tenants/{id}")
    public Result<String> updateTenant(@PathVariable Long id, @RequestBody TenantRequest request,
                                       HttpServletRequest servletRequest) {
        Result<String> validation = validateTenant(request);
        if (validation != null) {
            return validation;
        }
        repository.updateTenant(id, request);
        auditService.success(servletRequest, "系统管理", "更新租户", "UPDATE",
                auditService.params("tenantId", id, "tenantCode", request.getTenantCode(), "status", request.getStatus()));
        return Result.success("已更新");
    }

    @Operation(summary = "删除租户")
    @DeleteMapping("/tenants/{id}")
    public Result<String> deleteTenant(@PathVariable Long id, HttpServletRequest servletRequest) {
        if (id == 1L) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "默认租户不能删除");
        }
        if (repository.countUsersByTenant(id) > 0) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "租户下存在用户，不能删除");
        }
        repository.deleteTenant(id);
        auditService.success(servletRequest, "系统管理", "删除租户", "DELETE",
                auditService.params("tenantId", id));
        return Result.success("已删除");
    }

    @Operation(summary = "部门树")
    @GetMapping("/depts")
    public Result<List<Dept>> depts(@RequestParam(required = false) Long tenantId) {
        return Result.success(repository.listDeptTree(tenantId));
    }

    @Operation(summary = "新增部门")
    @PostMapping("/depts")
    public Result<Long> createDept(@RequestBody DeptRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateDept(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        Long deptId = repository.createDept(request);
        auditService.success(servletRequest, "系统管理", "新增部门", "INSERT",
                auditService.params("deptId", deptId, "tenantId", request.getTenantId(), "deptName", request.getDeptName()));
        return Result.success(deptId);
    }

    @Operation(summary = "更新部门")
    @PutMapping("/depts/{id}")
    public Result<String> updateDept(@PathVariable Long id, @RequestBody DeptRequest request,
                                     HttpServletRequest servletRequest) {
        Result<String> validation = validateDept(request);
        if (validation != null) {
            return validation;
        }
        if (id.equals(request.getParentId())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "上级部门不能选择自己");
        }
        repository.updateDept(id, request);
        auditService.success(servletRequest, "系统管理", "更新部门", "UPDATE",
                auditService.params("deptId", id, "tenantId", request.getTenantId(), "deptName", request.getDeptName(),
                        "status", request.getStatus()));
        return Result.success("已更新");
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/depts/{id}")
    public Result<String> deleteDept(@PathVariable Long id, HttpServletRequest servletRequest) {
        if (id == 1L) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "总部部门不能删除");
        }
        repository.deleteDept(id);
        auditService.success(servletRequest, "系统管理", "删除部门", "DELETE",
                auditService.params("deptId", id));
        return Result.success("已删除");
    }

    @Operation(summary = "用户列表")
    @GetMapping("/users")
    public Result<PageResult<AdminUser>> users(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "20") int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        List<AdminUser> records = repository.listUsers(keyword, status, safePageNum, safePageSize);
        records.forEach(user -> user.setPasswordHash(null));
        return Result.success(PageResult.of(records, repository.countUsers(keyword, status), safePageNum, safePageSize));
    }

    @Operation(summary = "新增用户")
    @PostMapping("/users")
    public Result<Long> createUser(@RequestBody UserCreateRequest request, HttpServletRequest servletRequest) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "用户名和密码不能为空");
        }
        Long userId = repository.createUser(request, PasswordUtils.hash(request.getPassword()));
        auditService.success(servletRequest, "系统管理", "新增用户", "INSERT",
                auditService.params("userId", userId, "username", request.getUsername(), "roleIds", request.getRoleIds()));
        return Result.success(userId);
    }

    @Operation(summary = "更新用户")
    @PutMapping("/users/{id}")
    public Result<String> updateUser(@PathVariable Long id, @RequestBody UserUpdateRequest request,
                                     HttpServletRequest servletRequest) {
        if (request == null) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "用户信息不能为空");
        }
        if (!isValidStatus(request.getStatus())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "状态只能是 ENABLED 或 DISABLED");
        }
        repository.updateUser(id, request);
        auditService.success(servletRequest, "系统管理", "更新用户", "UPDATE",
                auditService.params("userId", id, "nickname", request.getNickname(), "status", request.getStatus(),
                        "roleIds", request.getRoleIds()));
        return Result.success("已更新");
    }

    @Operation(summary = "更新用户状态")
    @PutMapping("/users/{id}/status")
    public Result<String> updateUserStatus(@PathVariable Long id, @RequestBody UserStatusRequest request,
                                           HttpServletRequest servletRequest) {
        String status = request == null || isBlank(request.getStatus()) ? "DISABLED" : request.getStatus();
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "状态只能是 ENABLED 或 DISABLED");
        }
        repository.updateUserStatus(id, status);
        auditService.success(servletRequest, "系统管理", "更新用户状态", "UPDATE",
                auditService.params("userId", id, "status", status));
        return Result.success("已更新");
    }

    @Operation(summary = "重置密码")
    @PutMapping("/users/{id}/password")
    public Result<String> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest request,
                                        HttpServletRequest servletRequest) {
        if (request == null || isBlank(request.getPassword())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "密码不能为空");
        }
        repository.resetPassword(id, PasswordUtils.hash(request.getPassword()));
        auditService.success(servletRequest, "系统管理", "重置用户密码", "UPDATE",
                auditService.params("userId", id));
        return Result.success("已重置");
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/users/{id}")
    public Result<String> deleteUser(@PathVariable Long id, HttpServletRequest servletRequest) {
        if (id == 1L) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "内置管理员不能删除");
        }
        repository.deleteUser(id);
        auditService.success(servletRequest, "系统管理", "删除用户", "DELETE",
                auditService.params("userId", id));
        return Result.success("已删除");
    }

    @Operation(summary = "角色列表")
    @GetMapping("/roles")
    public Result<List<Role>> roles() {
        return Result.success(repository.listRoles());
    }

    @Operation(summary = "新增角色")
    @PostMapping("/roles")
    public Result<Long> createRole(@RequestBody RoleRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateRole(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        Long roleId = repository.createRole(request);
        auditService.success(servletRequest, "系统管理", "新增角色", "INSERT",
                auditService.params("roleId", roleId, "roleCode", request.getRoleCode()));
        return Result.success(roleId);
    }

    @Operation(summary = "更新角色")
    @PutMapping("/roles/{id}")
    public Result<String> updateRole(@PathVariable Long id, @RequestBody RoleRequest request,
                                     HttpServletRequest servletRequest) {
        Result<String> validation = validateRole(request);
        if (validation != null) {
            return validation;
        }
        repository.updateRole(id, request);
        auditService.success(servletRequest, "系统管理", "更新角色", "UPDATE",
                auditService.params("roleId", id, "roleCode", request.getRoleCode(), "status", request.getStatus()));
        return Result.success("已更新");
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/roles/{id}")
    public Result<String> deleteRole(@PathVariable Long id, HttpServletRequest servletRequest) {
        if (id == 1L) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "内置超级管理员角色不能删除");
        }
        repository.deleteRole(id);
        auditService.success(servletRequest, "系统管理", "删除角色", "DELETE",
                auditService.params("roleId", id));
        return Result.success("已删除");
    }

    @Operation(summary = "角色已授权菜单ID")
    @GetMapping("/roles/{id}/menu-ids")
    public Result<List<Long>> roleMenuIds(@PathVariable Long id) {
        return Result.success(repository.listMenuIdsByRoleId(id));
    }

    @Operation(summary = "角色菜单授权")
    @PutMapping("/roles/{id}/menus")
    public Result<String> updateRoleMenus(@PathVariable Long id, @RequestBody RoleMenuRequest request,
                                          HttpServletRequest servletRequest) {
        repository.replaceRoleMenus(id, request == null ? List.of() : request.getMenuIds());
        auditService.success(servletRequest, "系统管理", "角色菜单授权", "UPDATE",
                auditService.params("roleId", id, "menuIds", request == null ? List.of() : request.getMenuIds()));
        return Result.success("已授权");
    }

    @Operation(summary = "菜单树")
    @GetMapping("/menus")
    public Result<List<Menu>> menus() {
        return Result.success(repository.listMenuTree());
    }

    @Operation(summary = "新增菜单")
    @PostMapping("/menus")
    public Result<Long> createMenu(@RequestBody MenuRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateMenu(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        Long menuId = repository.createMenu(request);
        auditService.success(servletRequest, "系统管理", "新增菜单", "INSERT",
                auditService.params("menuId", menuId, "menuName", request.getMenuName(), "permission", request.getPermission()));
        return Result.success(menuId);
    }

    @Operation(summary = "更新菜单")
    @PutMapping("/menus/{id}")
    public Result<String> updateMenu(@PathVariable Long id, @RequestBody MenuRequest request,
                                     HttpServletRequest servletRequest) {
        Result<String> validation = validateMenu(request);
        if (validation != null) {
            return validation;
        }
        if (id.equals(request.getParentId())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "上级菜单不能选择自己");
        }
        repository.updateMenu(id, request);
        auditService.success(servletRequest, "系统管理", "更新菜单", "UPDATE",
                auditService.params("menuId", id, "menuName", request.getMenuName(), "permission", request.getPermission()));
        return Result.success("已更新");
    }

    @Operation(summary = "删除菜单")
    @DeleteMapping("/menus/{id}")
    public Result<String> deleteMenu(@PathVariable Long id, HttpServletRequest servletRequest) {
        repository.deleteMenu(id);
        auditService.success(servletRequest, "系统管理", "删除菜单", "DELETE",
                auditService.params("menuId", id));
        return Result.success("已删除");
    }

    @Operation(summary = "字典类型")
    @GetMapping("/dict-types")
    public Result<List<DictType>> dictTypes() {
        return Result.success(repository.listDictTypes());
    }

    @Operation(summary = "新增字典类型")
    @PostMapping("/dict-types")
    public Result<Long> createDictType(@RequestBody DictTypeRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateDictType(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        Long dictTypeId = repository.createDictType(request);
        auditService.success(servletRequest, "系统管理", "新增字典类型", "INSERT",
                auditService.params("dictTypeId", dictTypeId, "dictCode", request.getDictCode()));
        return Result.success(dictTypeId);
    }

    @Operation(summary = "更新字典类型")
    @PutMapping("/dict-types/{id}")
    public Result<String> updateDictType(@PathVariable Long id, @RequestBody DictTypeRequest request,
                                         HttpServletRequest servletRequest) {
        Result<String> validation = validateDictType(request);
        if (validation != null) {
            return validation;
        }
        repository.updateDictType(id, request);
        auditService.success(servletRequest, "系统管理", "更新字典类型", "UPDATE",
                auditService.params("dictTypeId", id, "dictCode", request.getDictCode(), "status", request.getStatus()));
        return Result.success("已更新");
    }

    @Operation(summary = "删除字典类型")
    @DeleteMapping("/dict-types/{id}")
    public Result<String> deleteDictType(@PathVariable Long id, HttpServletRequest servletRequest) {
        repository.deleteDictType(id);
        auditService.success(servletRequest, "系统管理", "删除字典类型", "DELETE",
                auditService.params("dictTypeId", id));
        return Result.success("已删除");
    }

    @Operation(summary = "字典项")
    @GetMapping("/dict-items")
    public Result<List<DictItem>> dictItems(@RequestParam(required = false) String dictCode) {
        return Result.success(repository.listDictItems(dictCode));
    }

    @Operation(summary = "新增字典项")
    @PostMapping("/dict-items")
    public Result<Long> createDictItem(@RequestBody DictItemRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateDictItem(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        Long dictItemId = repository.createDictItem(request);
        auditService.success(servletRequest, "系统管理", "新增字典项", "INSERT",
                auditService.params("dictItemId", dictItemId, "dictCode", request.getDictCode(),
                        "itemValue", request.getItemValue()));
        return Result.success(dictItemId);
    }

    @Operation(summary = "更新字典项")
    @PutMapping("/dict-items/{id}")
    public Result<String> updateDictItem(@PathVariable Long id, @RequestBody DictItemRequest request,
                                         HttpServletRequest servletRequest) {
        Result<String> validation = validateDictItem(request);
        if (validation != null) {
            return validation;
        }
        repository.updateDictItem(id, request);
        auditService.success(servletRequest, "系统管理", "更新字典项", "UPDATE",
                auditService.params("dictItemId", id, "dictCode", request.getDictCode(),
                        "itemValue", request.getItemValue()));
        return Result.success("已更新");
    }

    @Operation(summary = "删除字典项")
    @DeleteMapping("/dict-items/{id}")
    public Result<String> deleteDictItem(@PathVariable Long id, HttpServletRequest servletRequest) {
        repository.deleteDictItem(id);
        auditService.success(servletRequest, "系统管理", "删除字典项", "DELETE",
                auditService.params("dictItemId", id));
        return Result.success("已删除");
    }

    @Operation(summary = "系统参数")
    @GetMapping("/configs")
    public Result<List<ConfigItem>> configs() {
        return Result.success(repository.listConfigs());
    }

    @Operation(summary = "新增系统参数")
    @PostMapping("/configs")
    public Result<Long> createConfig(@RequestBody ConfigRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateConfig(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        Long configId = repository.createConfig(request);
        auditService.success(servletRequest, "系统管理", "新增系统参数", "INSERT",
                auditService.params("configId", configId, "configKey", request.getConfigKey(),
                        "sensitive", request.getSensitive()));
        return Result.success(configId);
    }

    @Operation(summary = "更新系统参数")
    @PutMapping("/configs/{id}")
    public Result<String> updateConfig(@PathVariable Long id, @RequestBody ConfigRequest request,
                                       HttpServletRequest servletRequest) {
        Result<String> validation = validateConfig(request);
        if (validation != null) {
            return validation;
        }
        repository.updateConfig(id, request);
        auditService.success(servletRequest, "系统管理", "更新系统参数", "UPDATE",
                auditService.params("configId", id, "configKey", request.getConfigKey(),
                        "sensitive", request.getSensitive()));
        return Result.success("已更新");
    }

    @Operation(summary = "删除系统参数")
    @DeleteMapping("/configs/{id}")
    public Result<String> deleteConfig(@PathVariable Long id, HttpServletRequest servletRequest) {
        repository.deleteConfig(id);
        auditService.success(servletRequest, "系统管理", "删除系统参数", "DELETE",
                auditService.params("configId", id));
        return Result.success("已删除");
    }

    private int safePageNum(int pageNum) {
        return pageNum > 0 ? pageNum : DEFAULT_PAGE_NUM;
    }

    private int safePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isValidStatus(String status) {
        return isBlank(status) || "ENABLED".equals(status) || "DISABLED".equals(status);
    }

    private Result<String> validateRole(RoleRequest request) {
        if (request == null || isBlank(request.getRoleCode()) || isBlank(request.getRoleName())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "角色编码和名称不能为空");
        }
        if (!isValidStatus(request.getStatus())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "状态只能是 ENABLED 或 DISABLED");
        }
        return null;
    }

    private Result<String> validateTenant(TenantRequest request) {
        if (request == null || isBlank(request.getTenantCode()) || isBlank(request.getTenantName())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "租户编码和名称不能为空");
        }
        if (!isValidStatus(request.getStatus())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "状态只能是 ENABLED 或 DISABLED");
        }
        return null;
    }

    private Result<String> validateDept(DeptRequest request) {
        if (request == null || isBlank(request.getDeptName())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "部门名称不能为空");
        }
        if (!isValidStatus(request.getStatus())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "状态只能是 ENABLED 或 DISABLED");
        }
        return null;
    }

    private Result<String> validateMenu(MenuRequest request) {
        if (request == null || isBlank(request.getMenuType()) || isBlank(request.getMenuName())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "菜单类型和名称不能为空");
        }
        if (!"MENU".equals(request.getMenuType()) && !"BUTTON".equals(request.getMenuType())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "菜单类型只能是 MENU 或 BUTTON");
        }
        return null;
    }

    private Result<String> validateDictType(DictTypeRequest request) {
        if (request == null || isBlank(request.getDictCode()) || isBlank(request.getDictName())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "字典编码和名称不能为空");
        }
        if (!isValidStatus(request.getStatus())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "状态只能是 ENABLED 或 DISABLED");
        }
        return null;
    }

    private Result<String> validateDictItem(DictItemRequest request) {
        if (request == null || isBlank(request.getDictCode()) || isBlank(request.getItemLabel())
                || isBlank(request.getItemValue())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "字典编码、标签和值不能为空");
        }
        if (!isValidStatus(request.getStatus())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "状态只能是 ENABLED 或 DISABLED");
        }
        return null;
    }

    private Result<String> validateConfig(ConfigRequest request) {
        if (request == null || isBlank(request.getConfigKey()) || isBlank(request.getConfigName())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "参数 Key 和名称不能为空");
        }
        return null;
    }
}
