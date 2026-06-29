package com.framework.admin.system;

import com.framework.admin.audit.AdminAuditService;
import com.framework.admin.system.AdminSystemModels.AdminUser;
import com.framework.admin.system.AdminSystemModels.MenuRequest;
import com.framework.admin.system.AdminSystemModels.ResetPasswordRequest;
import com.framework.admin.system.AdminSystemModels.RoleRequest;
import com.framework.admin.system.AdminSystemModels.TenantRequest;
import com.framework.admin.system.AdminSystemModels.UserCreateRequest;
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

    private static class FakeRepository extends AdminSystemRepository {
        private List<AdminUser> users = List.of();
        private long userCount;
        private int listUserPageNum;
        private int listUserPageSize;
        private Long nextUserId = 1L;
        private UserCreateRequest createdUser;
        private String createdPasswordHash;
        private long tenantUserCount;
        private Long deletedTenantId;
        private RoleRequest createdRole;
        private Long resetPasswordUserId;
        private String resetPasswordHash;
        private AdminUser userById;
        private Long updatedUserId;
        private UserUpdateRequest updatedUser;
        private List<Long> affectedUserIdsByRole = List.of();
        private Long updatedRoleId;
        private RoleRequest updatedRole;
        private Long replacedRoleMenuRoleId;
        private List<Long> replacedRoleMenuIds = List.of();
        private MenuRequest createdMenu;
        private Long updatedMenuId;
        private MenuRequest updatedMenu;

        private FakeRepository() {
            super(null);
        }

        @Override
        public List<AdminUser> listUsers(String keyword, String status, int pageNum, int pageSize) {
            this.listUserPageNum = pageNum;
            this.listUserPageSize = pageSize;
            return new ArrayList<>(users);
        }

        @Override
        public long countUsers(String keyword, String status) {
            return userCount;
        }

        @Override
        public Optional<AdminUser> findUserById(Long id) {
            return Optional.ofNullable(userById);
        }

        @Override
        public Long createUser(UserCreateRequest request, String passwordHash) {
            this.createdUser = request;
            this.createdPasswordHash = passwordHash;
            return nextUserId;
        }

        @Override
        public void updateUser(Long userId, UserUpdateRequest request) {
            this.updatedUserId = userId;
            this.updatedUser = request;
        }

        @Override
        public long countUsersByTenant(Long tenantId) {
            return tenantUserCount;
        }

        @Override
        public void deleteTenant(Long id) {
            this.deletedTenantId = id;
        }

        @Override
        public void resetPassword(Long userId, String passwordHash) {
            this.resetPasswordUserId = userId;
            this.resetPasswordHash = passwordHash;
        }

        @Override
        public Long createRole(RoleRequest request) {
            this.createdRole = request;
            return 3L;
        }

        @Override
        public void updateRole(Long roleId, RoleRequest request) {
            this.updatedRoleId = roleId;
            this.updatedRole = request;
        }

        @Override
        public List<Long> listUserIdsByRoleId(Long roleId) {
            return affectedUserIdsByRole;
        }

        @Override
        public void replaceRoleMenus(Long roleId, List<Long> menuIds) {
            this.replacedRoleMenuRoleId = roleId;
            this.replacedRoleMenuIds = menuIds == null ? List.of() : List.copyOf(menuIds);
        }

        @Override
        public Long createMenu(MenuRequest request) {
            this.createdMenu = request;
            return 12L;
        }

        @Override
        public void updateMenu(Long menuId, MenuRequest request) {
            this.updatedMenuId = menuId;
            this.updatedMenu = request;
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
}
