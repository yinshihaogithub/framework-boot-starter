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
        return repository.listTenants();
    }

    public Result<Long> createTenant(TenantRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateTenant(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        Long tenantId = repository.createTenant(request);
        auditService.success(servletRequest, "系统管理", "新增租户", "INSERT",
                auditService.params("tenantId", tenantId, "tenantCode", request.getTenantCode()));
        return Result.success(tenantId);
    }

    public Result<String> updateTenant(Long id, TenantRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateTenant(request);
        if (validation != null) {
            return validation;
        }
        repository.updateTenant(id, request);
        auditService.success(servletRequest, "系统管理", "更新租户", "UPDATE",
                auditService.params("tenantId", id, "tenantCode", request.getTenantCode(), "status", request.getStatus()));
        return Result.success("已更新");
    }

    public Result<String> deleteTenant(Long id, HttpServletRequest servletRequest) {
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

    public List<Dept> depts(Long tenantId) {
        return repository.listDeptTree(tenantId);
    }

    public Result<Long> createDept(DeptRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateDept(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        Long deptId = repository.createDept(request);
        auditService.success(servletRequest, "系统管理", "新增部门", "INSERT",
                auditService.params("deptId", deptId, "tenantId", request.getTenantId(), "deptName", request.getDeptName()));
        return Result.success(deptId);
    }

    public Result<String> updateDept(Long id, DeptRequest request, HttpServletRequest servletRequest) {
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

    public Result<String> deleteDept(Long id, HttpServletRequest servletRequest) {
        if (id == 1L) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "总部部门不能删除");
        }
        repository.deleteDept(id);
        auditService.success(servletRequest, "系统管理", "删除部门", "DELETE",
                auditService.params("deptId", id));
        return Result.success("已删除");
    }

    public PageResult<AdminUser> users(String keyword, String status, int pageNum, int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        List<AdminUser> records = repository.listUsers(keyword, status, safePageNum, safePageSize);
        records.forEach(user -> {
            enrichLoginSecurity(user);
            user.setPasswordHash(null);
        });
        return PageResult.of(records, repository.countUsers(keyword, status), safePageNum, safePageSize);
    }

    public Result<Long> createUser(UserCreateRequest request, HttpServletRequest servletRequest) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "用户名和密码不能为空");
        }
        String passwordError = PasswordValidator.validateStrong(request.getPassword());
        if (passwordError != null) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), passwordError);
        }
        Long userId = repository.createUser(request, PasswordUtils.hash(request.getPassword()));
        refreshPermissionCache(userId);
        auditService.success(servletRequest, "系统管理", "新增用户", "INSERT",
                auditService.params("userId", userId, "username", request.getUsername(), "roleIds", request.getRoleIds()));
        return Result.success(userId);
    }

    public Result<String> updateUser(Long id, UserUpdateRequest request, HttpServletRequest servletRequest) {
        if (request == null) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "用户信息不能为空");
        }
        if (!isValidStatus(request.getStatus())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "状态只能是 ENABLED 或 DISABLED");
        }
        repository.updateUser(id, request);
        refreshPermissionCache(id);
        forceLogoutUser(id);
        auditService.success(servletRequest, "系统管理", "更新用户", "UPDATE",
                auditService.params("userId", id, "nickname", request.getNickname(), "status", request.getStatus(),
                        "roleIds", request.getRoleIds()));
        return Result.success("已更新");
    }

    public Result<String> updateUserStatus(Long id, UserStatusRequest request, HttpServletRequest servletRequest) {
        String status = request == null || isBlank(request.getStatus()) ? "DISABLED" : request.getStatus();
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "状态只能是 ENABLED 或 DISABLED");
        }
        repository.updateUserStatus(id, status);
        refreshPermissionCache(id);
        forceLogoutUser(id);
        auditService.success(servletRequest, "系统管理", "更新用户状态", "UPDATE",
                auditService.params("userId", id, "status", status));
        return Result.success("已更新");
    }

    public Result<String> resetPassword(Long id, ResetPasswordRequest request, HttpServletRequest servletRequest) {
        if (request == null || isBlank(request.getPassword())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "密码不能为空");
        }
        String passwordError = PasswordValidator.validateStrong(request.getPassword());
        if (passwordError != null) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), passwordError);
        }
        repository.resetPassword(id, PasswordUtils.hash(request.getPassword()));
        forceLogoutUser(id);
        auditService.success(servletRequest, "系统管理", "重置用户密码", "UPDATE",
                auditService.params("userId", id));
        return Result.success("已重置");
    }

    public Result<String> deleteUser(Long id, HttpServletRequest servletRequest) {
        if (id == 1L) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "内置管理员不能删除");
        }
        repository.deleteUser(id);
        refreshPermissionCache(id);
        forceLogoutUser(id);
        auditService.success(servletRequest, "系统管理", "删除用户", "DELETE",
                auditService.params("userId", id));
        return Result.success("已删除");
    }

    public Result<String> unlockUser(Long id, HttpServletRequest servletRequest) {
        if (id == null) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "用户ID不能为空");
        }
        AdminUser user = repository.findUserById(id).orElse(null);
        if (user == null) {
            return Result.fail(ResultCode.NOT_FOUND.getCode(), "用户不存在");
        }
        LoginSecurityService loginSecurityService = loginSecurityService();
        if (loginSecurityService == null) {
            return Result.fail(ResultCode.SERVICE_ERROR.getCode(), "登录安全服务不可用");
        }
        loginSecurityService.unlock(user.getUsername());
        auditService.success(servletRequest, "系统管理", "解锁用户", "UPDATE",
                auditService.params("userId", id, "username", user.getUsername()));
        return Result.success("已解锁");
    }

    public List<Role> roles() {
        return repository.listRoles();
    }

    public Result<Long> createRole(RoleRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateRole(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        Long roleId = repository.createRole(request);
        auditService.success(servletRequest, "系统管理", "新增角色", "INSERT",
                auditService.params("roleId", roleId, "roleCode", request.getRoleCode()));
        return Result.success(roleId);
    }

    public Result<String> updateRole(Long id, RoleRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateRole(request);
        if (validation != null) {
            return validation;
        }
        List<Long> affectedUserIds = repository.listUserIdsByRoleId(id);
        repository.updateRole(id, request);
        refreshPermissionCache(affectedUserIds);
        forceLogoutUsers(affectedUserIds);
        auditService.success(servletRequest, "系统管理", "更新角色", "UPDATE",
                auditService.params("roleId", id, "roleCode", request.getRoleCode(), "status", request.getStatus()));
        return Result.success("已更新");
    }

    public Result<String> deleteRole(Long id, HttpServletRequest servletRequest) {
        if (id == 1L) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "内置超级管理员角色不能删除");
        }
        List<Long> affectedUserIds = repository.listUserIdsByRoleId(id);
        repository.deleteRole(id);
        refreshPermissionCache(affectedUserIds);
        forceLogoutUsers(affectedUserIds);
        auditService.success(servletRequest, "系统管理", "删除角色", "DELETE",
                auditService.params("roleId", id));
        return Result.success("已删除");
    }

    public List<Long> roleMenuIds(Long id) {
        return repository.listMenuIdsByRoleId(id);
    }

    public Result<String> updateRoleMenus(Long id, RoleMenuRequest request, HttpServletRequest servletRequest) {
        List<Long> affectedUserIds = repository.listUserIdsByRoleId(id);
        repository.replaceRoleMenus(id, request == null ? List.of() : request.getMenuIds());
        refreshPermissionCache(affectedUserIds);
        forceLogoutUsers(affectedUserIds);
        auditService.success(servletRequest, "系统管理", "角色菜单授权", "UPDATE",
                auditService.params("roleId", id, "menuIds", request == null ? List.of() : request.getMenuIds()));
        return Result.success("已授权");
    }

    public List<Menu> menus() {
        return repository.listMenuTree();
    }

    public Result<Long> createMenu(MenuRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateMenu(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        Long menuId = repository.createMenu(request);
        clearPermissionCache();
        auditService.success(servletRequest, "系统管理", "新增菜单", "INSERT",
                auditService.params("menuId", menuId, "menuName", request.getMenuName(), "permission", request.getPermission()));
        return Result.success(menuId);
    }

    public Result<String> updateMenu(Long id, MenuRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateMenu(request);
        if (validation != null) {
            return validation;
        }
        if (id.equals(request.getParentId())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "上级菜单不能选择自己");
        }
        repository.updateMenu(id, request);
        clearPermissionCache();
        forceLogoutAllUsers();
        auditService.success(servletRequest, "系统管理", "更新菜单", "UPDATE",
                auditService.params("menuId", id, "menuName", request.getMenuName(), "permission", request.getPermission()));
        return Result.success("已更新");
    }

    public Result<String> deleteMenu(Long id, HttpServletRequest servletRequest) {
        repository.deleteMenu(id);
        clearPermissionCache();
        forceLogoutAllUsers();
        auditService.success(servletRequest, "系统管理", "删除菜单", "DELETE",
                auditService.params("menuId", id));
        return Result.success("已删除");
    }

    public List<DictType> dictTypes() {
        return repository.listDictTypes();
    }

    public Result<Long> createDictType(DictTypeRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateDictType(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        Long dictTypeId = repository.createDictType(request);
        auditService.success(servletRequest, "系统管理", "新增字典类型", "INSERT",
                auditService.params("dictTypeId", dictTypeId, "dictCode", request.getDictCode()));
        return Result.success(dictTypeId);
    }

    public Result<String> updateDictType(Long id, DictTypeRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateDictType(request);
        if (validation != null) {
            return validation;
        }
        repository.updateDictType(id, request);
        auditService.success(servletRequest, "系统管理", "更新字典类型", "UPDATE",
                auditService.params("dictTypeId", id, "dictCode", request.getDictCode(), "status", request.getStatus()));
        return Result.success("已更新");
    }

    public Result<String> deleteDictType(Long id, HttpServletRequest servletRequest) {
        repository.deleteDictType(id);
        auditService.success(servletRequest, "系统管理", "删除字典类型", "DELETE",
                auditService.params("dictTypeId", id));
        return Result.success("已删除");
    }

    public List<DictItem> dictItems(String dictCode) {
        return repository.listDictItems(dictCode);
    }

    public Result<Long> createDictItem(DictItemRequest request, HttpServletRequest servletRequest) {
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

    public Result<String> updateDictItem(Long id, DictItemRequest request, HttpServletRequest servletRequest) {
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

    public Result<String> deleteDictItem(Long id, HttpServletRequest servletRequest) {
        repository.deleteDictItem(id);
        auditService.success(servletRequest, "系统管理", "删除字典项", "DELETE",
                auditService.params("dictItemId", id));
        return Result.success("已删除");
    }

    public List<ConfigItem> configs() {
        return repository.listConfigs();
    }

    public Result<Long> createConfig(ConfigRequest request, HttpServletRequest servletRequest) {
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

    public Result<String> updateConfig(Long id, ConfigRequest request, HttpServletRequest servletRequest) {
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

    public Result<String> deleteConfig(Long id, HttpServletRequest servletRequest) {
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
        return permissionCacheServiceProvider == null ? null : permissionCacheServiceProvider.getIfAvailable();
    }

    private SessionManager sessionManager() {
        return sessionManagerProvider == null ? null : sessionManagerProvider.getIfAvailable();
    }

    private LoginSecurityService loginSecurityService() {
        return loginSecurityServiceProvider == null ? null : loginSecurityServiceProvider.getIfAvailable();
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
