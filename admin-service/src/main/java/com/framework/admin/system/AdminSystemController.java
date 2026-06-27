package com.framework.admin.system;

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
import com.framework.admin.system.AdminSystemModels.RoleMenuRequest;
import com.framework.admin.system.AdminSystemModels.RoleRequest;
import com.framework.admin.system.AdminSystemModels.Tenant;
import com.framework.admin.system.AdminSystemModels.TenantRequest;
import com.framework.admin.system.AdminSystemModels.UserCreateRequest;
import com.framework.admin.system.AdminSystemModels.UserStatusRequest;
import com.framework.admin.system.AdminSystemModels.UserUpdateRequest;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
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

    private final AdminSystemService systemService;

    public AdminSystemController(AdminSystemService systemService) {
        this.systemService = systemService;
    }

    @Operation(summary = "租户列表")
    @GetMapping("/tenants")
    public Result<List<Tenant>> tenants() {
        return Result.success(systemService.tenants());
    }

    @Operation(summary = "新增租户")
    @PostMapping("/tenants")
    public Result<Long> createTenant(@RequestBody TenantRequest request, HttpServletRequest servletRequest) {
        return systemService.createTenant(request, servletRequest);
    }

    @Operation(summary = "更新租户")
    @PutMapping("/tenants/{id}")
    public Result<String> updateTenant(@PathVariable Long id, @RequestBody TenantRequest request,
                                       HttpServletRequest servletRequest) {
        return systemService.updateTenant(id, request, servletRequest);
    }

    @Operation(summary = "删除租户")
    @DeleteMapping("/tenants/{id}")
    public Result<String> deleteTenant(@PathVariable Long id, HttpServletRequest servletRequest) {
        return systemService.deleteTenant(id, servletRequest);
    }

    @Operation(summary = "部门树")
    @GetMapping("/depts")
    public Result<List<Dept>> depts(@RequestParam(required = false) Long tenantId) {
        return Result.success(systemService.depts(tenantId));
    }

    @Operation(summary = "新增部门")
    @PostMapping("/depts")
    public Result<Long> createDept(@RequestBody DeptRequest request, HttpServletRequest servletRequest) {
        return systemService.createDept(request, servletRequest);
    }

    @Operation(summary = "更新部门")
    @PutMapping("/depts/{id}")
    public Result<String> updateDept(@PathVariable Long id, @RequestBody DeptRequest request,
                                     HttpServletRequest servletRequest) {
        return systemService.updateDept(id, request, servletRequest);
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/depts/{id}")
    public Result<String> deleteDept(@PathVariable Long id, HttpServletRequest servletRequest) {
        return systemService.deleteDept(id, servletRequest);
    }

    @Operation(summary = "用户列表")
    @GetMapping("/users")
    public Result<PageResult<AdminUser>> users(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(systemService.users(keyword, status, pageNum, pageSize));
    }

    @Operation(summary = "新增用户")
    @PostMapping("/users")
    public Result<Long> createUser(@RequestBody UserCreateRequest request, HttpServletRequest servletRequest) {
        return systemService.createUser(request, servletRequest);
    }

    @Operation(summary = "更新用户")
    @PutMapping("/users/{id}")
    public Result<String> updateUser(@PathVariable Long id, @RequestBody UserUpdateRequest request,
                                     HttpServletRequest servletRequest) {
        return systemService.updateUser(id, request, servletRequest);
    }

    @Operation(summary = "更新用户状态")
    @PutMapping("/users/{id}/status")
    public Result<String> updateUserStatus(@PathVariable Long id, @RequestBody UserStatusRequest request,
                                           HttpServletRequest servletRequest) {
        return systemService.updateUserStatus(id, request, servletRequest);
    }

    @Operation(summary = "重置密码")
    @PutMapping("/users/{id}/password")
    public Result<String> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest request,
                                        HttpServletRequest servletRequest) {
        return systemService.resetPassword(id, request, servletRequest);
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/users/{id}")
    public Result<String> deleteUser(@PathVariable Long id, HttpServletRequest servletRequest) {
        return systemService.deleteUser(id, servletRequest);
    }

    @Operation(summary = "角色列表")
    @GetMapping("/roles")
    public Result<List<Role>> roles() {
        return Result.success(systemService.roles());
    }

    @Operation(summary = "新增角色")
    @PostMapping("/roles")
    public Result<Long> createRole(@RequestBody RoleRequest request, HttpServletRequest servletRequest) {
        return systemService.createRole(request, servletRequest);
    }

    @Operation(summary = "更新角色")
    @PutMapping("/roles/{id}")
    public Result<String> updateRole(@PathVariable Long id, @RequestBody RoleRequest request,
                                     HttpServletRequest servletRequest) {
        return systemService.updateRole(id, request, servletRequest);
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/roles/{id}")
    public Result<String> deleteRole(@PathVariable Long id, HttpServletRequest servletRequest) {
        return systemService.deleteRole(id, servletRequest);
    }

    @Operation(summary = "角色已授权菜单ID")
    @GetMapping("/roles/{id}/menu-ids")
    public Result<List<Long>> roleMenuIds(@PathVariable Long id) {
        return Result.success(systemService.roleMenuIds(id));
    }

    @Operation(summary = "角色菜单授权")
    @PutMapping("/roles/{id}/menus")
    public Result<String> updateRoleMenus(@PathVariable Long id, @RequestBody RoleMenuRequest request,
                                          HttpServletRequest servletRequest) {
        return systemService.updateRoleMenus(id, request, servletRequest);
    }

    @Operation(summary = "菜单树")
    @GetMapping("/menus")
    public Result<List<Menu>> menus() {
        return Result.success(systemService.menus());
    }

    @Operation(summary = "新增菜单")
    @PostMapping("/menus")
    public Result<Long> createMenu(@RequestBody MenuRequest request, HttpServletRequest servletRequest) {
        return systemService.createMenu(request, servletRequest);
    }

    @Operation(summary = "更新菜单")
    @PutMapping("/menus/{id}")
    public Result<String> updateMenu(@PathVariable Long id, @RequestBody MenuRequest request,
                                     HttpServletRequest servletRequest) {
        return systemService.updateMenu(id, request, servletRequest);
    }

    @Operation(summary = "删除菜单")
    @DeleteMapping("/menus/{id}")
    public Result<String> deleteMenu(@PathVariable Long id, HttpServletRequest servletRequest) {
        return systemService.deleteMenu(id, servletRequest);
    }

    @Operation(summary = "字典类型")
    @GetMapping("/dict-types")
    public Result<List<DictType>> dictTypes() {
        return Result.success(systemService.dictTypes());
    }

    @Operation(summary = "新增字典类型")
    @PostMapping("/dict-types")
    public Result<Long> createDictType(@RequestBody DictTypeRequest request, HttpServletRequest servletRequest) {
        return systemService.createDictType(request, servletRequest);
    }

    @Operation(summary = "更新字典类型")
    @PutMapping("/dict-types/{id}")
    public Result<String> updateDictType(@PathVariable Long id, @RequestBody DictTypeRequest request,
                                         HttpServletRequest servletRequest) {
        return systemService.updateDictType(id, request, servletRequest);
    }

    @Operation(summary = "删除字典类型")
    @DeleteMapping("/dict-types/{id}")
    public Result<String> deleteDictType(@PathVariable Long id, HttpServletRequest servletRequest) {
        return systemService.deleteDictType(id, servletRequest);
    }

    @Operation(summary = "字典项")
    @GetMapping("/dict-items")
    public Result<List<DictItem>> dictItems(@RequestParam(required = false) String dictCode) {
        return Result.success(systemService.dictItems(dictCode));
    }

    @Operation(summary = "新增字典项")
    @PostMapping("/dict-items")
    public Result<Long> createDictItem(@RequestBody DictItemRequest request, HttpServletRequest servletRequest) {
        return systemService.createDictItem(request, servletRequest);
    }

    @Operation(summary = "更新字典项")
    @PutMapping("/dict-items/{id}")
    public Result<String> updateDictItem(@PathVariable Long id, @RequestBody DictItemRequest request,
                                         HttpServletRequest servletRequest) {
        return systemService.updateDictItem(id, request, servletRequest);
    }

    @Operation(summary = "删除字典项")
    @DeleteMapping("/dict-items/{id}")
    public Result<String> deleteDictItem(@PathVariable Long id, HttpServletRequest servletRequest) {
        return systemService.deleteDictItem(id, servletRequest);
    }

    @Operation(summary = "系统参数")
    @GetMapping("/configs")
    public Result<List<ConfigItem>> configs() {
        return Result.success(systemService.configs());
    }

    @Operation(summary = "新增系统参数")
    @PostMapping("/configs")
    public Result<Long> createConfig(@RequestBody ConfigRequest request, HttpServletRequest servletRequest) {
        return systemService.createConfig(request, servletRequest);
    }

    @Operation(summary = "更新系统参数")
    @PutMapping("/configs/{id}")
    public Result<String> updateConfig(@PathVariable Long id, @RequestBody ConfigRequest request,
                                       HttpServletRequest servletRequest) {
        return systemService.updateConfig(id, request, servletRequest);
    }

    @Operation(summary = "删除系统参数")
    @DeleteMapping("/configs/{id}")
    public Result<String> deleteConfig(@PathVariable Long id, HttpServletRequest servletRequest) {
        return systemService.deleteConfig(id, servletRequest);
    }
}
