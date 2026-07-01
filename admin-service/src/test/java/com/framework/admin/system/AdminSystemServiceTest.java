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
import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.auth.service.LoginSecurityService;
import com.framework.auth.service.SessionManager;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.crypto.util.PasswordUtils;
import com.framework.security.service.PermissionCacheService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSystemServiceTest {

    private final FakeMapperSupport mapperSupport = new FakeMapperSupport();
    private final FakeAuditService auditService = new FakeAuditService();
    private final AdminSystemService service = new AdminSystemService(mapperSupport, auditService);

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void usersSanitizesPagingAndPasswordHash() {
        mapperSupport.users = List.of(new AdminUser()
                .setId(2L)
                .setUsername("alice")
                .setPasswordHash("secret"));
        mapperSupport.userCount = 1;

        PageResult<AdminUser> page = service.users("\u00A0alice\u3000", "\u3000enabled\u00A0", -1, 500);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(200);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getPasswordHash()).isNull();
        assertThat(mapperSupport.listUserKeyword).isEqualTo("alice");
        assertThat(mapperSupport.listUserStatus).isEqualTo("ENABLED");
        assertThat(mapperSupport.countUserKeyword).isEqualTo("alice");
        assertThat(mapperSupport.countUserStatus).isEqualTo("ENABLED");
        assertThat(mapperSupport.listUserPageNum).isEqualTo(1);
        assertThat(mapperSupport.listUserPageSize).isEqualTo(200);
    }

    @Test
    void usersReturnEmptyPageForInvalidStatusFilter() {
        mapperSupport.users = List.of(new AdminUser()
                .setId(2L)
                .setUsername("alice")
                .setPasswordHash("secret"));
        mapperSupport.userCount = 1;

        PageResult<AdminUser> page = service.users(" alice ", "LOCKED", 1, 20);

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
        assertThat(mapperSupport.listUserKeyword).isNull();
        assertThat(mapperSupport.listUserStatus).isNull();
        assertThat(mapperSupport.countUserKeyword).isNull();
        assertThat(mapperSupport.countUserStatus).isNull();
    }

    @Test
    void usersIncludesLoginSecurityStatus() {
        FakeLoginSecurityService loginSecurityService = new FakeLoginSecurityService();
        loginSecurityService.status = new LoginSecurityService.LoginSecurityStatus(2L, true, 18L);
        AdminSystemService serviceWithLoginSecurity = new AdminSystemService(
                mapperSupport, auditService, null, null, provider(loginSecurityService));
        mapperSupport.users = List.of(new AdminUser()
                .setId(2L)
                .setUsername("alice")
                .setPasswordHash("secret"));
        mapperSupport.userCount = 1;

        PageResult<AdminUser> page = serviceWithLoginSecurity.users(null, null, 1, 20);

        AdminUser user = page.getRecords().get(0);
        assertThat(user.getLoginFailCount()).isEqualTo(2L);
        assertThat(user.getLoginLocked()).isTrue();
        assertThat(user.getLoginLockTtlMinutes()).isEqualTo(18L);
        assertThat(user.getPasswordHash()).isNull();
    }

    @Test
    void queryEndpointsFallBackWhenMapperSupportFails() {
        mapperSupport.queryFailure = new RuntimeException("database down");

        PageResult<Tenant> tenants = service.tenants(null, null, 0, 0);
        PageResult<AdminUser> users = service.users(null, null, 0, 0);
        PageResult<Role> roles = service.roles(null, null, 0, 0);

        assertThat(tenants.getPageNum()).isEqualTo(1);
        assertThat(tenants.getPageSize()).isEqualTo(20);
        assertThat(tenants.getRecords()).isEmpty();
        assertThat(tenants.getTotal()).isZero();
        assertThat(service.tenantOptions(null, 0)).isEmpty();
        assertThat(service.depts(1L)).isEmpty();
        assertThat(users.getPageNum()).isEqualTo(1);
        assertThat(users.getPageSize()).isEqualTo(20);
        assertThat(users.getRecords()).isEmpty();
        assertThat(users.getTotal()).isZero();
        assertThat(roles.getPageNum()).isEqualTo(1);
        assertThat(roles.getPageSize()).isEqualTo(20);
        assertThat(roles.getRecords()).isEmpty();
        assertThat(roles.getTotal()).isZero();
        assertThat(service.roleOptions(null, 0)).isEmpty();
        assertThat(service.roleMenuIds(1L)).isEmpty();
        assertThat(service.menus()).isEmpty();
        assertThat(service.dictTypes()).isEmpty();
        assertThat(service.dictItems("sys_status")).isEmpty();
        PageResult<ConfigItem> configs = service.configs(null, 0, 0);
        assertThat(configs.getPageNum()).isEqualTo(1);
        assertThat(configs.getPageSize()).isEqualTo(20);
        assertThat(configs.getRecords()).isEmpty();
        assertThat(configs.getTotal()).isZero();
    }

    @Test
    void tenantsReturnsPagedResultAndNormalizesFilters() {
        mapperSupport.tenants = List.of(new Tenant()
                .setId(2L)
                .setTenantCode("tenant-a")
                .setTenantName("租户A")
                .setStatus("ENABLED"));
        mapperSupport.tenantCount = 6L;

        PageResult<Tenant> tenants = service.tenants("\u00A0tenant\u3000", "\u3000enabled\u00A0", 3, 500);

        assertThat(tenants.getRecords()).hasSize(1);
        assertThat(tenants.getTotal()).isEqualTo(6L);
        assertThat(tenants.getPageNum()).isEqualTo(3);
        assertThat(tenants.getPageSize()).isEqualTo(200);
        assertThat(mapperSupport.listTenantKeyword).isEqualTo("tenant");
        assertThat(mapperSupport.listTenantStatus).isEqualTo("ENABLED");
        assertThat(mapperSupport.listTenantPageNum).isEqualTo(3);
        assertThat(mapperSupport.listTenantPageSize).isEqualTo(200);
        assertThat(mapperSupport.countTenantKeyword).isEqualTo("tenant");
        assertThat(mapperSupport.countTenantStatus).isEqualTo("ENABLED");
    }

    @Test
    void tenantsReturnEmptyPageForInvalidStatusFilter() {
        mapperSupport.tenants = List.of(new Tenant().setId(2L));
        mapperSupport.tenantCount = 1L;

        PageResult<Tenant> tenants = service.tenants(" tenant ", "LOCKED", 1, 20);

        assertThat(tenants.getRecords()).isEmpty();
        assertThat(tenants.getTotal()).isZero();
        assertThat(mapperSupport.listTenantKeyword).isNull();
        assertThat(mapperSupport.listTenantStatus).isNull();
        assertThat(mapperSupport.countTenantKeyword).isNull();
        assertThat(mapperSupport.countTenantStatus).isNull();
    }

    @Test
    void tenantOptionsAreTrimmedAndLimitIsBounded() {
        mapperSupport.tenantOptions = List.of(new Tenant()
                .setId(1L)
                .setTenantCode("default")
                .setTenantName("默认租户"));

        List<Tenant> options = service.tenantOptions("\u00A0default\u3000", 500);

        assertThat(options).hasSize(1);
        assertThat(mapperSupport.tenantOptionKeyword).isEqualTo("default");
        assertThat(mapperSupport.tenantOptionLimit).isEqualTo(200);
    }

    @Test
    void rolesReturnsPagedResultAndNormalizesFilters() {
        mapperSupport.roles = List.of(new Role()
                .setId(3L)
                .setRoleCode("OPS")
                .setRoleName("运维")
                .setStatus("ENABLED"));
        mapperSupport.roleCount = 9L;

        PageResult<Role> roles = service.roles("\u00A0ops\u3000", "\u3000enabled\u00A0", 2, 500);

        assertThat(roles.getRecords()).hasSize(1);
        assertThat(roles.getTotal()).isEqualTo(9L);
        assertThat(roles.getPageNum()).isEqualTo(2);
        assertThat(roles.getPageSize()).isEqualTo(200);
        assertThat(mapperSupport.listRoleKeyword).isEqualTo("ops");
        assertThat(mapperSupport.listRoleStatus).isEqualTo("ENABLED");
        assertThat(mapperSupport.listRolePageNum).isEqualTo(2);
        assertThat(mapperSupport.listRolePageSize).isEqualTo(200);
        assertThat(mapperSupport.countRoleKeyword).isEqualTo("ops");
        assertThat(mapperSupport.countRoleStatus).isEqualTo("ENABLED");
    }

    @Test
    void rolesReturnEmptyPageForInvalidStatusFilter() {
        mapperSupport.roles = List.of(new Role().setId(3L));
        mapperSupport.roleCount = 1L;

        PageResult<Role> roles = service.roles(" ops ", "LOCKED", 1, 20);

        assertThat(roles.getRecords()).isEmpty();
        assertThat(roles.getTotal()).isZero();
        assertThat(mapperSupport.listRoleKeyword).isNull();
        assertThat(mapperSupport.listRoleStatus).isNull();
        assertThat(mapperSupport.countRoleKeyword).isNull();
        assertThat(mapperSupport.countRoleStatus).isNull();
    }

    @Test
    void roleOptionsAreTrimmedAndLimitIsBounded() {
        mapperSupport.roleOptions = List.of(new Role()
                .setId(1L)
                .setRoleCode("SUPER_ADMIN")
                .setRoleName("超级管理员"));

        List<Role> options = service.roleOptions("\u00A0admin\u3000", 500);

        assertThat(options).hasSize(1);
        assertThat(mapperSupport.roleOptionKeyword).isEqualTo("admin");
        assertThat(mapperSupport.roleOptionLimit).isEqualTo(200);
    }

    @Test
    void configsReturnsPagedResultAndNormalizesKeyword() {
        mapperSupport.configs = List.of(new ConfigItem()
                .setId(1L)
                .setConfigKey("app.name")
                .setConfigName("应用名称")
                .setConfigValue("Framework")
                .setSensitive(false));
        mapperSupport.configCount = 12L;

        PageResult<ConfigItem> configs = service.configs("\u00A0app\u3000", 2, 500);

        assertThat(configs.getRecords()).hasSize(1);
        assertThat(configs.getTotal()).isEqualTo(12L);
        assertThat(configs.getPageNum()).isEqualTo(2);
        assertThat(configs.getPageSize()).isEqualTo(200);
        assertThat(mapperSupport.listConfigKeyword).isEqualTo("app");
        assertThat(mapperSupport.listConfigPageNum).isEqualTo(2);
        assertThat(mapperSupport.listConfigPageSize).isEqualTo(200);
        assertThat(mapperSupport.countConfigKeyword).isEqualTo("app");
    }

    @Test
    void createUserHashesPasswordAndWritesAudit() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");
        request.setPassword("Pass@123");
        request.setRoleIds(List.of(1L));
        mapperSupport.nextUserId = 9L;

        Result<Long> result = service.createUser(request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(9L);
        assertThat(mapperSupport.createdUser).isSameAs(request);
        assertThat(mapperSupport.createdPasswordHash).isNotEqualTo("Pass@123");
        assertThat(PasswordUtils.verify("Pass@123", mapperSupport.createdPasswordHash)).isTrue();
        assertThat(auditService.actions).containsExactly("新增用户");
        assertThat(auditService.params).containsEntry("operator", "admin");
    }

    @Test
    void writeAuditRecordsCurrentOperator() {
        UserContextHolder.set(new LoginUser().setUserId(7L).setUsername("alice"));
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");
        request.setPassword("Pass@123");
        mapperSupport.nextUserId = 9L;

        Result<Long> result = service.createUser(request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(auditService.actions).containsExactly("新增用户");
        assertThat(auditService.params).containsEntry("operator", "alice");
    }

    @Test
    void writeOperationsSucceedWhenAuditFails() {
        AdminSystemService serviceWithAuditFailure = new AdminSystemService(mapperSupport, new ThrowingAuditService());
        UserCreateRequest userCreate = new UserCreateRequest();
        userCreate.setUsername("alice");
        userCreate.setPassword("Pass@123");
        mapperSupport.userById = new AdminUser()
                .setId(9L)
                .setUsername("alice");
        AdminSystemService serviceWithLoginSecurity = new AdminSystemService(
                mapperSupport, new ThrowingAuditService(), null, null, provider(new FakeLoginSecurityService()));

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
        assertThat(mapperSupport.createdUser).isSameAs(userCreate);
        assertThat(mapperSupport.deletedTenantId).isEqualTo(9L);
        assertThat(mapperSupport.updatedRoleId).isEqualTo(9L);
        assertThat(mapperSupport.updatedMenuId).isEqualTo(9L);
    }

    @Test
    void createUserRejectsWeakPassword() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");
        request.setPassword("password");

        Result<Long> result = service.createUser(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("密码必须包含大写字母");
        assertThat(mapperSupport.createdUser).isNull();
    }

    @Test
    void createUserRejectsInvalidRoleIdsBeforeMapperSupportWrite() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");
        request.setPassword("Pass@123");
        request.setRoleIds(List.of(1L, 0L));

        Result<Long> result = service.createUser(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("角色ID必须大于0");
        assertThat(mapperSupport.createdUser).isNull();
        assertThat(mapperSupport.checkedRoleIds).isEmpty();
    }

    @Test
    void createUserReturnsNotFoundWhenRoleIdsDoNotExist() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");
        request.setPassword("Pass@123");
        request.setRoleIds(List.of(2L, 2L, 3L));
        mapperSupport.rolesExist = false;

        Result<Long> result = service.createUser(request, null);

        assertNotFound(result, "角色不存在");
        assertThat(request.getRoleIds()).containsExactly(2L, 3L);
        assertThat(mapperSupport.checkedRoleIds).containsExactly(2L, 3L);
        assertThat(mapperSupport.createdUser).isNull();
    }

    @Test
    void updateUserRefreshesPermissionCacheAndForcesLogout() {
        FakePermissionCacheService permissionCacheService = new FakePermissionCacheService();
        FakeSessionManager sessionManager = new FakeSessionManager();
        AdminSystemService serviceWithSecurity = new AdminSystemService(
                mapperSupport, auditService, provider(permissionCacheService), provider(sessionManager), null);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setNickname("Alice");
        request.setStatus("ENABLED");
        request.setRoleIds(List.of(1L, 2L));

        Result<String> result = serviceWithSecurity.updateUser(9L, request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(mapperSupport.updatedUserId).isEqualTo(9L);
        assertThat(mapperSupport.updatedUser).isSameAs(request);
        assertThat(permissionCacheService.refreshedUserIds).containsExactly(9L);
        assertThat(sessionManager.forceLogoutUserIds).containsExactly(9L);
        assertThat(auditService.actions).containsExactly("更新用户");
    }

    @Test
    void updateUserDeduplicatesRoleIdsBeforeMapperSupportWrite() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setNickname("Alice");
        request.setStatus("ENABLED");
        request.setRoleIds(List.of(2L, 2L, 3L));

        Result<String> result = service.updateUser(9L, request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(mapperSupport.checkedRoleIds).containsExactly(2L, 3L);
        assertThat(mapperSupport.updatedUser.getRoleIds()).containsExactly(2L, 3L);
    }

    @Test
    void updateUserReturnsNotFoundWhenRoleIdsDoNotExist() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setNickname("Alice");
        request.setStatus("ENABLED");
        request.setRoleIds(List.of(2L));
        mapperSupport.rolesExist = false;

        Result<String> result = service.updateUser(9L, request, null);

        assertNotFound(result, "角色不存在");
        assertThat(mapperSupport.checkedRoleIds).containsExactly(2L);
        assertThat(mapperSupport.updatedUserId).isNull();
    }

    @Test
    void updateUserContinuesWhenOptionalSecurityProvidersFail() {
        AdminSystemService serviceWithFailingProviders = new AdminSystemService(
                mapperSupport, auditService, failingProvider(), failingProvider(), null);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setNickname("Alice");
        request.setStatus("ENABLED");

        Result<String> result = serviceWithFailingProviders.updateUser(9L, request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(mapperSupport.updatedUserId).isEqualTo(9L);
        assertThat(auditService.actions).containsExactly("更新用户");
    }

    @Test
    void updateUserRejectsDisablingBuiltInAdmin() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setStatus("\u00A0disabled\u3000");

        Result<String> result = service.updateUser(1L, request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("内置管理员不能禁用");
        assertThat(mapperSupport.updatedUserId).isNull();
    }

    @Test
    void updateUserStatusRejectsDisablingBuiltInAdmin() {
        UserStatusRequest request = new UserStatusRequest();
        request.setStatus("\u3000disabled\u00A0");

        Result<String> result = service.updateUserStatus(1L, request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("内置管理员不能禁用");
        assertThat(mapperSupport.updatedUserStatusId).isNull();
    }

    @Test
    void updateUserStatusNormalizesBoundarySpaceBeforeMapperSupportWrite() {
        UserStatusRequest request = new UserStatusRequest();
        request.setStatus("\u00A0enabled\u3000");

        Result<String> result = service.updateUserStatus(9L, request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(mapperSupport.updatedUserStatusId).isEqualTo(9L);
        assertThat(mapperSupport.updatedUserStatus).isEqualTo("ENABLED");
    }

    @Test
    void userIdOperationsRejectInvalidIdBeforeMapperSupportLookup() {
        mapperSupport.commandFailure = new RuntimeException("database down");

        assertInvalidUserId(service.updateUser(0L, userUpdateRequest(), null));
        assertInvalidUserId(service.updateUserStatus(0L, userStatusRequest(), null));
        assertInvalidUserId(service.resetPassword(0L, resetPasswordRequest(), null));
        assertInvalidUserId(service.deleteUser(0L, null));
        assertInvalidUserId(service.unlockUser(0L, null));

        assertThat(mapperSupport.updatedUserId).isNull();
        assertThat(mapperSupport.updatedUserStatusId).isNull();
        assertThat(mapperSupport.resetPasswordUserId).isNull();
        assertThat(mapperSupport.deletedUserId).isNull();
    }

    @Test
    void resetPasswordRejectsWeakPassword() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setPassword("12345678");

        Result<String> result = service.resetPassword(9L, request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("密码必须包含小写字母");
        assertThat(mapperSupport.resetPasswordUserId).isNull();
    }

    @Test
    void unlockUserClearsLoginSecurityAndWritesAudit() {
        FakeLoginSecurityService loginSecurityService = new FakeLoginSecurityService();
        AdminSystemService serviceWithLoginSecurity = new AdminSystemService(
                mapperSupport, auditService, null, null, provider(loginSecurityService));
        mapperSupport.userById = new AdminUser()
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
                mapperSupport, auditService, null, null, failingProvider());
        mapperSupport.userById = new AdminUser()
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
        mapperSupport.tenantUserCount = 2;
        Result<String> occupiedTenant = service.deleteTenant(8L, null);

        assertThat(defaultTenant.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(defaultTenant.getMessage()).isEqualTo("默认租户不能删除");
        assertThat(occupiedTenant.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(occupiedTenant.getMessage()).isEqualTo("租户下存在用户，不能删除");
        assertThat(mapperSupport.deletedTenantId).isNull();
    }

    @Test
    void tenantIdOperationsRejectInvalidIdBeforeMapperSupportLookup() {
        mapperSupport.commandFailure = new RuntimeException("database down");

        assertInvalidResourceId(service.updateTenant(0L, tenantRequest(), null), "租户ID必须大于0");
        assertInvalidResourceId(service.deleteTenant(0L, null), "租户ID必须大于0");

        assertThat(mapperSupport.updatedTenantId).isNull();
        assertThat(mapperSupport.deletedTenantId).isNull();
    }

    @Test
    void deptIdOperationsRejectInvalidIdBeforeMapperSupportLookup() {
        mapperSupport.commandFailure = new RuntimeException("database down");

        assertInvalidResourceId(service.updateDept(0L, deptRequest(), null), "部门ID必须大于0");
        assertInvalidResourceId(service.deleteDept(0L, null), "部门ID必须大于0");

        assertThat(mapperSupport.updatedDeptId).isNull();
        assertThat(mapperSupport.deletedDeptId).isNull();
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
        assertThat(mapperSupport.createdRole).isNull();
    }

    @Test
    void updateRoleRefreshesAffectedUsersAndForcesLogout() {
        FakePermissionCacheService permissionCacheService = new FakePermissionCacheService();
        FakeSessionManager sessionManager = new FakeSessionManager();
        AdminSystemService serviceWithSecurity = new AdminSystemService(
                mapperSupport, auditService, provider(permissionCacheService), provider(sessionManager), null);
        mapperSupport.affectedUserIdsByRole = new ArrayList<>(List.of(2L, 2L, 3L));
        mapperSupport.affectedUserIdsByRole.add(null);
        RoleRequest request = new RoleRequest();
        request.setRoleCode("OPS");
        request.setRoleName("运维");
        request.setStatus("ENABLED");

        Result<String> result = serviceWithSecurity.updateRole(7L, request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(mapperSupport.updatedRoleId).isEqualTo(7L);
        assertThat(mapperSupport.updatedRole).isSameAs(request);
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
        assertThat(mapperSupport.updatedRoleId).isNull();
    }

    @Test
    void roleIdOperationsRejectInvalidIdBeforeMapperSupportLookup() {
        mapperSupport.commandFailure = new RuntimeException("database down");
        mapperSupport.queryFailure = new RuntimeException("database down");

        assertInvalidResourceId(service.updateRole(0L, roleRequest(), null), "角色ID必须大于0");
        assertInvalidResourceId(service.deleteRole(0L, null), "角色ID必须大于0");
        assertThat(service.roleMenuIds(0L)).isEmpty();
        assertInvalidResourceId(service.updateRoleMenus(0L, roleMenuRequest(), null), "角色ID必须大于0");

        assertThat(mapperSupport.updatedRoleId).isNull();
        assertThat(mapperSupport.deletedRoleId).isNull();
        assertThat(mapperSupport.replacedRoleMenuRoleId).isNull();
    }

    @Test
    void updateRoleMenusRefreshesAffectedUsersAndClearsMenuBindings() {
        FakePermissionCacheService permissionCacheService = new FakePermissionCacheService();
        FakeSessionManager sessionManager = new FakeSessionManager();
        AdminSystemService serviceWithSecurity = new AdminSystemService(
                mapperSupport, auditService, provider(permissionCacheService), provider(sessionManager), null);
        mapperSupport.affectedUserIdsByRole = List.of(5L, 6L);

        Result<String> result = serviceWithSecurity.updateRoleMenus(8L, null, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(mapperSupport.replacedRoleMenuRoleId).isEqualTo(8L);
        assertThat(mapperSupport.replacedRoleMenuIds).isEmpty();
        assertThat(permissionCacheService.batchRefreshedUserIds).containsExactly(5L, 6L);
        assertThat(sessionManager.forceLogoutUserIds).containsExactly(5L, 6L);
        assertThat(auditService.actions).containsExactly("角色菜单授权");
    }

    @Test
    void updateRoleMenusReturnsNotFoundWhenRoleDoesNotExist() {
        FakePermissionCacheService permissionCacheService = new FakePermissionCacheService();
        FakeSessionManager sessionManager = new FakeSessionManager();
        AdminSystemService serviceWithSecurity = new AdminSystemService(
                mapperSupport, auditService, provider(permissionCacheService), provider(sessionManager), null);
        mapperSupport.affectedUserIdsByRole = List.of(5L);
        mapperSupport.replaceRoleMenusAffected = false;

        Result<String> result = serviceWithSecurity.updateRoleMenus(8L, roleMenuRequest(), null);

        assertNotFound(result, "角色不存在");
        assertThat(mapperSupport.replacedRoleMenuRoleId).isEqualTo(8L);
        assertThat(permissionCacheService.batchRefreshedUserIds).isEmpty();
        assertThat(sessionManager.forceLogoutUserIds).isEmpty();
        assertThat(auditService.actions).isEmpty();
    }

    @Test
    void updateRoleMenusRejectsInvalidMenuIdsBeforeMapperSupportWrite() {
        RoleMenuRequest request = roleMenuRequest();
        request.setMenuIds(List.of(1L, -1L));

        Result<String> result = service.updateRoleMenus(8L, request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("菜单ID必须大于0");
        assertThat(mapperSupport.checkedMenuIds).isEmpty();
        assertThat(mapperSupport.replacedRoleMenuRoleId).isNull();
    }

    @Test
    void updateRoleMenusReturnsNotFoundWhenMenuIdsDoNotExist() {
        RoleMenuRequest request = roleMenuRequest();
        request.setMenuIds(List.of(11L, 11L, 12L));
        mapperSupport.menusExist = false;

        Result<String> result = service.updateRoleMenus(8L, request, null);

        assertNotFound(result, "菜单不存在");
        assertThat(mapperSupport.checkedMenuIds).containsExactly(11L, 12L);
        assertThat(mapperSupport.replacedRoleMenuRoleId).isNull();
        assertThat(auditService.actions).isEmpty();
    }

    @Test
    void menuValidationRejectsInvalidTypeAndSelfParent() {
        MenuRequest invalidType = new MenuRequest();
        invalidType.setMenuType("\u00A0page\u3000");
        invalidType.setMenuName("首页");

        MenuRequest selfParent = new MenuRequest();
        selfParent.setMenuType("\u3000menu\u00A0");
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
    void createMenuReturnsNotFoundWhenParentMenuDoesNotExist() {
        MenuRequest request = menuRequest();
        request.setParentId(99L);
        mapperSupport.menusExist = false;

        Result<Long> result = service.createMenu(request, null);

        assertNotFound(result, "上级菜单不存在");
        assertThat(mapperSupport.checkedMenuIds).containsExactly(99L);
        assertThat(mapperSupport.createdMenu).isNull();
    }

    @Test
    void updateMenuRejectsDescendantParentBeforeMapperSupportWrite() {
        MenuRequest request = menuRequest();
        request.setParentId(12L);
        mapperSupport.menuDescendant = true;

        Result<String> result = service.updateMenu(11L, request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("上级菜单不能选择自己的下级");
        assertThat(mapperSupport.checkedMenuIds).containsExactly(12L);
        assertThat(mapperSupport.checkedMenuId).isEqualTo(11L);
        assertThat(mapperSupport.checkedDescendantMenuId).isEqualTo(12L);
        assertThat(mapperSupport.updatedMenuId).isNull();
    }

    @Test
    void createMenuClearsAllPermissionCacheAndForcesAllSessionsOffline() {
        FakePermissionCacheService permissionCacheService = new FakePermissionCacheService();
        FakeSessionManager sessionManager = new FakeSessionManager();
        AdminSystemService serviceWithSecurity = new AdminSystemService(
                mapperSupport, auditService, provider(permissionCacheService), provider(sessionManager), null);
        MenuRequest request = new MenuRequest();
        request.setMenuType("MENU");
        request.setMenuName("通知中心");
        request.setParentId(0L);

        Result<Long> result = serviceWithSecurity.createMenu(request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(12L);
        assertThat(mapperSupport.createdMenu).isSameAs(request);
        assertThat(permissionCacheService.clearAllCount).isEqualTo(1);
        assertThat(sessionManager.forceLogoutAllCount).isEqualTo(1);
        assertThat(auditService.actions).containsExactly("新增菜单");
    }

    @Test
    void updateMenuClearsAllPermissionCacheAndForcesAllSessionsOffline() {
        FakePermissionCacheService permissionCacheService = new FakePermissionCacheService();
        FakeSessionManager sessionManager = new FakeSessionManager();
        AdminSystemService serviceWithSecurity = new AdminSystemService(
                mapperSupport, auditService, provider(permissionCacheService), provider(sessionManager), null);
        MenuRequest request = new MenuRequest();
        request.setMenuType("MENU");
        request.setMenuName("日志中心");
        request.setParentId(0L);

        Result<String> result = serviceWithSecurity.updateMenu(11L, request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(mapperSupport.updatedMenuId).isEqualTo(11L);
        assertThat(mapperSupport.updatedMenu).isSameAs(request);
        assertThat(permissionCacheService.clearAllCount).isEqualTo(1);
        assertThat(sessionManager.forceLogoutAllCount).isEqualTo(1);
        assertThat(auditService.actions).containsExactly("更新菜单");
    }

    @Test
    void menuIdOperationsRejectInvalidIdBeforeMapperSupportLookup() {
        mapperSupport.commandFailure = new RuntimeException("database down");

        assertInvalidResourceId(service.updateMenu(0L, menuRequest(), null), "菜单ID必须大于0");
        assertInvalidResourceId(service.deleteMenu(0L, null), "菜单ID必须大于0");

        assertThat(mapperSupport.updatedMenuId).isNull();
        assertThat(mapperSupport.deletedMenuId).isNull();
    }

    @Test
    void dictTypeIdOperationsRejectInvalidIdBeforeMapperSupportLookup() {
        mapperSupport.commandFailure = new RuntimeException("database down");

        assertInvalidResourceId(service.updateDictType(0L, dictTypeRequest(), null), "字典类型ID必须大于0");
        assertInvalidResourceId(service.deleteDictType(0L, null), "字典类型ID必须大于0");

        assertThat(mapperSupport.updatedDictTypeId).isNull();
        assertThat(mapperSupport.deletedDictTypeId).isNull();
    }

    @Test
    void dictItemIdOperationsRejectInvalidIdBeforeMapperSupportLookup() {
        mapperSupport.commandFailure = new RuntimeException("database down");

        assertInvalidResourceId(service.updateDictItem(0L, dictItemRequest(), null), "字典项ID必须大于0");
        assertInvalidResourceId(service.deleteDictItem(0L, null), "字典项ID必须大于0");

        assertThat(mapperSupport.updatedDictItemId).isNull();
        assertThat(mapperSupport.deletedDictItemId).isNull();
    }

    @Test
    void configIdOperationsRejectInvalidIdBeforeMapperSupportLookup() {
        mapperSupport.commandFailure = new RuntimeException("database down");

        assertInvalidResourceId(service.updateConfig(0L, configRequest(), null), "系统参数ID必须大于0");
        assertInvalidResourceId(service.deleteConfig(0L, null), "系统参数ID必须大于0");

        assertThat(mapperSupport.updatedConfigId).isNull();
        assertThat(mapperSupport.deletedConfigId).isNull();
    }

    @Test
    void createTenantValidatesRequiredFields() {
        TenantRequest request = new TenantRequest();
        request.setTenantCode("\u00A0tenant-a\u3000");
        request.setTenantName("\u00A0\u3000");

        Result<Long> result = service.createTenant(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("租户编码和名称不能为空");
    }

    @Test
    void writeEndpointsReturnServiceErrorWhenMapperSupportFails() {
        mapperSupport.commandFailure = new RuntimeException("database down");
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
    void unlockUserReturnsServiceErrorWhenMapperSupportFails() {
        mapperSupport.commandFailure = new RuntimeException("database down");

        Result<String> result = service.unlockUser(9L, null);

        assertServiceError(result, "用户解锁失败");
        assertThat(auditService.actions).isEmpty();
    }

    @Test
    void updateAndDeleteEndpointsReturnNotFoundWhenMapperSupportAffectsNoRows() {
        mapperSupport.updateTenantAffected = false;
        mapperSupport.deleteTenantAffected = false;
        mapperSupport.updateDeptAffected = false;
        mapperSupport.deleteDeptAffected = false;
        mapperSupport.updateUserAffected = false;
        mapperSupport.updateUserStatusAffected = false;
        mapperSupport.resetPasswordAffected = false;
        mapperSupport.deleteUserAffected = false;
        mapperSupport.updateRoleAffected = false;
        mapperSupport.deleteRoleAffected = false;
        mapperSupport.updateMenuAffected = false;
        mapperSupport.deleteMenuAffected = false;
        mapperSupport.updateDictTypeAffected = false;
        mapperSupport.deleteDictTypeAffected = false;
        mapperSupport.updateDictItemAffected = false;
        mapperSupport.deleteDictItemAffected = false;
        mapperSupport.updateConfigAffected = false;
        mapperSupport.deleteConfigAffected = false;

        assertNotFound(service.updateTenant(9L, tenantRequest(), null), "租户不存在");
        assertNotFound(service.deleteTenant(9L, null), "租户不存在");
        assertNotFound(service.updateDept(9L, deptRequest(), null), "部门不存在");
        assertNotFound(service.deleteDept(9L, null), "部门不存在");
        assertNotFound(service.updateUser(9L, userUpdateRequest(), null), "用户不存在");
        assertNotFound(service.updateUserStatus(9L, userStatusRequest(), null), "用户不存在");
        assertNotFound(service.resetPassword(9L, resetPasswordRequest(), null), "用户不存在");
        assertNotFound(service.deleteUser(9L, null), "用户不存在");
        assertNotFound(service.updateRole(9L, roleRequest(), null), "角色不存在");
        assertNotFound(service.deleteRole(9L, null), "角色不存在");
        assertNotFound(service.updateMenu(9L, menuRequest(), null), "菜单不存在");
        assertNotFound(service.deleteMenu(9L, null), "菜单不存在");
        assertNotFound(service.updateDictType(9L, dictTypeRequest(), null), "字典类型不存在");
        assertNotFound(service.deleteDictType(9L, null), "字典类型不存在");
        assertNotFound(service.updateDictItem(9L, dictItemRequest(), null), "字典项不存在");
        assertNotFound(service.deleteDictItem(9L, null), "字典项不存在");
        assertNotFound(service.updateConfig(9L, configRequest(), null), "系统参数不存在");
        assertNotFound(service.deleteConfig(9L, null), "系统参数不存在");
        assertThat(auditService.actions).isEmpty();
    }

    private static void assertServiceError(Result<?> result, String message) {
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo(message);
    }

    private static void assertNotFound(Result<?> result, String message) {
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
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

    private static class FakeMapperSupport extends AdminSystemMapperSupport {
        private List<AdminUser> users = List.of();
        private long userCount;
        private int listUserPageNum;
        private int listUserPageSize;
        private String listUserKeyword;
        private String listUserStatus;
        private String countUserKeyword;
        private String countUserStatus;
        private List<Tenant> tenants = List.of();
        private long tenantCount;
        private List<Tenant> tenantOptions = List.of();
        private String listTenantKeyword;
        private String listTenantStatus;
        private int listTenantPageNum;
        private int listTenantPageSize;
        private String countTenantKeyword;
        private String countTenantStatus;
        private String tenantOptionKeyword;
        private int tenantOptionLimit;
        private List<Role> roles = List.of();
        private long roleCount;
        private List<Role> roleOptions = List.of();
        private String listRoleKeyword;
        private String listRoleStatus;
        private int listRolePageNum;
        private int listRolePageSize;
        private String countRoleKeyword;
        private String countRoleStatus;
        private String roleOptionKeyword;
        private int roleOptionLimit;
        private List<ConfigItem> configs = List.of();
        private long configCount;
        private String listConfigKeyword;
        private int listConfigPageNum;
        private int listConfigPageSize;
        private String countConfigKeyword;
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
        private boolean rolesExist = true;
        private boolean menusExist = true;
        private boolean menuDescendant;
        private List<Long> checkedRoleIds = List.of();
        private List<Long> checkedMenuIds = List.of();
        private Long checkedMenuId;
        private Long checkedDescendantMenuId;
        private Long updatedRoleId;
        private RoleRequest updatedRole;
        private Long deletedRoleId;
        private Long replacedRoleMenuRoleId;
        private List<Long> replacedRoleMenuIds = List.of();
        private boolean replaceRoleMenusAffected = true;
        private MenuRequest createdMenu;
        private Long updatedMenuId;
        private MenuRequest updatedMenu;
        private Long deletedMenuId;
        private Long updatedDictTypeId;
        private Long deletedDictTypeId;
        private Long updatedDictItemId;
        private Long deletedDictItemId;
        private Long updatedConfigId;
        private Long deletedConfigId;
        private boolean updateTenantAffected = true;
        private boolean deleteTenantAffected = true;
        private boolean updateDeptAffected = true;
        private boolean deleteDeptAffected = true;
        private boolean updateUserAffected = true;
        private boolean updateUserStatusAffected = true;
        private boolean resetPasswordAffected = true;
        private boolean deleteUserAffected = true;
        private boolean updateRoleAffected = true;
        private boolean deleteRoleAffected = true;
        private boolean updateMenuAffected = true;
        private boolean deleteMenuAffected = true;
        private boolean updateDictTypeAffected = true;
        private boolean deleteDictTypeAffected = true;
        private boolean updateDictItemAffected = true;
        private boolean deleteDictItemAffected = true;
        private boolean updateConfigAffected = true;
        private boolean deleteConfigAffected = true;
        private RuntimeException queryFailure;
        private RuntimeException commandFailure;

        private FakeMapperSupport() {
            super(null);
        }

        @Override
        public List<Tenant> listTenants(String keyword, String status, int pageNum, int pageSize) {
            failQueryIfNeeded();
            this.listTenantKeyword = keyword;
            this.listTenantStatus = status;
            this.listTenantPageNum = pageNum;
            this.listTenantPageSize = pageSize;
            return new ArrayList<>(tenants);
        }

        @Override
        public long countTenants(String keyword, String status) {
            failQueryIfNeeded();
            this.countTenantKeyword = keyword;
            this.countTenantStatus = status;
            return tenantCount;
        }

        @Override
        public List<Tenant> listTenantOptions(String keyword, int limit) {
            failQueryIfNeeded();
            this.tenantOptionKeyword = keyword;
            this.tenantOptionLimit = limit;
            return new ArrayList<>(tenantOptions);
        }

        @Override
        public List<Dept> listDeptTree(Long tenantId) {
            failQueryIfNeeded();
            return List.of();
        }

        @Override
        public List<AdminUser> listUsers(String keyword, String status, int pageNum, int pageSize) {
            failQueryIfNeeded();
            this.listUserKeyword = keyword;
            this.listUserStatus = status;
            this.listUserPageNum = pageNum;
            this.listUserPageSize = pageSize;
            return new ArrayList<>(users);
        }

        @Override
        public long countUsers(String keyword, String status) {
            failQueryIfNeeded();
            this.countUserKeyword = keyword;
            this.countUserStatus = status;
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
        public boolean updateTenant(Long id, TenantRequest request) {
            failCommandIfNeeded();
            this.updatedTenantId = id;
            return updateTenantAffected;
        }

        @Override
        public long countUsersByTenant(Long tenantId) {
            failCommandIfNeeded();
            return tenantUserCount;
        }

        @Override
        public boolean deleteTenant(Long id) {
            failCommandIfNeeded();
            this.deletedTenantId = id;
            return deleteTenantAffected;
        }

        @Override
        public Long createDept(DeptRequest request) {
            failCommandIfNeeded();
            return 11L;
        }

        @Override
        public boolean updateDept(Long id, DeptRequest request) {
            failCommandIfNeeded();
            this.updatedDeptId = id;
            return updateDeptAffected;
        }

        @Override
        public boolean deleteDept(Long id) {
            failCommandIfNeeded();
            this.deletedDeptId = id;
            return deleteDeptAffected;
        }

        @Override
        public Long createUser(UserCreateRequest request, String passwordHash) {
            failCommandIfNeeded();
            this.createdUser = request;
            this.createdPasswordHash = passwordHash;
            return nextUserId;
        }

        @Override
        public boolean updateUser(Long userId, UserUpdateRequest request) {
            failCommandIfNeeded();
            this.updatedUserId = userId;
            this.updatedUser = request;
            return updateUserAffected;
        }

        @Override
        public boolean updateUserStatus(Long userId, String status) {
            failCommandIfNeeded();
            this.updatedUserStatusId = userId;
            this.updatedUserStatus = status;
            return updateUserStatusAffected;
        }

        @Override
        public boolean resetPassword(Long userId, String passwordHash) {
            failCommandIfNeeded();
            this.resetPasswordUserId = userId;
            this.resetPasswordHash = passwordHash;
            return resetPasswordAffected;
        }

        @Override
        public boolean deleteUser(Long userId) {
            failCommandIfNeeded();
            this.deletedUserId = userId;
            return deleteUserAffected;
        }

        @Override
        public Long createRole(RoleRequest request) {
            failCommandIfNeeded();
            this.createdRole = request;
            return 3L;
        }

        @Override
        public boolean allRolesExist(List<Long> roleIds) {
            failCommandIfNeeded();
            this.checkedRoleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
            return rolesExist;
        }

        @Override
        public List<Role> listRoles(String keyword, String status, int pageNum, int pageSize) {
            failQueryIfNeeded();
            this.listRoleKeyword = keyword;
            this.listRoleStatus = status;
            this.listRolePageNum = pageNum;
            this.listRolePageSize = pageSize;
            return new ArrayList<>(roles);
        }

        @Override
        public long countRoles(String keyword, String status) {
            failQueryIfNeeded();
            this.countRoleKeyword = keyword;
            this.countRoleStatus = status;
            return roleCount;
        }

        @Override
        public List<Role> listRoleOptions(String keyword, int limit) {
            failQueryIfNeeded();
            this.roleOptionKeyword = keyword;
            this.roleOptionLimit = limit;
            return new ArrayList<>(roleOptions);
        }

        @Override
        public boolean updateRole(Long roleId, RoleRequest request) {
            failCommandIfNeeded();
            this.updatedRoleId = roleId;
            this.updatedRole = request;
            return updateRoleAffected;
        }

        @Override
        public List<Long> listUserIdsByRoleId(Long roleId) {
            failCommandIfNeeded();
            return affectedUserIdsByRole;
        }

        @Override
        public boolean deleteRole(Long roleId) {
            failCommandIfNeeded();
            this.deletedRoleId = roleId;
            return deleteRoleAffected;
        }

        @Override
        public List<Long> listMenuIdsByRoleId(Long roleId) {
            failQueryIfNeeded();
            return List.of();
        }

        @Override
        public boolean replaceRoleMenus(Long roleId, List<Long> menuIds) {
            failCommandIfNeeded();
            this.replacedRoleMenuRoleId = roleId;
            this.replacedRoleMenuIds = menuIds == null ? List.of() : List.copyOf(menuIds);
            return replaceRoleMenusAffected;
        }

        @Override
        public boolean allMenusExist(List<Long> menuIds) {
            failCommandIfNeeded();
            this.checkedMenuIds = menuIds == null ? List.of() : List.copyOf(menuIds);
            return menusExist;
        }

        @Override
        public boolean isMenuDescendant(Long menuId, Long possibleDescendantId) {
            failCommandIfNeeded();
            this.checkedMenuId = menuId;
            this.checkedDescendantMenuId = possibleDescendantId;
            return menuDescendant;
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
        public boolean updateMenu(Long menuId, MenuRequest request) {
            failCommandIfNeeded();
            this.updatedMenuId = menuId;
            this.updatedMenu = request;
            return updateMenuAffected;
        }

        @Override
        public boolean deleteMenu(Long menuId) {
            failCommandIfNeeded();
            this.deletedMenuId = menuId;
            return deleteMenuAffected;
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
        public List<ConfigItem> listConfigs(String keyword, int pageNum, int pageSize) {
            failQueryIfNeeded();
            this.listConfigKeyword = keyword;
            this.listConfigPageNum = pageNum;
            this.listConfigPageSize = pageSize;
            return configs;
        }

        @Override
        public long countConfigs(String keyword) {
            failQueryIfNeeded();
            this.countConfigKeyword = keyword;
            return configCount;
        }

        @Override
        public Long createDictType(DictTypeRequest request) {
            failCommandIfNeeded();
            return 13L;
        }

        @Override
        public boolean updateDictType(Long id, DictTypeRequest request) {
            failCommandIfNeeded();
            this.updatedDictTypeId = id;
            return updateDictTypeAffected;
        }

        @Override
        public boolean deleteDictType(Long id) {
            failCommandIfNeeded();
            this.deletedDictTypeId = id;
            return deleteDictTypeAffected;
        }

        @Override
        public Long createDictItem(DictItemRequest request) {
            failCommandIfNeeded();
            return 14L;
        }

        @Override
        public boolean updateDictItem(Long id, DictItemRequest request) {
            failCommandIfNeeded();
            this.updatedDictItemId = id;
            return updateDictItemAffected;
        }

        @Override
        public boolean deleteDictItem(Long id) {
            failCommandIfNeeded();
            this.deletedDictItemId = id;
            return deleteDictItemAffected;
        }

        @Override
        public Long createConfig(ConfigRequest request) {
            failCommandIfNeeded();
            return 15L;
        }

        @Override
        public boolean updateConfig(Long id, ConfigRequest request) {
            failCommandIfNeeded();
            this.updatedConfigId = id;
            return updateConfigAffected;
        }

        @Override
        public boolean deleteConfig(Long id) {
            failCommandIfNeeded();
            this.deletedConfigId = id;
            return deleteConfigAffected;
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
        private Map<String, Object> params;

        private FakeAuditService() {
            super(null, null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            actions.add(action);
            this.params = (Map<String, Object>) params;
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
