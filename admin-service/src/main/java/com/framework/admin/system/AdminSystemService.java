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
import com.framework.admin.system.AdminSystemModels.RoleMenuRequest;
import com.framework.admin.system.AdminSystemModels.RoleRequest;
import com.framework.admin.system.AdminSystemModels.Tenant;
import com.framework.admin.system.AdminSystemModels.TenantRequest;
import com.framework.admin.system.AdminSystemModels.UserCreateRequest;
import com.framework.admin.system.AdminSystemModels.UserStatusRequest;
import com.framework.admin.system.AdminSystemModels.UserUpdateRequest;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.auth.service.LoginSecurityService;
import com.framework.auth.util.PasswordValidator;
import com.framework.auth.service.SessionManager;
import com.framework.crypto.util.PasswordUtils;
import com.framework.security.service.PermissionCacheService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统管理后台服务。
 */
@Slf4j
@Service
public class AdminSystemService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final long BUILT_IN_ADMIN_ID = 1L;
    private static final long BUILT_IN_SUPER_ADMIN_ROLE_ID = 1L;

    private final AdminSystemRepository repository;
    private final AdminAuditService auditService;
    private final ObjectProvider<PermissionCacheService> permissionCacheServiceProvider;
    private final ObjectProvider<SessionManager> sessionManagerProvider;
    private final ObjectProvider<LoginSecurityService> loginSecurityServiceProvider;

    public AdminSystemService(AdminSystemRepository repository, AdminAuditService auditService) {
        this(repository, auditService, null, null, null);
    }

    @Autowired
    public AdminSystemService(AdminSystemRepository repository, AdminAuditService auditService,
                              ObjectProvider<PermissionCacheService> permissionCacheServiceProvider,
                              ObjectProvider<SessionManager> sessionManagerProvider,
                              ObjectProvider<LoginSecurityService> loginSecurityServiceProvider) {
        this.repository = repository;
        this.auditService = auditService;
        this.permissionCacheServiceProvider = permissionCacheServiceProvider;
        this.sessionManagerProvider = sessionManagerProvider;
        this.loginSecurityServiceProvider = loginSecurityServiceProvider;
    }

    public List<Tenant> tenants() {
        try {
            return repository.listTenants();
        } catch (RuntimeException e) {
            log.warn("[系统管理] 租户列表查询失败 error={}", e.getMessage());
            return List.of();
        }
    }

    public Result<Long> createTenant(TenantRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateTenant(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        try {
            Long tenantId = repository.createTenant(request);
            auditSuccess(servletRequest, "新增租户", "INSERT",
                    "tenantId", tenantId, "tenantCode", request.getTenantCode());
            return Result.success(tenantId);
        } catch (RuntimeException e) {
            return serviceError("新增租户", "租户保存失败", e);
        }
    }

    public Result<String> updateTenant(Long id, TenantRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateTenant(request);
        if (validation != null) {
            return validation;
        }
        try {
            repository.updateTenant(id, request);
            auditSuccess(servletRequest, "更新租户", "UPDATE",
                    "tenantId", id, "tenantCode", request.getTenantCode(), "status", request.getStatus());
            return Result.success("已更新");
        } catch (RuntimeException e) {
            return serviceError("更新租户", "租户更新失败", e);
        }
    }

    public Result<String> deleteTenant(Long id, HttpServletRequest servletRequest) {
        if (id == 1L) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "默认租户不能删除");
        }
        try {
            if (repository.countUsersByTenant(id) > 0) {
                return Result.fail(ResultCode.PARAM_ERROR.getCode(), "租户下存在用户，不能删除");
            }
            repository.deleteTenant(id);
            auditSuccess(servletRequest, "删除租户", "DELETE", "tenantId", id);
            return Result.success("已删除");
        } catch (RuntimeException e) {
            return serviceError("删除租户", "租户删除失败", e);
        }
    }

    public List<Dept> depts(Long tenantId) {
        try {
            return repository.listDeptTree(tenantId);
        } catch (RuntimeException e) {
            log.warn("[系统管理] 部门树查询失败 tenantId={}, error={}", tenantId, e.getMessage());
            return List.of();
        }
    }

    public Result<Long> createDept(DeptRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateDept(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        try {
            Long deptId = repository.createDept(request);
            auditSuccess(servletRequest, "新增部门", "INSERT",
                    "deptId", deptId, "tenantId", request.getTenantId(), "deptName", request.getDeptName());
            return Result.success(deptId);
        } catch (RuntimeException e) {
            return serviceError("新增部门", "部门保存失败", e);
        }
    }

    public Result<String> updateDept(Long id, DeptRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateDept(request);
        if (validation != null) {
            return validation;
        }
        if (id.equals(request.getParentId())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "上级部门不能选择自己");
        }
        try {
            repository.updateDept(id, request);
            auditSuccess(servletRequest, "更新部门", "UPDATE",
                    "deptId", id, "tenantId", request.getTenantId(), "deptName", request.getDeptName(),
                    "status", request.getStatus());
            return Result.success("已更新");
        } catch (RuntimeException e) {
            return serviceError("更新部门", "部门更新失败", e);
        }
    }

    public Result<String> deleteDept(Long id, HttpServletRequest servletRequest) {
        if (id == 1L) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "总部部门不能删除");
        }
        try {
            repository.deleteDept(id);
            auditSuccess(servletRequest, "删除部门", "DELETE", "deptId", id);
            return Result.success("已删除");
        } catch (RuntimeException e) {
            return serviceError("删除部门", "部门删除失败", e);
        }
    }

    public PageResult<AdminUser> users(String keyword, String status, int pageNum, int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        try {
            List<AdminUser> records = repository.listUsers(keyword, status, safePageNum, safePageSize);
            records.forEach(user -> {
                enrichLoginSecurity(user);
                user.setPasswordHash(null);
            });
            return PageResult.of(records, repository.countUsers(keyword, status), safePageNum, safePageSize);
        } catch (RuntimeException e) {
            log.warn("[系统管理] 用户列表查询失败 error={}", e.getMessage());
            return PageResult.empty(safePageNum, safePageSize);
        }
    }

    public Result<Long> createUser(UserCreateRequest request, HttpServletRequest servletRequest) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "用户名和密码不能为空");
        }
        String passwordError = PasswordValidator.validateStrong(request.getPassword());
        if (passwordError != null) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), passwordError);
        }
        try {
            Long userId = repository.createUser(request, PasswordUtils.hash(request.getPassword()));
            refreshPermissionCache(userId);
            auditSuccess(servletRequest, "新增用户", "INSERT",
                    "userId", userId, "username", request.getUsername(), "roleIds", request.getRoleIds());
            return Result.success(userId);
        } catch (RuntimeException e) {
            return serviceError("新增用户", "用户保存失败", e);
        }
    }

    public Result<String> updateUser(Long id, UserUpdateRequest request, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidUserId(id);
        if (invalidId != null) {
            return invalidId;
        }
        if (request == null) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "用户信息不能为空");
        }
        if (!isValidStatus(request.getStatus())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "状态只能是 ENABLED 或 DISABLED");
        }
        if (isBuiltInAdmin(id) && "DISABLED".equals(request.getStatus())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "内置管理员不能禁用");
        }
        try {
            repository.updateUser(id, request);
            refreshPermissionCache(id);
            forceLogoutUser(id);
            auditSuccess(servletRequest, "更新用户", "UPDATE",
                    "userId", id, "nickname", request.getNickname(), "status", request.getStatus(),
                    "roleIds", request.getRoleIds());
            return Result.success("已更新");
        } catch (RuntimeException e) {
            return serviceError("更新用户", "用户更新失败", e);
        }
    }

    public Result<String> updateUserStatus(Long id, UserStatusRequest request, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidUserId(id);
        if (invalidId != null) {
            return invalidId;
        }
        String status = request == null || isBlank(request.getStatus()) ? "DISABLED" : request.getStatus();
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "状态只能是 ENABLED 或 DISABLED");
        }
        if (isBuiltInAdmin(id) && "DISABLED".equals(status)) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "内置管理员不能禁用");
        }
        try {
            repository.updateUserStatus(id, status);
            refreshPermissionCache(id);
            forceLogoutUser(id);
            auditSuccess(servletRequest, "更新用户状态", "UPDATE", "userId", id, "status", status);
            return Result.success("已更新");
        } catch (RuntimeException e) {
            return serviceError("更新用户状态", "用户状态更新失败", e);
        }
    }

    public Result<String> resetPassword(Long id, ResetPasswordRequest request, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidUserId(id);
        if (invalidId != null) {
            return invalidId;
        }
        if (request == null || isBlank(request.getPassword())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "密码不能为空");
        }
        String passwordError = PasswordValidator.validateStrong(request.getPassword());
        if (passwordError != null) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), passwordError);
        }
        try {
            repository.resetPassword(id, PasswordUtils.hash(request.getPassword()));
            forceLogoutUser(id);
            auditSuccess(servletRequest, "重置用户密码", "UPDATE", "userId", id);
            return Result.success("已重置");
        } catch (RuntimeException e) {
            return serviceError("重置用户密码", "用户密码重置失败", e);
        }
    }

    public Result<String> deleteUser(Long id, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidUserId(id);
        if (invalidId != null) {
            return invalidId;
        }
        if (isBuiltInAdmin(id)) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "内置管理员不能删除");
        }
        try {
            repository.deleteUser(id);
            refreshPermissionCache(id);
            forceLogoutUser(id);
            auditSuccess(servletRequest, "删除用户", "DELETE", "userId", id);
            return Result.success("已删除");
        } catch (RuntimeException e) {
            return serviceError("删除用户", "用户删除失败", e);
        }
    }

    public Result<String> unlockUser(Long id, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidUserId(id);
        if (invalidId != null) {
            return invalidId;
        }
        AdminUser user;
        try {
            user = repository.findUserById(id).orElse(null);
        } catch (RuntimeException e) {
            return serviceError("解锁用户", "用户解锁失败", e);
        }
        if (user == null) {
            return Result.fail(ResultCode.NOT_FOUND.getCode(), "用户不存在");
        }
        LoginSecurityService loginSecurityService = loginSecurityService();
        if (loginSecurityService == null) {
            return Result.fail(ResultCode.SERVICE_ERROR.getCode(), "登录安全服务不可用");
        }
        try {
            loginSecurityService.unlock(user.getUsername());
            auditSuccess(servletRequest, "解锁用户", "UPDATE",
                    "userId", id, "username", user.getUsername());
            return Result.success("已解锁");
        } catch (RuntimeException e) {
            return serviceError("解锁用户", "用户解锁失败", e);
        }
    }

    public List<Role> roles() {
        try {
            return repository.listRoles();
        } catch (RuntimeException e) {
            log.warn("[系统管理] 角色列表查询失败 error={}", e.getMessage());
            return List.of();
        }
    }

    public Result<Long> createRole(RoleRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateRole(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        try {
            Long roleId = repository.createRole(request);
            auditSuccess(servletRequest, "新增角色", "INSERT",
                    "roleId", roleId, "roleCode", request.getRoleCode());
            return Result.success(roleId);
        } catch (RuntimeException e) {
            return serviceError("新增角色", "角色保存失败", e);
        }
    }

    public Result<String> updateRole(Long id, RoleRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateRole(request);
        if (validation != null) {
            return validation;
        }
        if (isBuiltInSuperAdminRole(id) && "DISABLED".equals(request.getStatus())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "内置超级管理员角色不能禁用");
        }
        try {
            List<Long> affectedUserIds = repository.listUserIdsByRoleId(id);
            repository.updateRole(id, request);
            refreshPermissionCache(affectedUserIds);
            forceLogoutUsers(affectedUserIds);
            auditSuccess(servletRequest, "更新角色", "UPDATE",
                    "roleId", id, "roleCode", request.getRoleCode(), "status", request.getStatus());
            return Result.success("已更新");
        } catch (RuntimeException e) {
            return serviceError("更新角色", "角色更新失败", e);
        }
    }

    public Result<String> deleteRole(Long id, HttpServletRequest servletRequest) {
        if (isBuiltInSuperAdminRole(id)) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "内置超级管理员角色不能删除");
        }
        try {
            List<Long> affectedUserIds = repository.listUserIdsByRoleId(id);
            repository.deleteRole(id);
            refreshPermissionCache(affectedUserIds);
            forceLogoutUsers(affectedUserIds);
            auditSuccess(servletRequest, "删除角色", "DELETE", "roleId", id);
            return Result.success("已删除");
        } catch (RuntimeException e) {
            return serviceError("删除角色", "角色删除失败", e);
        }
    }

    public List<Long> roleMenuIds(Long id) {
        try {
            return repository.listMenuIdsByRoleId(id);
        } catch (RuntimeException e) {
            log.warn("[系统管理] 角色菜单ID查询失败 roleId={}, error={}", id, e.getMessage());
            return List.of();
        }
    }

    public Result<String> updateRoleMenus(Long id, RoleMenuRequest request, HttpServletRequest servletRequest) {
        try {
            List<Long> affectedUserIds = repository.listUserIdsByRoleId(id);
            repository.replaceRoleMenus(id, request == null ? List.of() : request.getMenuIds());
            refreshPermissionCache(affectedUserIds);
            forceLogoutUsers(affectedUserIds);
            auditSuccess(servletRequest, "角色菜单授权", "UPDATE",
                    "roleId", id, "menuIds", request == null ? List.of() : request.getMenuIds());
            return Result.success("已授权");
        } catch (RuntimeException e) {
            return serviceError("角色菜单授权", "角色授权失败", e);
        }
    }

    public List<Menu> menus() {
        try {
            return repository.listMenuTree();
        } catch (RuntimeException e) {
            log.warn("[系统管理] 菜单树查询失败 error={}", e.getMessage());
            return List.of();
        }
    }

    public Result<Long> createMenu(MenuRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateMenu(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        try {
            Long menuId = repository.createMenu(request);
            clearPermissionCache();
            forceLogoutAllUsers();
            auditSuccess(servletRequest, "新增菜单", "INSERT",
                    "menuId", menuId, "menuName", request.getMenuName(), "permission", request.getPermission());
            return Result.success(menuId);
        } catch (RuntimeException e) {
            return serviceError("新增菜单", "菜单保存失败", e);
        }
    }

    public Result<String> updateMenu(Long id, MenuRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateMenu(request);
        if (validation != null) {
            return validation;
        }
        if (id.equals(request.getParentId())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "上级菜单不能选择自己");
        }
        try {
            repository.updateMenu(id, request);
            clearPermissionCache();
            forceLogoutAllUsers();
            auditSuccess(servletRequest, "更新菜单", "UPDATE",
                    "menuId", id, "menuName", request.getMenuName(), "permission", request.getPermission());
            return Result.success("已更新");
        } catch (RuntimeException e) {
            return serviceError("更新菜单", "菜单更新失败", e);
        }
    }

    public Result<String> deleteMenu(Long id, HttpServletRequest servletRequest) {
        try {
            repository.deleteMenu(id);
            clearPermissionCache();
            forceLogoutAllUsers();
            auditSuccess(servletRequest, "删除菜单", "DELETE", "menuId", id);
            return Result.success("已删除");
        } catch (RuntimeException e) {
            return serviceError("删除菜单", "菜单删除失败", e);
        }
    }

    public List<DictType> dictTypes() {
        try {
            return repository.listDictTypes();
        } catch (RuntimeException e) {
            log.warn("[系统管理] 字典类型查询失败 error={}", e.getMessage());
            return List.of();
        }
    }

    public Result<Long> createDictType(DictTypeRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateDictType(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        try {
            Long dictTypeId = repository.createDictType(request);
            auditSuccess(servletRequest, "新增字典类型", "INSERT",
                    "dictTypeId", dictTypeId, "dictCode", request.getDictCode());
            return Result.success(dictTypeId);
        } catch (RuntimeException e) {
            return serviceError("新增字典类型", "字典类型保存失败", e);
        }
    }

    public Result<String> updateDictType(Long id, DictTypeRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateDictType(request);
        if (validation != null) {
            return validation;
        }
        try {
            repository.updateDictType(id, request);
            auditSuccess(servletRequest, "更新字典类型", "UPDATE",
                    "dictTypeId", id, "dictCode", request.getDictCode(), "status", request.getStatus());
            return Result.success("已更新");
        } catch (RuntimeException e) {
            return serviceError("更新字典类型", "字典类型更新失败", e);
        }
    }

    public Result<String> deleteDictType(Long id, HttpServletRequest servletRequest) {
        try {
            repository.deleteDictType(id);
            auditSuccess(servletRequest, "删除字典类型", "DELETE", "dictTypeId", id);
            return Result.success("已删除");
        } catch (RuntimeException e) {
            return serviceError("删除字典类型", "字典类型删除失败", e);
        }
    }

    public List<DictItem> dictItems(String dictCode) {
        try {
            return repository.listDictItems(dictCode);
        } catch (RuntimeException e) {
            log.warn("[系统管理] 字典项查询失败 dictCode={}, error={}", dictCode, e.getMessage());
            return List.of();
        }
    }

    public Result<Long> createDictItem(DictItemRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateDictItem(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        try {
            Long dictItemId = repository.createDictItem(request);
            auditSuccess(servletRequest, "新增字典项", "INSERT",
                    "dictItemId", dictItemId, "dictCode", request.getDictCode(),
                    "itemValue", request.getItemValue());
            return Result.success(dictItemId);
        } catch (RuntimeException e) {
            return serviceError("新增字典项", "字典项保存失败", e);
        }
    }

    public Result<String> updateDictItem(Long id, DictItemRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateDictItem(request);
        if (validation != null) {
            return validation;
        }
        try {
            repository.updateDictItem(id, request);
            auditSuccess(servletRequest, "更新字典项", "UPDATE",
                    "dictItemId", id, "dictCode", request.getDictCode(),
                    "itemValue", request.getItemValue());
            return Result.success("已更新");
        } catch (RuntimeException e) {
            return serviceError("更新字典项", "字典项更新失败", e);
        }
    }

    public Result<String> deleteDictItem(Long id, HttpServletRequest servletRequest) {
        try {
            repository.deleteDictItem(id);
            auditSuccess(servletRequest, "删除字典项", "DELETE", "dictItemId", id);
            return Result.success("已删除");
        } catch (RuntimeException e) {
            return serviceError("删除字典项", "字典项删除失败", e);
        }
    }

    public List<ConfigItem> configs() {
        try {
            return repository.listConfigs();
        } catch (RuntimeException e) {
            log.warn("[系统管理] 系统参数查询失败 error={}", e.getMessage());
            return List.of();
        }
    }

    public Result<Long> createConfig(ConfigRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateConfig(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        try {
            Long configId = repository.createConfig(request);
            auditSuccess(servletRequest, "新增系统参数", "INSERT",
                    "configId", configId, "configKey", request.getConfigKey(),
                    "sensitive", request.getSensitive());
            return Result.success(configId);
        } catch (RuntimeException e) {
            return serviceError("新增系统参数", "系统参数保存失败", e);
        }
    }

    public Result<String> updateConfig(Long id, ConfigRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateConfig(request);
        if (validation != null) {
            return validation;
        }
        try {
            repository.updateConfig(id, request);
            auditSuccess(servletRequest, "更新系统参数", "UPDATE",
                    "configId", id, "configKey", request.getConfigKey(),
                    "sensitive", request.getSensitive());
            return Result.success("已更新");
        } catch (RuntimeException e) {
            return serviceError("更新系统参数", "系统参数更新失败", e);
        }
    }

    public Result<String> deleteConfig(Long id, HttpServletRequest servletRequest) {
        try {
            repository.deleteConfig(id);
            auditSuccess(servletRequest, "删除系统参数", "DELETE", "configId", id);
            return Result.success("已删除");
        } catch (RuntimeException e) {
            return serviceError("删除系统参数", "系统参数删除失败", e);
        }
    }

    private void auditSuccess(HttpServletRequest request, String action, String operationType, Object... params) {
        if (auditService == null) {
            return;
        }
        try {
            auditService.success(request, "系统管理", action, operationType, auditService.params(params));
        } catch (RuntimeException e) {
            log.warn("[系统管理] 审计日志写入失败 action={}, error={}", action, e.getMessage());
        }
    }

    private <T> Result<T> serviceError(String action, String message, RuntimeException exception) {
        log.warn("[系统管理] {}失败 error={}", action, exception.getMessage());
        return Result.fail(ResultCode.SERVICE_ERROR.getCode(), message);
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

    private Result<String> invalidUserId(Long id) {
        if (id == null || id <= 0) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "用户ID必须大于0");
        }
        return null;
    }

    private boolean isValidStatus(String status) {
        return isBlank(status) || "ENABLED".equals(status) || "DISABLED".equals(status);
    }

    private boolean isBuiltInAdmin(Long id) {
        return id != null && id == BUILT_IN_ADMIN_ID;
    }

    private boolean isBuiltInSuperAdminRole(Long id) {
        return id != null && id == BUILT_IN_SUPER_ADMIN_ROLE_ID;
    }

    private void refreshPermissionCache(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            PermissionCacheService cacheService = permissionCacheService();
            if (cacheService != null) {
                cacheService.refresh(userId);
            }
        } catch (RuntimeException e) {
            log.warn("[权限缓存] 刷新用户缓存失败 userId={}", userId, e);
        }
    }

    private void refreshPermissionCache(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<Long> safeUserIds = userIds.stream()
                .filter(userId -> userId != null)
                .distinct()
                .toList();
        if (safeUserIds.isEmpty()) {
            return;
        }
        try {
            PermissionCacheService cacheService = permissionCacheService();
            if (cacheService != null) {
                cacheService.refreshBatch(safeUserIds);
            }
        } catch (RuntimeException e) {
            log.warn("[权限缓存] 批量刷新用户缓存失败 userIds={}", safeUserIds, e);
        }
    }

    private void clearPermissionCache() {
        try {
            PermissionCacheService cacheService = permissionCacheService();
            if (cacheService != null) {
                cacheService.clearAll();
            }
        } catch (RuntimeException e) {
            log.warn("[权限缓存] 清空缓存失败", e);
        }
    }

    private void forceLogoutUser(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            SessionManager sessionManager = sessionManager();
            if (sessionManager != null) {
                sessionManager.forceLogoutAll(userId);
            }
        } catch (RuntimeException e) {
            log.warn("[权限会话] 强制下线用户失败 userId={}", userId, e);
        }
    }

    private void forceLogoutUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userIds.stream()
                .filter(userId -> userId != null)
                .distinct()
                .forEach(this::forceLogoutUser);
    }

    private void forceLogoutAllUsers() {
        try {
            SessionManager sessionManager = sessionManager();
            if (sessionManager != null) {
                sessionManager.forceLogoutAll();
            }
        } catch (RuntimeException e) {
            log.warn("[权限会话] 强制下线全部用户失败", e);
        }
    }

    private PermissionCacheService permissionCacheService() {
        if (permissionCacheServiceProvider == null) {
            return null;
        }
        try {
            return permissionCacheServiceProvider.getIfAvailable();
        } catch (RuntimeException e) {
            log.warn("[权限缓存] 获取缓存服务失败 error={}", e.getMessage());
            return null;
        }
    }

    private SessionManager sessionManager() {
        if (sessionManagerProvider == null) {
            return null;
        }
        try {
            return sessionManagerProvider.getIfAvailable();
        } catch (RuntimeException e) {
            log.warn("[权限会话] 获取会话服务失败 error={}", e.getMessage());
            return null;
        }
    }

    private LoginSecurityService loginSecurityService() {
        if (loginSecurityServiceProvider == null) {
            return null;
        }
        try {
            return loginSecurityServiceProvider.getIfAvailable();
        } catch (RuntimeException e) {
            log.warn("[登录安全] 获取登录安全服务失败 error={}", e.getMessage());
            return null;
        }
    }

    private void enrichLoginSecurity(AdminUser user) {
        if (user == null || isBlank(user.getUsername())) {
            return;
        }
        try {
            LoginSecurityService loginSecurityService = loginSecurityService();
            if (loginSecurityService == null) {
                return;
            }
            LoginSecurityService.LoginSecurityStatus status = loginSecurityService.getStatus(user.getUsername());
            user.setLoginFailCount(status.failCount())
                    .setLoginLocked(status.locked())
                    .setLoginLockTtlMinutes(status.lockTtlMinutes());
        } catch (RuntimeException e) {
            log.warn("[登录安全] 查询用户登录安全状态失败 username={}", user.getUsername(), e);
        }
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
