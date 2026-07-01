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
import com.framework.admin.support.AdminPageSupport;
import com.framework.admin.support.AdminTextSupport;
import com.framework.auth.context.UserContextHolder;
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
import java.util.Locale;

/**
 * 系统管理后台服务。
 */
@Slf4j
@Service
public class AdminSystemService {

    private static final long BUILT_IN_ADMIN_ID = 1L;
    private static final long BUILT_IN_SUPER_ADMIN_ROLE_ID = 1L;

    private final AdminSystemMapperSupport mapperSupport;
    private final AdminAuditService auditService;
    private final ObjectProvider<PermissionCacheService> permissionCacheServiceProvider;
    private final ObjectProvider<SessionManager> sessionManagerProvider;
    private final ObjectProvider<LoginSecurityService> loginSecurityServiceProvider;

    public AdminSystemService(AdminSystemMapperSupport mapperSupport, AdminAuditService auditService) {
        this(mapperSupport, auditService, null, null, null);
    }

    @Autowired
    public AdminSystemService(AdminSystemMapperSupport mapperSupport, AdminAuditService auditService,
                              ObjectProvider<PermissionCacheService> permissionCacheServiceProvider,
                              ObjectProvider<SessionManager> sessionManagerProvider,
                              ObjectProvider<LoginSecurityService> loginSecurityServiceProvider) {
        this.mapperSupport = mapperSupport;
        this.auditService = auditService;
        this.permissionCacheServiceProvider = permissionCacheServiceProvider;
        this.sessionManagerProvider = sessionManagerProvider;
        this.loginSecurityServiceProvider = loginSecurityServiceProvider;
    }

    public PageResult<Tenant> tenants(String keyword, String status, int pageNum, int pageSize) {
        int safePageNum = AdminPageSupport.safePageNum(pageNum);
        int safePageSize = AdminPageSupport.safePageSize(pageSize);
        String safeKeyword = text(keyword);
        String safeStatus = normalizeStatusFilter(status);
        if (isInvalidStatusFilter(status, safeStatus)) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        try {
            List<Tenant> records = mapperSupport.listTenants(safeKeyword, safeStatus, safePageNum, safePageSize);
            return PageResult.of(records, mapperSupport.countTenants(safeKeyword, safeStatus), safePageNum, safePageSize);
        } catch (RuntimeException e) {
            log.warn("[系统管理] 租户列表查询失败 error={}", e.getMessage());
            return PageResult.empty(safePageNum, safePageSize);
        }
    }

    public List<Tenant> tenantOptions(String keyword, int limit) {
        int safeLimit = AdminPageSupport.safePageSize(limit);
        try {
            return mapperSupport.listTenantOptions(text(keyword), safeLimit);
        } catch (RuntimeException e) {
            log.warn("[系统管理] 租户选项查询失败 error={}", e.getMessage());
            return List.of();
        }
    }

    public Result<Long> createTenant(TenantRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateTenant(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        try {
            Long tenantId = mapperSupport.createTenant(request);
            auditSuccess(servletRequest, "新增租户", "INSERT",
                    "tenantId", tenantId, "tenantCode", request.getTenantCode());
            return Result.success(tenantId);
        } catch (RuntimeException e) {
            return serviceError("新增租户", "租户保存失败", e);
        }
    }

    public Result<String> updateTenant(Long id, TenantRequest request, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidResourceId(id, "租户");
        if (invalidId != null) {
            return invalidId;
        }
        Result<String> validation = validateTenant(request);
        if (validation != null) {
            return validation;
        }
        try {
            if (!mapperSupport.updateTenant(id, request)) {
                return resourceNotFound("租户");
            }
            auditSuccess(servletRequest, "更新租户", "UPDATE",
                    "tenantId", id, "tenantCode", request.getTenantCode(), "status", request.getStatus());
            return Result.success("已更新");
        } catch (RuntimeException e) {
            return serviceError("更新租户", "租户更新失败", e);
        }
    }

    public Result<String> deleteTenant(Long id, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidResourceId(id, "租户");
        if (invalidId != null) {
            return invalidId;
        }
        if (id == 1L) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "默认租户不能删除");
        }
        try {
            if (mapperSupport.countUsersByTenant(id) > 0) {
                return Result.fail(ResultCode.PARAM_ERROR.getCode(), "租户下存在用户，不能删除");
            }
            if (!mapperSupport.deleteTenant(id)) {
                return resourceNotFound("租户");
            }
            auditSuccess(servletRequest, "删除租户", "DELETE", "tenantId", id);
            return Result.success("已删除");
        } catch (RuntimeException e) {
            return serviceError("删除租户", "租户删除失败", e);
        }
    }

    public List<Dept> depts(Long tenantId) {
        try {
            return mapperSupport.listDeptTree(tenantId);
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
            Long deptId = mapperSupport.createDept(request);
            auditSuccess(servletRequest, "新增部门", "INSERT",
                    "deptId", deptId, "tenantId", request.getTenantId(), "deptName", request.getDeptName());
            return Result.success(deptId);
        } catch (RuntimeException e) {
            return serviceError("新增部门", "部门保存失败", e);
        }
    }

    public Result<String> updateDept(Long id, DeptRequest request, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidResourceId(id, "部门");
        if (invalidId != null) {
            return invalidId;
        }
        Result<String> validation = validateDept(request);
        if (validation != null) {
            return validation;
        }
        if (id.equals(request.getParentId())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "上级部门不能选择自己");
        }
        try {
            if (!mapperSupport.updateDept(id, request)) {
                return resourceNotFound("部门");
            }
            auditSuccess(servletRequest, "更新部门", "UPDATE",
                    "deptId", id, "tenantId", request.getTenantId(), "deptName", request.getDeptName(),
                    "status", request.getStatus());
            return Result.success("已更新");
        } catch (RuntimeException e) {
            return serviceError("更新部门", "部门更新失败", e);
        }
    }

    public Result<String> deleteDept(Long id, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidResourceId(id, "部门");
        if (invalidId != null) {
            return invalidId;
        }
        if (id == 1L) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "总部部门不能删除");
        }
        try {
            if (!mapperSupport.deleteDept(id)) {
                return resourceNotFound("部门");
            }
            auditSuccess(servletRequest, "删除部门", "DELETE", "deptId", id);
            return Result.success("已删除");
        } catch (RuntimeException e) {
            return serviceError("删除部门", "部门删除失败", e);
        }
    }

    public PageResult<AdminUser> users(String keyword, String status, int pageNum, int pageSize) {
        int safePageNum = AdminPageSupport.safePageNum(pageNum);
        int safePageSize = AdminPageSupport.safePageSize(pageSize);
        String safeKeyword = text(keyword);
        String safeStatus = normalizeStatusFilter(status);
        if (isInvalidStatusFilter(status, safeStatus)) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        try {
            List<AdminUser> records = mapperSupport.listUsers(safeKeyword, safeStatus, safePageNum, safePageSize);
            records.forEach(user -> {
                enrichLoginSecurity(user);
                user.setPasswordHash(null);
            });
            return PageResult.of(records, mapperSupport.countUsers(safeKeyword, safeStatus), safePageNum, safePageSize);
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
        Result<String> roleIdValidation = validatePositiveIds(request.getRoleIds(), "角色");
        if (roleIdValidation != null) {
            return Result.fail(roleIdValidation.getCode(), roleIdValidation.getMessage());
        }
        request.setRoleIds(distinctIds(request.getRoleIds()));
        try {
            if (!request.getRoleIds().isEmpty() && !mapperSupport.allRolesExist(request.getRoleIds())) {
                return Result.fail(ResultCode.NOT_FOUND.getCode(), "角色不存在");
            }
            Long userId = mapperSupport.createUser(request, PasswordUtils.hash(request.getPassword()));
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
        if (isBuiltInAdmin(id) && "DISABLED".equals(normalizeStatus(request.getStatus()))) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "内置管理员不能禁用");
        }
        Result<String> roleIdValidation = validatePositiveIds(request.getRoleIds(), "角色");
        if (roleIdValidation != null) {
            return roleIdValidation;
        }
        request.setRoleIds(distinctIds(request.getRoleIds()));
        try {
            if (!request.getRoleIds().isEmpty() && !mapperSupport.allRolesExist(request.getRoleIds())) {
                return resourceNotFound("角色");
            }
            if (!mapperSupport.updateUser(id, request)) {
                return resourceNotFound("用户");
            }
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
        String status = normalizeStatus(request == null ? null : request.getStatus());
        status = status == null ? "DISABLED" : status;
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "状态只能是 ENABLED 或 DISABLED");
        }
        if (isBuiltInAdmin(id) && "DISABLED".equals(status)) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "内置管理员不能禁用");
        }
        try {
            if (!mapperSupport.updateUserStatus(id, status)) {
                return resourceNotFound("用户");
            }
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
            if (!mapperSupport.resetPassword(id, PasswordUtils.hash(request.getPassword()))) {
                return resourceNotFound("用户");
            }
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
            if (!mapperSupport.deleteUser(id)) {
                return resourceNotFound("用户");
            }
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
            user = mapperSupport.findUserById(id).orElse(null);
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

    public PageResult<Role> roles(String keyword, String status, int pageNum, int pageSize) {
        int safePageNum = AdminPageSupport.safePageNum(pageNum);
        int safePageSize = AdminPageSupport.safePageSize(pageSize);
        String safeKeyword = text(keyword);
        String safeStatus = normalizeStatusFilter(status);
        if (isInvalidStatusFilter(status, safeStatus)) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        try {
            List<Role> records = mapperSupport.listRoles(safeKeyword, safeStatus, safePageNum, safePageSize);
            return PageResult.of(records, mapperSupport.countRoles(safeKeyword, safeStatus), safePageNum, safePageSize);
        } catch (RuntimeException e) {
            log.warn("[系统管理] 角色列表查询失败 error={}", e.getMessage());
            return PageResult.empty(safePageNum, safePageSize);
        }
    }

    public List<Role> roleOptions(String keyword, int limit) {
        int safeLimit = AdminPageSupport.safePageSize(limit);
        try {
            return mapperSupport.listRoleOptions(text(keyword), safeLimit);
        } catch (RuntimeException e) {
            log.warn("[系统管理] 角色选项查询失败 error={}", e.getMessage());
            return List.of();
        }
    }

    public Result<Long> createRole(RoleRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateRole(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        try {
            Long roleId = mapperSupport.createRole(request);
            auditSuccess(servletRequest, "新增角色", "INSERT",
                    "roleId", roleId, "roleCode", request.getRoleCode());
            return Result.success(roleId);
        } catch (RuntimeException e) {
            return serviceError("新增角色", "角色保存失败", e);
        }
    }

    public Result<String> updateRole(Long id, RoleRequest request, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidResourceId(id, "角色");
        if (invalidId != null) {
            return invalidId;
        }
        Result<String> validation = validateRole(request);
        if (validation != null) {
            return validation;
        }
        if (isBuiltInSuperAdminRole(id) && "DISABLED".equals(normalizeStatus(request.getStatus()))) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "内置超级管理员角色不能禁用");
        }
        try {
            List<Long> affectedUserIds = mapperSupport.listUserIdsByRoleId(id);
            if (!mapperSupport.updateRole(id, request)) {
                return resourceNotFound("角色");
            }
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
        Result<String> invalidId = invalidResourceId(id, "角色");
        if (invalidId != null) {
            return invalidId;
        }
        if (isBuiltInSuperAdminRole(id)) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "内置超级管理员角色不能删除");
        }
        try {
            List<Long> affectedUserIds = mapperSupport.listUserIdsByRoleId(id);
            if (!mapperSupport.deleteRole(id)) {
                return resourceNotFound("角色");
            }
            refreshPermissionCache(affectedUserIds);
            forceLogoutUsers(affectedUserIds);
            auditSuccess(servletRequest, "删除角色", "DELETE", "roleId", id);
            return Result.success("已删除");
        } catch (RuntimeException e) {
            return serviceError("删除角色", "角色删除失败", e);
        }
    }

    public List<Long> roleMenuIds(Long id) {
        if (invalidResourceId(id, "角色") != null) {
            return List.of();
        }
        try {
            return mapperSupport.listMenuIdsByRoleId(id);
        } catch (RuntimeException e) {
            log.warn("[系统管理] 角色菜单ID查询失败 roleId={}, error={}", id, e.getMessage());
            return List.of();
        }
    }

    public Result<String> updateRoleMenus(Long id, RoleMenuRequest request, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidResourceId(id, "角色");
        if (invalidId != null) {
            return invalidId;
        }
        List<Long> menuIds = request == null ? List.of() : distinctIds(request.getMenuIds());
        Result<String> menuIdValidation = validatePositiveIds(menuIds, "菜单");
        if (menuIdValidation != null) {
            return menuIdValidation;
        }
        try {
            if (!menuIds.isEmpty() && !mapperSupport.allMenusExist(menuIds)) {
                return resourceNotFound("菜单");
            }
            List<Long> affectedUserIds = mapperSupport.listUserIdsByRoleId(id);
            if (!mapperSupport.replaceRoleMenus(id, menuIds)) {
                return resourceNotFound("角色");
            }
            refreshPermissionCache(affectedUserIds);
            forceLogoutUsers(affectedUserIds);
            auditSuccess(servletRequest, "角色菜单授权", "UPDATE",
                    "roleId", id, "menuIds", menuIds);
            return Result.success("已授权");
        } catch (RuntimeException e) {
            return serviceError("角色菜单授权", "角色授权失败", e);
        }
    }

    public List<Menu> menus() {
        try {
            return mapperSupport.listMenuTree();
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
            Result<String> parentValidation = validateMenuParent(null, request);
            if (parentValidation != null) {
                return Result.fail(parentValidation.getCode(), parentValidation.getMessage());
            }
            Long menuId = mapperSupport.createMenu(request);
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
        Result<String> invalidId = invalidResourceId(id, "菜单");
        if (invalidId != null) {
            return invalidId;
        }
        Result<String> validation = validateMenu(request);
        if (validation != null) {
            return validation;
        }
        try {
            Result<String> parentValidation = validateMenuParent(id, request);
            if (parentValidation != null) {
                return parentValidation;
            }
            if (!mapperSupport.updateMenu(id, request)) {
                return resourceNotFound("菜单");
            }
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
        Result<String> invalidId = invalidResourceId(id, "菜单");
        if (invalidId != null) {
            return invalidId;
        }
        try {
            if (!mapperSupport.deleteMenu(id)) {
                return resourceNotFound("菜单");
            }
            clearPermissionCache();
            forceLogoutAllUsers();
            auditSuccess(servletRequest, "删除菜单", "DELETE", "menuId", id);
            return Result.success("已删除");
        } catch (RuntimeException e) {
            return serviceError("删除菜单", "菜单删除失败", e);
        }
    }

    public PageResult<DictType> dictTypes(String keyword, String status, int pageNum, int pageSize) {
        int safePageNum = AdminPageSupport.safePageNum(pageNum);
        int safePageSize = AdminPageSupport.safePageSize(pageSize);
        String safeKeyword = text(keyword);
        String safeStatus = normalizeStatusFilter(status);
        if (isInvalidStatusFilter(status, safeStatus)) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        try {
            List<DictType> records = mapperSupport.listDictTypes(safeKeyword, safeStatus, safePageNum, safePageSize);
            return PageResult.of(records, mapperSupport.countDictTypes(safeKeyword, safeStatus), safePageNum, safePageSize);
        } catch (RuntimeException e) {
            log.warn("[系统管理] 字典类型查询失败 error={}", e.getMessage());
            return PageResult.empty(safePageNum, safePageSize);
        }
    }

    public List<DictType> dictTypeOptions(String keyword, int limit) {
        int safeLimit = AdminPageSupport.safePageSize(limit);
        try {
            return mapperSupport.listDictTypeOptions(text(keyword), safeLimit);
        } catch (RuntimeException e) {
            log.warn("[系统管理] 字典类型选项查询失败 error={}", e.getMessage());
            return List.of();
        }
    }

    public Result<Long> createDictType(DictTypeRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateDictType(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        try {
            Long dictTypeId = mapperSupport.createDictType(request);
            auditSuccess(servletRequest, "新增字典类型", "INSERT",
                    "dictTypeId", dictTypeId, "dictCode", request.getDictCode());
            return Result.success(dictTypeId);
        } catch (RuntimeException e) {
            return serviceError("新增字典类型", "字典类型保存失败", e);
        }
    }

    public Result<String> updateDictType(Long id, DictTypeRequest request, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidResourceId(id, "字典类型");
        if (invalidId != null) {
            return invalidId;
        }
        Result<String> validation = validateDictType(request);
        if (validation != null) {
            return validation;
        }
        try {
            if (!mapperSupport.updateDictType(id, request)) {
                return resourceNotFound("字典类型");
            }
            auditSuccess(servletRequest, "更新字典类型", "UPDATE",
                    "dictTypeId", id, "dictCode", request.getDictCode(), "status", request.getStatus());
            return Result.success("已更新");
        } catch (RuntimeException e) {
            return serviceError("更新字典类型", "字典类型更新失败", e);
        }
    }

    public Result<String> deleteDictType(Long id, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidResourceId(id, "字典类型");
        if (invalidId != null) {
            return invalidId;
        }
        try {
            if (!mapperSupport.deleteDictType(id)) {
                return resourceNotFound("字典类型");
            }
            auditSuccess(servletRequest, "删除字典类型", "DELETE", "dictTypeId", id);
            return Result.success("已删除");
        } catch (RuntimeException e) {
            return serviceError("删除字典类型", "字典类型删除失败", e);
        }
    }

    public PageResult<DictItem> dictItems(String dictCode, String keyword, String status, int pageNum, int pageSize) {
        int safePageNum = AdminPageSupport.safePageNum(pageNum);
        int safePageSize = AdminPageSupport.safePageSize(pageSize);
        String safeDictCode = text(dictCode);
        String safeKeyword = text(keyword);
        String safeStatus = normalizeStatusFilter(status);
        if (isInvalidStatusFilter(status, safeStatus)) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        try {
            List<DictItem> records = mapperSupport.listDictItems(
                    safeDictCode, safeKeyword, safeStatus, safePageNum, safePageSize);
            long total = mapperSupport.countDictItems(safeDictCode, safeKeyword, safeStatus);
            return PageResult.of(records, total, safePageNum, safePageSize);
        } catch (RuntimeException e) {
            log.warn("[系统管理] 字典项查询失败 dictCode={}, error={}", dictCode, e.getMessage());
            return PageResult.empty(safePageNum, safePageSize);
        }
    }

    public Result<Long> createDictItem(DictItemRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateDictItem(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        try {
            Long dictItemId = mapperSupport.createDictItem(request);
            auditSuccess(servletRequest, "新增字典项", "INSERT",
                    "dictItemId", dictItemId, "dictCode", request.getDictCode(),
                    "itemValue", request.getItemValue());
            return Result.success(dictItemId);
        } catch (RuntimeException e) {
            return serviceError("新增字典项", "字典项保存失败", e);
        }
    }

    public Result<String> updateDictItem(Long id, DictItemRequest request, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidResourceId(id, "字典项");
        if (invalidId != null) {
            return invalidId;
        }
        Result<String> validation = validateDictItem(request);
        if (validation != null) {
            return validation;
        }
        try {
            if (!mapperSupport.updateDictItem(id, request)) {
                return resourceNotFound("字典项");
            }
            auditSuccess(servletRequest, "更新字典项", "UPDATE",
                    "dictItemId", id, "dictCode", request.getDictCode(),
                    "itemValue", request.getItemValue());
            return Result.success("已更新");
        } catch (RuntimeException e) {
            return serviceError("更新字典项", "字典项更新失败", e);
        }
    }

    public Result<String> deleteDictItem(Long id, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidResourceId(id, "字典项");
        if (invalidId != null) {
            return invalidId;
        }
        try {
            if (!mapperSupport.deleteDictItem(id)) {
                return resourceNotFound("字典项");
            }
            auditSuccess(servletRequest, "删除字典项", "DELETE", "dictItemId", id);
            return Result.success("已删除");
        } catch (RuntimeException e) {
            return serviceError("删除字典项", "字典项删除失败", e);
        }
    }

    public PageResult<ConfigItem> configs(String keyword, int pageNum, int pageSize) {
        int safePageNum = AdminPageSupport.safePageNum(pageNum);
        int safePageSize = AdminPageSupport.safePageSize(pageSize);
        String safeKeyword = text(keyword);
        try {
            List<ConfigItem> records = mapperSupport.listConfigs(safeKeyword, safePageNum, safePageSize);
            return PageResult.of(records, mapperSupport.countConfigs(safeKeyword), safePageNum, safePageSize);
        } catch (RuntimeException e) {
            log.warn("[系统管理] 系统参数查询失败 error={}", e.getMessage());
            return PageResult.empty(safePageNum, safePageSize);
        }
    }

    public Result<Long> createConfig(ConfigRequest request, HttpServletRequest servletRequest) {
        Result<String> validation = validateConfig(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        try {
            Long configId = mapperSupport.createConfig(request);
            auditSuccess(servletRequest, "新增系统参数", "INSERT",
                    "configId", configId, "configKey", request.getConfigKey(),
                    "sensitive", request.getSensitive());
            return Result.success(configId);
        } catch (RuntimeException e) {
            return serviceError("新增系统参数", "系统参数保存失败", e);
        }
    }

    public Result<String> updateConfig(Long id, ConfigRequest request, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidResourceId(id, "系统参数");
        if (invalidId != null) {
            return invalidId;
        }
        Result<String> validation = validateConfig(request);
        if (validation != null) {
            return validation;
        }
        try {
            if (!mapperSupport.updateConfig(id, request)) {
                return resourceNotFound("系统参数");
            }
            auditSuccess(servletRequest, "更新系统参数", "UPDATE",
                    "configId", id, "configKey", request.getConfigKey(),
                    "sensitive", request.getSensitive());
            return Result.success("已更新");
        } catch (RuntimeException e) {
            return serviceError("更新系统参数", "系统参数更新失败", e);
        }
    }

    public Result<String> deleteConfig(Long id, HttpServletRequest servletRequest) {
        Result<String> invalidId = invalidResourceId(id, "系统参数");
        if (invalidId != null) {
            return invalidId;
        }
        try {
            if (!mapperSupport.deleteConfig(id)) {
                return resourceNotFound("系统参数");
            }
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
            auditService.success(request, "系统管理", action, operationType,
                    auditService.params(paramsWithOperator(params)));
        } catch (RuntimeException e) {
            log.warn("[系统管理] 审计日志写入失败 action={}, error={}", action, e.getMessage());
        }
    }

    private Object[] paramsWithOperator(Object[] params) {
        int length = params == null ? 0 : params.length;
        Object[] values = new Object[length + 2];
        if (length > 0) {
            System.arraycopy(params, 0, values, 0, length);
        }
        values[length] = "operator";
        values[length + 1] = currentOperatorName();
        return values;
    }

    private <T> Result<T> serviceError(String action, String message, RuntimeException exception) {
        log.warn("[系统管理] {}失败 error={}", action, exception.getMessage());
        return Result.fail(ResultCode.SERVICE_ERROR.getCode(), message);
    }

    private Result<String> resourceNotFound(String resourceName) {
        return Result.fail(ResultCode.NOT_FOUND.getCode(), resourceName + "不存在");
    }

    private boolean isBlank(String value) {
        return !AdminTextSupport.hasText(value);
    }

    private String text(String value) {
        return AdminTextSupport.trimToNull(value);
    }

    private String text(String value, String fallback) {
        String text = text(value);
        return text == null ? fallback : text;
    }

    private String currentOperatorName() {
        return text(UserContextHolder.getUsername(), "admin");
    }

    private String normalizeStatusFilter(String status) {
        String normalized = normalizeStatus(status);
        return "ENABLED".equals(normalized) || "DISABLED".equals(normalized) ? normalized : null;
    }

    private boolean isInvalidStatusFilter(String originalStatus, String normalizedStatus) {
        return !isBlank(originalStatus) && normalizedStatus == null;
    }

    private Result<String> invalidResourceId(Long id, String resourceName) {
        if (id == null || id <= 0) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), resourceName + "ID必须大于0");
        }
        return null;
    }

    private Result<String> invalidUserId(Long id) {
        if (id == null || id <= 0) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "用户ID必须大于0");
        }
        return null;
    }

    private Result<String> validatePositiveIds(List<Long> ids, String resourceName) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        for (Long id : ids) {
            if (id == null || id <= 0) {
                return Result.fail(ResultCode.PARAM_ERROR.getCode(), resourceName + "ID必须大于0");
            }
        }
        return null;
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().distinct().toList();
    }

    private boolean isValidStatus(String status) {
        String normalized = normalizeStatus(status);
        return normalized == null || "ENABLED".equals(normalized) || "DISABLED".equals(normalized);
    }

    private String normalizeStatus(String status) {
        String text = text(status);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
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
        String menuType = normalizeMenuType(request.getMenuType());
        if (!"MENU".equals(menuType) && !"BUTTON".equals(menuType)) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "菜单类型只能是 MENU 或 BUTTON");
        }
        if (request.getParentId() != null && request.getParentId() < 0) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "上级菜单ID不能小于0");
        }
        return null;
    }

    private String normalizeMenuType(String menuType) {
        String text = text(menuType);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private Result<String> validateMenuParent(Long currentMenuId, MenuRequest request) {
        Long parentId = request.getParentId();
        if (parentId == null || parentId == 0) {
            return null;
        }
        if (currentMenuId != null && currentMenuId.equals(parentId)) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "上级菜单不能选择自己");
        }
        if (!mapperSupport.allMenusExist(List.of(parentId))) {
            return Result.fail(ResultCode.NOT_FOUND.getCode(), "上级菜单不存在");
        }
        if (currentMenuId != null && mapperSupport.isMenuDescendant(currentMenuId, parentId)) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "上级菜单不能选择自己的下级");
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
