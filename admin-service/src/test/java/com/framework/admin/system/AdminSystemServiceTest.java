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
import com.framework.auth.service.LoginSecurityService;
import com.framework.auth.service.SessionManager;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.crypto.util.PasswordUtils;
import com.framework.security.service.PermissionCacheService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSystemServiceTest {

    private final FakeRepository repository = new FakeRepository();
    private final FakeAuditService auditService = new FakeAuditService();
    private final AdminSystemService service = new AdminSystemService(repository, auditService);

    @Test
    void usersSanitizesPagingAndPasswordHash() {
        repository.users = List.of(new AdminUser()
                .setId(2L)
                .setUsername("alice")
                .setPasswordHash("secret"));
        repository.userCount = 1;

        PageResult<AdminUser> page = service.users(null, null, -1, 500);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(200);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getPasswordHash()).isNull();
        assertThat(repository.listUserPageNum).isEqualTo(1);
        assertThat(repository.listUserPageSize).isEqualTo(200);
    }

    @Test
    void usersIncludesLoginSecurityStatus() {
        FakeLoginSecurityService loginSecurityService = new FakeLoginSecurityService();
        loginSecurityService.status = new LoginSecurityService.LoginSecurityStatus(2L, true, 18L);
        AdminSystemService serviceWithLoginSecurity = new AdminSystemService(
                repository, auditService, null, null, provider(loginSecurityService));
        repository.users = List.of(new AdminUser()
                .setId(2L)
                .setUsername("alice")
                .setPasswordHash("secret"));
        repository.userCount = 1;

        PageResult<AdminUser> page = serviceWithLoginSecurity.users(null, null, 1, 20);

        AdminUser user = page.getRecords().get(0);
        assertThat(user.getLoginFailCount()).isEqualTo(2L);
        assertThat(user.getLoginLocked()).isTrue();
        assertThat(user.getLoginLockTtlMinutes()).isEqualTo(18L);
        assertThat(user.getPasswordHash()).isNull();
    }

    @Test
    void queryEndpointsFallBackWhenRepositoryFails() {
        repository.queryFailure = new RuntimeException("database down");

        PageResult<AdminUser> users = service.users(null, null, 0, 0);

        assertThat(service.tenants()).isEmpty();
        assertThat(service.depts(1L)).isEmpty();
        assertThat(users.getPageNum()).isEqualTo(1);
        assertThat(users.getPageSize()).isEqualTo(20);
        assertThat(users.getRecords()).isEmpty();
        assertThat(users.getTotal()).isZero();
        assertThat(service.roles()).isEmpty();
        assertThat(service.roleMenuIds(1L)).isEmpty();
        assertThat(service.menus()).isEmpty();
        assertThat(service.dictTypes()).isEmpty();
        assertThat(service.dictItems("sys_status")).isEmpty();
        assertThat(service.configs()).isEmpty();
    }

    @Test
    void createUserHashesPasswordAndWritesAudit() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");
        request.setPassword("Pass@123");
        request.setRoleIds(List.of(1L));
        repository.nextUserId = 9L;

        Result<Long> result = service.createUser(request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(9L);
        assertThat(repository.createdUser).isSameAs(request);
        assertThat(repository.createdPasswordHash).isNotEqualTo("Pass@123");
        assertThat(PasswordUtils.verify("Pass@123", repository.createdPasswordHash)).isTrue();
        assertThat(auditService.actions).containsExactly("新增用户");
    }

    @Test
    void writeOperationsSucceedWhenAuditFails() {
        AdminSystemService serviceWithAuditFailure = new AdminSystemService(repository, new ThrowingAuditService());
        UserCreateRequest userCreate = new UserCreateRequest();
        userCreate.setUsername("alice");
        userCreate.setPassword("Pass@123");
        repository.userById = new AdminUser()
                .setId(9L)
                .setUsername("alice");
        AdminSystemService serviceWithLoginSecurity = new AdminSystemService(
                repository, new ThrowingAuditService(), null, null, provider(new FakeLoginSecurityService()));

        List<Result<?>> results = List.of(
                serviceWithAuditFailure.createTenant(tenantRequest(), null),
                serviceWithAuditFailure.updateTenant(9L, tenantRequest(), null),
                serviceWithAuditFailure.deleteTenant(9L, null),
                serviceWithAuditFailure.createDept(deptRequest(), null),
                serviceWithAuditFailure.updateDept(9L, deptRequest(), null),
                serviceWithAuditFailure.deleteDept(9L, null),
                serviceWithAuditFailure.createUser(userCreate, null),
                serviceWithAuditFailure.updateUser(9L, userUpdateRequest(), null),
                serviceWithAuditFailure.updateUserStatus(9L, userStatusRequest(), null),
                serviceWithAuditFailure.resetPassword(9L, resetPasswordRequest(), null),
                serviceWithAuditFailure.deleteUser(9L, null),
                serviceWithLoginSecurity.unlockUser(9L, null),
                serviceWithAuditFailure.createRole(roleRequest(), null),
                serviceWithAuditFailure.updateRole(9L, roleRequest(), null),
                serviceWithAuditFailure.deleteRole(9L, null),
                serviceWithAuditFailure.updateRoleMenus(9L, roleMenuRequest(), null),
                serviceWithAuditFailure.createMenu(menuRequest(), null),
                serviceWithAuditFailure.updateMenu(9L, menuRequest(), null),
                serviceWithAuditFailure.deleteMenu(9L, null),
                serviceWithAuditFailure.createDictType(dictTypeRequest(), null),
                serviceWithAuditFailure.updateDictType(9L, dictTypeRequest(), null),
                serviceWithAuditFailure.deleteDictType(9L, null),
                serviceWithAuditFailure.createDictItem(dictItemRequest(), null),
                serviceWithAuditFailure.updateDictItem(9L, dictItemRequest(), null),
                serviceWithAuditFailure.deleteDictItem(9L, null),
                serviceWithAuditFailure.createConfig(configRequest(), null),
                serviceWithAuditFailure.updateConfig(9L, configRequest(), null),
                serviceWithAuditFailure.deleteConfig(9L, null)
        );

        assertThat(results).allMatch(Result::isSuccess);
        assertThat(repository.createdUser).isSameAs(userCreate);
        assertThat(repository.deletedTenantId).isEqualTo(9L);
        assertThat(repository.updatedRoleId).isEqualTo(9L);
        assertThat(repository.updatedMenuId).isEqualTo(9L);
    }

    @Test
    void createUserRejectsWeakPassword() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");
        request.setPassword("password");

        Result<Long> result = service.createUser(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("密码必须包含大写字母");
        assertThat(repository.createdUser).isNull();
    }

    @Test
    void updateUserRefreshesPermissionCacheAndForcesLogout() {
        FakePermissionCacheService permissionCacheService = new FakePermissionCacheService();
        FakeSessionManager sessionManager = new FakeSessionManager();
        AdminSystemService serviceWithSecurity = new AdminSystemService(
                repository, auditService, provider(permissionCacheService), provider(sessionManager), null);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setNickname("Alice");
        request.setStatus("ENABLED");
        request.setRoleIds(List.of(1L, 2L));

        Result<String> result = serviceWithSecurity.updateUser(9L, request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(repository.updatedUserId).isEqualTo(9L);
        assertThat(repository.updatedUser).isSameAs(request);
        assertThat(permissionCacheService.refreshedUserIds).containsExactly(9L);
        assertThat(sessionManager.forceLogoutUserIds).containsExactly(9L);
        assertThat(auditService.actions).containsExactly("更新用户");
    }

    @Test
    void updateUserContinuesWhenOptionalSecurityProvidersFail() {
        AdminSystemService serviceWithFailingProviders = new AdminSystemService(
                repository, auditService, failingProvider(), failingProvider(), null);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setNickname("Alice");
        request.setStatus("ENABLED");

        Result<String> result = serviceWithFailingProviders.updateUser(9L, request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(repository.updatedUserId).isEqualTo(9L);
        assertThat(auditService.actions).containsExactly("更新用户");
    }

    @Test
    void updateUserRejectsDisablingBuiltInAdmin() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setStatus("DISABLED");

        Result<String> result = service.updateUser(1L, request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("内置管理员不能禁用");
        assertThat(repository.updatedUserId).isNull();
    }

    @Test
    void updateUserStatusRejectsDisablingBuiltInAdmin() {
        UserStatusRequest request = new UserStatusRequest();
        request.setStatus("DISABLED");

        Result<String> result = service.updateUserStatus(1L, request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("内置管理员不能禁用");
        assertThat(repository.updatedUserStatusId).isNull();
    }

    @Test
    void userIdOperationsRejectInvalidIdBeforeRepositoryLookup() {
        repository.commandFailure = new RuntimeException("database down");

        assertInvalidUserId(service.updateUser(0L, userUpdateRequest(), null));
        assertInvalidUserId(service.updateUserStatus(0L, userStatusRequest(), null));
        assertInvalidUserId(service.resetPassword(0L, resetPasswordRequest(), null));
        assertInvalidUserId(service.deleteUser(0L, null));
        assertInvalidUserId(service.unlockUser(0L, null));

        assertThat(repository.updatedUserId).isNull();
        assertThat(repository.updatedUserStatusId).isNull();
        assertThat(repository.resetPasswordUserId).isNull();
        assertThat(repository.deletedUserId).isNull();
    }

    @Test
    void resetPasswordRejectsWeakPassword() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setPassword("12345678");

        Result<String> result = service.resetPassword(9L, request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("密码必须包含小写字母");
        assertThat(repository.resetPasswordUserId).isNull();
    }

    @Test
    void unlockUserClearsLoginSecurityAndWritesAudit() {
        FakeLoginSecurityService loginSecurityService = new FakeLoginSecurityService();
        AdminSystemService serviceWithLoginSecurity = new AdminSystemService(
                repository, auditService, null, null, provider(loginSecurityService));
        repository.userById = new AdminUser()
                .setId(9L)
                .setUsername("alice");

        Result<String> result = serviceWithLoginSecurity.unlockUser(9L, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("已解锁");
        assertThat(loginSecurityService.unlockedUsername).isEqualTo("alice");
        assertThat(auditService.actions).containsExactly("解锁用户");
    }

    @Test
    void unlockUserReportsUnavailableWhenLoginSecurityProviderFails() {
        AdminSystemService serviceWithFailingLoginSecurity = new AdminSystemService(
                repository, auditService, null, null, failingProvider());
        repository.userById = new AdminUser()
                .setId(9L)
                .setUsername("alice");

        Result<String> result = serviceWithFailingLoginSecurity.unlockUser(9L, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("登录安全服务不可用");
        assertThat(auditService.actions).isEmpty();
    }

    @Test
    void deleteTenantRejectsDefaultAndTenantWithUsers() {
        Result<String> defaultTenant = service.deleteTenant(1L, null);
        repository.tenantUserCount = 2;
        Result<String> occupiedTenant = service.deleteTenant(8L, null);

        assertThat(defaultTenant.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(defaultTenant.getMessage()).isEqualTo("默认租户不能删除");
        assertThat(occupiedTenant.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(occupiedTenant.getMessage()).isEqualTo("租户下存在用户，不能删除");
        assertThat(repository.deletedTenantId).isNull();
    }

    @Test
    void tenantIdOperationsRejectInvalidIdBeforeRepositoryLookup() {
        repository.commandFailure = new RuntimeException("database down");

        assertInvalidResourceId(service.updateTenant(0L, tenantRequest(), null), "租户ID必须大于0");
        assertInvalidResourceId(service.deleteTenant(0L, null), "租户ID必须大于0");

        assertThat(repository.updatedTenantId).isNull();
        assertThat(repository.deletedTenantId).isNull();
    }

    @Test
    void deptIdOperationsRejectInvalidIdBeforeRepositoryLookup() {
        repository.commandFailure = new RuntimeException("database down");

        assertInvalidResourceId(service.updateDept(0L, deptRequest(), null), "部门ID必须大于0");
        assertInvalidResourceId(service.deleteDept(0L, null), "部门ID必须大于0");

        assertThat(repository.updatedDeptId).isNull();
        assertThat(repository.deletedDeptId).isNull();
    }

    @Test
    void roleValidationRejectsInvalidStatus() {
        RoleRequest request = new RoleRequest();
        request.setRoleCode("OPS");
        request.setRoleName("运维");
        request.setStatus("LOCKED");

        Result<Long> result = service.createRole(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("状态只能是 ENABLED 或 DISABLED");
        assertThat(repository.createdRole).isNull();
    }

    @Test
    void updateRoleRefreshesAffectedUsersAndForcesLogout() {
        FakePermissionCacheService permissionCacheService = new FakePermissionCacheService();
        FakeSessionManager sessionManager = new FakeSessionManager();
        AdminSystemService serviceWithSecurity = new AdminSystemService(
                repository, auditService, provider(permissionCacheService), provider(sessionManager), null);
        repository.affectedUserIdsByRole = new ArrayList<>(List.of(2L, 2L, 3L));
        repository.affectedUserIdsByRole.add(null);
        RoleRequest request = new RoleRequest();
        request.setRoleCode("OPS");
        request.setRoleName("运维");
        request.setStatus("ENABLED");

        Result<String> result = serviceWithSecurity.updateRole(7L, request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(repository.updatedRoleId).isEqualTo(7L);
        assertThat(repository.updatedRole).isSameAs(request);
        assertThat(permissionCacheService.batchRefreshedUserIds).containsExactly(2L, 3L);
        assertThat(sessionManager.forceLogoutUserIds).containsExactly(2L, 3L);
        assertThat(auditService.actions).containsExactly("更新角色");
    }

    @Test
    void updateRoleRejectsDisablingBuiltInSuperAdminRole() {
        RoleRequest request = new RoleRequest();
        request.setRoleCode("SUPER_ADMIN");
        request.setRoleName("超级管理员");
        request.setStatus("DISABLED");

        Result<String> result = service.updateRole(1L, request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("内置超级管理员角色不能禁用");
        assertThat(repository.updatedRoleId).isNull();
    }

    @Test
    void updateRoleMenusRefreshesAffectedUsersAndClearsMenuBindings() {
        FakePermissionCacheService permissionCacheService = new FakePermissionCacheService();
        FakeSessionManager sessionManager = new FakeSessionManager();
        AdminSystemService serviceWithSecurity = new AdminSystemService(
                repository, auditService, provider(permissionCacheService), provider(sessionManager), null);
        repository.affectedUserIdsByRole = List.of(5L, 6L);

        Result<String> result = serviceWithSecurity.updateRoleMenus(8L, null, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(repository.replacedRoleMenuRoleId).isEqualTo(8L);
        assertThat(repository.replacedRoleMenuIds).isEmpty();
        assertThat(permissionCacheService.batchRefreshedUserIds).containsExactly(5L, 6L);
        assertThat(sessionManager.forceLogoutUserIds).containsExactly(5L, 6L);
        assertThat(auditService.actions).containsExactly("角色菜单授权");
    }

    @Test
    void menuValidationRejectsInvalidTypeAndSelfParent() {
        MenuRequest invalidType = new MenuRequest();
        invalidType.setMenuType("PAGE");
        invalidType.setMenuName("首页");

        MenuRequest selfParent = new MenuRequest();
        selfParent.setMenuType("MENU");
        selfParent.setMenuName("首页");
        selfParent.setParentId(7L);

        Result<Long> invalidTypeResult = service.createMenu(invalidType, null);
        Result<String> selfParentResult = service.updateMenu(7L, selfParent, null);

        assertThat(invalidTypeResult.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(invalidTypeResult.getMessage()).isEqualTo("菜单类型只能是 MENU 或 BUTTON");
        assertThat(selfParentResult.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(selfParentResult.getMessage()).isEqualTo("上级菜单不能选择自己");
    }

    @Test
    void createMenuClearsAllPermissionCacheAndForcesAllSessionsOffline() {
        FakePermissionCacheService permissionCacheService = new FakePermissionCacheService();
        FakeSessionManager sessionManager = new FakeSessionManager();
        AdminSystemService serviceWithSecurity = new AdminSystemService(
                repository, auditService, provider(permissionCacheService), provider(sessionManager), null);
        MenuRequest request = new MenuRequest();
        request.setMenuType("MENU");
        request.setMenuName("通知中心");
        request.setParentId(0L);

        Result<Long> result = serviceWithSecurity.createMenu(request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(12L);
        assertThat(repository.createdMenu).isSameAs(request);
        assertThat(permissionCacheService.clearAllCount).isEqualTo(1);
        assertThat(sessionManager.forceLogoutAllCount).isEqualTo(1);
        assertThat(auditService.actions).containsExactly("新增菜单");
    }

    @Test
    void updateMenuClearsAllPermissionCacheAndForcesAllSessionsOffline() {
        FakePermissionCacheService permissionCacheService = new FakePermissionCacheService();
        FakeSessionManager sessionManager = new FakeSessionManager();
        AdminSystemService serviceWithSecurity = new AdminSystemService(
                repository, auditService, provider(permissionCacheService), provider(sessionManager), null);
        MenuRequest request = new MenuRequest();
        request.setMenuType("MENU");
        request.setMenuName("日志中心");
        request.setParentId(0L);

        Result<String> result = serviceWithSecurity.updateMenu(11L, request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(repository.updatedMenuId).isEqualTo(11L);
        assertThat(repository.updatedMenu).isSameAs(request);
        assertThat(permissionCacheService.clearAllCount).isEqualTo(1);
        assertThat(sessionManager.forceLogoutAllCount).isEqualTo(1);
        assertThat(auditService.actions).containsExactly("更新菜单");
    }

    @Test
    void createTenantValidatesRequiredFields() {
        TenantRequest request = new TenantRequest();
        request.setTenantCode("tenant-a");

        Result<Long> result = service.createTenant(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("租户编码和名称不能为空");
    }

    @Test
    void writeEndpointsReturnServiceErrorWhenRepositoryFails() {
        repository.commandFailure = new RuntimeException("database down");
        UserCreateRequest userCreate = new UserCreateRequest();
        userCreate.setUsername("alice");
        userCreate.setPassword("Pass@123");

        assertServiceError(service.createTenant(tenantRequest(), null), "租户保存失败");
        assertServiceError(service.updateTenant(9L, tenantRequest(), null), "租户更新失败");
        assertServiceError(service.deleteTenant(9L, null), "租户删除失败");
        assertServiceError(service.createDept(deptRequest(), null), "部门保存失败");
        assertServiceError(service.updateDept(9L, deptRequest(), null), "部门更新失败");
        assertServiceError(service.deleteDept(9L, null), "部门删除失败");
        assertServiceError(service.createUser(userCreate, null), "用户保存失败");
        assertServiceError(service.updateUser(9L, userUpdateRequest(), null), "用户更新失败");
        assertServiceError(service.updateUserStatus(9L, userStatusRequest(), null), "用户状态更新失败");
        assertServiceError(service.resetPassword(9L, resetPasswordRequest(), null), "用户密码重置失败");
        assertServiceError(service.deleteUser(9L, null), "用户删除失败");
        assertServiceError(service.createRole(roleRequest(), null), "角色保存失败");
        assertServiceError(service.updateRole(9L, roleRequest(), null), "角色更新失败");
        assertServiceError(service.deleteRole(9L, null), "角色删除失败");
        assertServiceError(service.updateRoleMenus(9L, roleMenuRequest(), null), "角色授权失败");
        assertServiceError(service.createMenu(menuRequest(), null), "菜单保存失败");
        assertServiceError(service.updateMenu(9L, menuRequest(), null), "菜单更新失败");
        assertServiceError(service.deleteMenu(9L, null), "菜单删除失败");
        assertServiceError(service.createDictType(dictTypeRequest(), null), "字典类型保存失败");
        assertServiceError(service.updateDictType(9L, dictTypeRequest(), null), "字典类型更新失败");
        assertServiceError(service.deleteDictType(9L, null), "字典类型删除失败");
        assertServiceError(service.createDictItem(dictItemRequest(), null), "字典项保存失败");
        assertServiceError(service.updateDictItem(9L, dictItemRequest(), null), "字典项更新失败");
        assertServiceError(service.deleteDictItem(9L, null), "字典项删除失败");
        assertServiceError(service.createConfig(configRequest(), null), "系统参数保存失败");
        assertServiceError(service.updateConfig(9L, configRequest(), null), "系统参数更新失败");
        assertServiceError(service.deleteConfig(9L, null), "系统参数删除失败");
        assertThat(auditService.actions).isEmpty();
    }

    @Test
    void unlockUserReturnsServiceErrorWhenRepositoryFails() {
        repository.commandFailure = new RuntimeException("database down");

        Result<String> result = service.unlockUser(9L, null);

        assertServiceError(result, "用户解锁失败");
        assertThat(auditService.actions).isEmpty();
    }

    private static void assertServiceError(Result<?> result, String message) {
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo(message);
    }

    private static TenantRequest tenantRequest() {
        TenantRequest request = new TenantRequest();
        request.setTenantCode("tenant-a");
        request.setTenantName("租户A");
        request.setStatus("ENABLED");
        return request;
    }

    private static DeptRequest deptRequest() {
        DeptRequest request = new DeptRequest();
        request.setDeptName("研发部");
        request.setParentId(0L);
        request.setStatus("ENABLED");
        return request;
    }

    private static UserUpdateRequest userUpdateRequest() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setNickname("Alice");
        request.setStatus("ENABLED");
        return request;
    }

    private static UserStatusRequest userStatusRequest() {
        UserStatusRequest request = new UserStatusRequest();
        request.setStatus("ENABLED");
        return request;
    }

    private static ResetPasswordRequest resetPasswordRequest() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setPassword("NewAdmin@123");
        return request;
    }

    private static void assertInvalidUserId(Result<?> result) {
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("用户ID必须大于0");
    }

    private static void assertInvalidResourceId(Result<?> result, String message) {
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo(message);
    }

    private static RoleRequest roleRequest() {
        RoleRequest request = new RoleRequest();
        request.setRoleCode("OPS");
        request.setRoleName("运维");
        request.setStatus("ENABLED");
        return request;
    }

    private static RoleMenuRequest roleMenuRequest() {
        RoleMenuRequest request = new RoleMenuRequest();
        request.setMenuIds(List.of(1L, 2L));
        return request;
    }

    private static MenuRequest menuRequest() {
        MenuRequest request = new MenuRequest();
        request.setMenuType("MENU");
        request.setMenuName("运维中心");
        request.setParentId(0L);
        return request;
    }

    private static DictTypeRequest dictTypeRequest() {
        DictTypeRequest request = new DictTypeRequest();
        request.setDictCode("sys_status");
        request.setDictName("系统状态");
        request.setStatus("ENABLED");
        return request;
    }

    private static DictItemRequest dictItemRequest() {
        DictItemRequest request = new DictItemRequest();
        request.setDictCode("sys_status");
        request.setItemLabel("启用");
        request.setItemValue("ENABLED");
        request.setStatus("ENABLED");
        return request;
    }

    private static ConfigRequest configRequest() {
        ConfigRequest request = new ConfigRequest();
        request.setConfigKey("system.name");
        request.setConfigName("系统名称");
        request.setConfigValue("Framework");
        return request;
    }

    private static class FakeRepository extends AdminSystemRepository {
        private List<AdminUser> users = List.of();
        private long userCount;
        private int listUserPageNum;
        private int listUserPageSize;
        private Long nextUserId = 1L;
        private UserCreateRequest createdUser;
        private String createdPasswordHash;
        private long tenantUserCount;
        private Long updatedTenantId;
        private Long deletedTenantId;
        private Long updatedDeptId;
        private Long deletedDeptId;
        private RoleRequest createdRole;
        private Long resetPasswordUserId;
        private String resetPasswordHash;
        private AdminUser userById;
        private Long updatedUserId;
        private UserUpdateRequest updatedUser;
        private Long updatedUserStatusId;
        private String updatedUserStatus;
        private Long deletedUserId;
        private List<Long> affectedUserIdsByRole = List.of();
        private Long updatedRoleId;
        private RoleRequest updatedRole;
        private Long replacedRoleMenuRoleId;
        private List<Long> replacedRoleMenuIds = List.of();
        private MenuRequest createdMenu;
        private Long updatedMenuId;
        private MenuRequest updatedMenu;
        private RuntimeException queryFailure;
        private RuntimeException commandFailure;

        private FakeRepository() {
            super(null);
        }

        @Override
        public List<Tenant> listTenants() {
            failQueryIfNeeded();
            return List.of();
        }

        @Override
        public List<Dept> listDeptTree(Long tenantId) {
            failQueryIfNeeded();
            return List.of();
        }

        @Override
        public List<AdminUser> listUsers(String keyword, String status, int pageNum, int pageSize) {
            failQueryIfNeeded();
            this.listUserPageNum = pageNum;
            this.listUserPageSize = pageSize;
            return new ArrayList<>(users);
        }

        @Override
        public long countUsers(String keyword, String status) {
            failQueryIfNeeded();
            return userCount;
        }

        @Override
        public Optional<AdminUser> findUserById(Long id) {
            failCommandIfNeeded();
            return Optional.ofNullable(userById);
        }

        @Override
        public Long createTenant(TenantRequest request) {
            failCommandIfNeeded();
            return 10L;
        }

        @Override
        public void updateTenant(Long id, TenantRequest request) {
            failCommandIfNeeded();
            this.updatedTenantId = id;
        }

        @Override
        public long countUsersByTenant(Long tenantId) {
            failCommandIfNeeded();
            return tenantUserCount;
        }

        @Override
        public void deleteTenant(Long id) {
            failCommandIfNeeded();
            this.deletedTenantId = id;
        }

        @Override
        public Long createDept(DeptRequest request) {
            failCommandIfNeeded();
            return 11L;
        }

        @Override
        public void updateDept(Long id, DeptRequest request) {
            failCommandIfNeeded();
            this.updatedDeptId = id;
        }

        @Override
        public void deleteDept(Long id) {
            failCommandIfNeeded();
            this.deletedDeptId = id;
        }

        @Override
        public Long createUser(UserCreateRequest request, String passwordHash) {
            failCommandIfNeeded();
            this.createdUser = request;
            this.createdPasswordHash = passwordHash;
            return nextUserId;
        }

        @Override
        public void updateUser(Long userId, UserUpdateRequest request) {
            failCommandIfNeeded();
            this.updatedUserId = userId;
            this.updatedUser = request;
        }

        @Override
        public void updateUserStatus(Long userId, String status) {
            failCommandIfNeeded();
            this.updatedUserStatusId = userId;
            this.updatedUserStatus = status;
        }

        @Override
        public void resetPassword(Long userId, String passwordHash) {
            failCommandIfNeeded();
            this.resetPasswordUserId = userId;
            this.resetPasswordHash = passwordHash;
        }

        @Override
        public void deleteUser(Long userId) {
            failCommandIfNeeded();
            this.deletedUserId = userId;
        }

        @Override
        public Long createRole(RoleRequest request) {
            failCommandIfNeeded();
            this.createdRole = request;
            return 3L;
        }

        @Override
        public List<Role> listRoles() {
            failQueryIfNeeded();
            return List.of();
        }

        @Override
        public void updateRole(Long roleId, RoleRequest request) {
            failCommandIfNeeded();
            this.updatedRoleId = roleId;
            this.updatedRole = request;
        }

        @Override
        public List<Long> listUserIdsByRoleId(Long roleId) {
            failCommandIfNeeded();
            return affectedUserIdsByRole;
        }

        @Override
        public void deleteRole(Long roleId) {
            failCommandIfNeeded();
        }

        @Override
        public List<Long> listMenuIdsByRoleId(Long roleId) {
            failQueryIfNeeded();
            return List.of();
        }

        @Override
        public void replaceRoleMenus(Long roleId, List<Long> menuIds) {
            failCommandIfNeeded();
            this.replacedRoleMenuRoleId = roleId;
            this.replacedRoleMenuIds = menuIds == null ? List.of() : List.copyOf(menuIds);
        }

        @Override
        public List<Menu> listMenuTree() {
            failQueryIfNeeded();
            return List.of();
        }

        @Override
        public Long createMenu(MenuRequest request) {
            failCommandIfNeeded();
            this.createdMenu = request;
            return 12L;
        }

        @Override
        public void updateMenu(Long menuId, MenuRequest request) {
            failCommandIfNeeded();
            this.updatedMenuId = menuId;
            this.updatedMenu = request;
        }

        @Override
        public void deleteMenu(Long menuId) {
            failCommandIfNeeded();
        }

        @Override
        public List<DictType> listDictTypes() {
            failQueryIfNeeded();
            return List.of();
        }

        @Override
        public List<DictItem> listDictItems(String dictCode) {
            failQueryIfNeeded();
            return List.of();
        }

        @Override
        public List<ConfigItem> listConfigs() {
            failQueryIfNeeded();
            return List.of();
        }

        @Override
        public Long createDictType(DictTypeRequest request) {
            failCommandIfNeeded();
            return 13L;
        }

        @Override
        public void updateDictType(Long id, DictTypeRequest request) {
            failCommandIfNeeded();
        }

        @Override
        public void deleteDictType(Long id) {
            failCommandIfNeeded();
        }

        @Override
        public Long createDictItem(DictItemRequest request) {
            failCommandIfNeeded();
            return 14L;
        }

        @Override
        public void updateDictItem(Long id, DictItemRequest request) {
            failCommandIfNeeded();
        }

        @Override
        public void deleteDictItem(Long id) {
            failCommandIfNeeded();
        }

        @Override
        public Long createConfig(ConfigRequest request) {
            failCommandIfNeeded();
            return 15L;
        }

        @Override
        public void updateConfig(Long id, ConfigRequest request) {
            failCommandIfNeeded();
        }

        @Override
        public void deleteConfig(Long id) {
            failCommandIfNeeded();
        }

        private void failQueryIfNeeded() {
            if (queryFailure != null) {
                throw queryFailure;
            }
        }

        private void failCommandIfNeeded() {
            if (commandFailure != null) {
                throw commandFailure;
            }
        }
    }

    private static class FakeLoginSecurityService extends LoginSecurityService {
        private LoginSecurityStatus status = new LoginSecurityStatus(0L, false, 0L);
        private String unlockedUsername;

        private FakeLoginSecurityService() {
            super(null, 5, 30);
        }

        @Override
        public LoginSecurityStatus getStatus(String username) {
            return status;
        }

        @Override
        public void unlock(String username) {
            unlockedUsername = username;
        }
    }

    private static class FakePermissionCacheService extends PermissionCacheService {
        private final List<Long> refreshedUserIds = new ArrayList<>();
        private final List<Long> batchRefreshedUserIds = new ArrayList<>();
        private int clearAllCount;

        private FakePermissionCacheService() {
            super(null);
        }

        @Override
        public void refresh(Long userId) {
            refreshedUserIds.add(userId);
        }

        @Override
        public void refreshBatch(List<Long> userIds) {
            batchRefreshedUserIds.addAll(userIds);
        }

        @Override
        public void clearAll() {
            clearAllCount++;
        }
    }

    private static class FakeSessionManager extends SessionManager {
        private final List<Long> forceLogoutUserIds = new ArrayList<>();
        private int forceLogoutAllCount;

        private FakeSessionManager() {
            super(null, null, 0);
        }

        @Override
        public void forceLogoutAll(Long userId) {
            forceLogoutUserIds.add(userId);
        }

        @Override
        public void forceLogoutAll() {
            forceLogoutAllCount++;
        }
    }

    private static class FakeAuditService extends AdminAuditService {
        private final List<String> actions = new ArrayList<>();

        private FakeAuditService() {
            super(null, null);
        }

        @Override
        public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            actions.add(action);
        }
    }

    private static class ThrowingAuditService extends AdminAuditService {
        private ThrowingAuditService() {
            super(null, null);
        }

        @Override
        public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            throw new IllegalStateException("audit unavailable");
        }
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public Stream<T> stream() {
                return value == null ? Stream.empty() : Stream.of(value);
            }
        };
    }

    private static <T> ObjectProvider<T> failingProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                throw new IllegalStateException("provider failed");
            }

            @Override
            public T getIfAvailable() {
                throw new IllegalStateException("provider failed");
            }

            @Override
            public T getIfUnique() {
                throw new IllegalStateException("provider failed");
            }

            @Override
            public T getObject() {
                throw new IllegalStateException("provider failed");
            }

            @Override
            public Stream<T> stream() {
                throw new IllegalStateException("provider failed");
            }
        };
    }
}
