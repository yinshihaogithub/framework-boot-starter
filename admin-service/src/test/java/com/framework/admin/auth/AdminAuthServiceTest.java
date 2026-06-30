package com.framework.admin.auth;

import com.framework.admin.system.AdminSystemModels.AdminUser;
import com.framework.admin.system.AdminSystemModels.Menu;
import com.framework.admin.system.AdminSystemRepository;
import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.auth.service.LoginSecurityService;
import com.framework.auth.service.SessionManager;
import com.framework.core.constant.FrameworkConstants;
import com.framework.core.exception.AuthException;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.crypto.util.PasswordUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AdminAuthServiceTest {

    private final FakeRepository repository = new FakeRepository();
    private final FakeSessionManager sessionManager = new FakeSessionManager();
    private final AdminAuthService service = new AdminAuthService(repository, sessionManager, provider((LoginSecurityService) null));

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void loginRejectsBlankCredentials() {
        AdminAuthController.LoginRequest request = new AdminAuthController.LoginRequest();
        request.setUsername("admin");

        Result<AdminAuthController.LoginResponse> result = service.login(request, "127.0.0.1");

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(repository.loginLogs).isEmpty();
    }

    @Test
    void loginRejectsTooLongUsernameBeforeQueryingRepository() {
        AdminAuthController.LoginRequest request = new AdminAuthController.LoginRequest();
        request.setUsername("a".repeat(65));
        request.setPassword("Admin@123");

        Result<AdminAuthController.LoginResponse> result = service.login(request, "127.0.0.1");

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("用户名长度不能超过64个字符");
        assertThat(repository.findByUsernameCalls).isZero();
        assertThat(repository.loginLogs).isEmpty();
    }

    @Test
    void loginRejectsTooLongDeviceIdBeforeCreatingSession() {
        AdminAuthController.LoginRequest request = new AdminAuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");
        request.setDeviceId("d".repeat(65));

        Result<AdminAuthController.LoginResponse> result = service.login(request, "127.0.0.1");

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("设备标识长度不能超过64个字符");
        assertThat(repository.findByUsernameCalls).isZero();
        assertThat(sessionManager.createdDeviceId).isNull();
    }

    @Test
    void loginCreatesSessionAndWritesSuccessLog() {
        repository.user = enabledUser();
        repository.menus = List.of(menu(1L, "dashboard"));
        AdminAuthController.LoginRequest request = new AdminAuthController.LoginRequest();
        request.setUsername(" admin ");
        request.setPassword("Admin@123");

        Result<AdminAuthController.LoginResponse> result = service.login(request, "10.0.0.8");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getAccessToken()).isEqualTo("access-token");
        assertThat(result.getData().getUser().getUsername()).isEqualTo("admin");
        assertThat(result.getData().getMenus()).hasSize(1);
        assertThat(sessionManager.createdDeviceId).isEqualTo("admin-web");
        assertThat(repository.lastLoginUserId).isEqualTo(1L);
        assertThat(repository.loginLogs)
                .extracting(LoginLogRecord::username, LoginLogRecord::userId, LoginLogRecord::clientIp,
                        LoginLogRecord::success, LoginLogRecord::message)
                .containsExactly(tuple("admin", 1L, "10.0.0.8", true, "登录成功"));
    }

    @Test
    void loginTrimsDeviceIdAndTruncatesClientIpForLoginLog() {
        repository.user = enabledUser();
        AdminAuthController.LoginRequest request = new AdminAuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");
        request.setDeviceId(" ios ");
        String longClientIp = "1".repeat(80);

        Result<AdminAuthController.LoginResponse> result = service.login(request, longClientIp);

        assertThat(result.isSuccess()).isTrue();
        assertThat(sessionManager.createdDeviceId).isEqualTo("ios");
        assertThat(repository.loginLogs)
                .extracting(LoginLogRecord::clientIp)
                .containsExactly("1".repeat(64));
    }

    @Test
    void loginAllowsUserWithoutRolePermissionOrMenuRows() {
        repository.user = enabledUser()
                .setRoles(null)
                .setPermissions(null);
        repository.menus = null;
        AdminAuthController.LoginRequest request = new AdminAuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");

        Result<AdminAuthController.LoginResponse> result = service.login(request, "10.0.0.8");

        assertThat(result.isSuccess()).isTrue();
        assertThat(sessionManager.createdRoles).isEmpty();
        assertThat(sessionManager.createdPermissions).isEmpty();
        assertThat(result.getData().getUser().getRoles()).isEmpty();
        assertThat(result.getData().getUser().getPermissions()).isEmpty();
        assertThat(result.getData().getMenus()).isEmpty();
    }

    @Test
    void loginRejectsDisabledUser() {
        repository.user = enabledUser().setStatus("DISABLED");
        AdminAuthController.LoginRequest request = new AdminAuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");

        Result<AdminAuthController.LoginResponse> result = service.login(request, "10.0.0.8");

        assertThat(result.getCode()).isEqualTo(ResultCode.LOGIN_FAIL.getCode());
        assertThat(sessionManager.createdDeviceId).isNull();
        assertThat(repository.lastLoginUserId).isNull();
        assertThat(repository.loginLogs)
                .extracting(LoginLogRecord::username, LoginLogRecord::userId, LoginLogRecord::clientIp,
                        LoginLogRecord::success, LoginLogRecord::message)
                .containsExactly(tuple("admin", 1L, "10.0.0.8", false, "账号或密码错误"));
    }

    @Test
    void loginPreservesAccountLockedCodeFromLoginSecurity() {
        FakeLoginSecurityService loginSecurityService = new FakeLoginSecurityService();
        loginSecurityService.lockedMessage = "账号已被锁定，请 10 分钟后再试";
        AdminAuthService lockedService = new AdminAuthService(repository, sessionManager, provider(loginSecurityService));
        AdminAuthController.LoginRequest request = new AdminAuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");

        Result<AdminAuthController.LoginResponse> result = lockedService.login(request, "10.0.0.8");

        assertThat(result.getCode()).isEqualTo(ResultCode.ACCOUNT_LOCKED.getCode());
        assertThat(result.getMessage()).isEqualTo("账号已被锁定，请 10 分钟后再试");
        assertThat(sessionManager.createdDeviceId).isNull();
        assertThat(repository.loginLogs)
                .extracting(LoginLogRecord::username, LoginLogRecord::userId, LoginLogRecord::clientIp,
                        LoginLogRecord::success, LoginLogRecord::message)
                .containsExactly(tuple("admin", null, "10.0.0.8", false, "账号已被锁定，请 10 分钟后再试"));
    }

    @Test
    void loginContinuesWhenLoginSecurityProviderFails() {
        repository.user = enabledUser();
        AdminAuthService serviceWithFailingProvider = new AdminAuthService(
                repository, sessionManager, failingProvider());
        AdminAuthController.LoginRequest request = new AdminAuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");

        Result<AdminAuthController.LoginResponse> result = serviceWithFailingProvider.login(request, "10.0.0.8");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getAccessToken()).isEqualTo("access-token");
        assertThat(repository.loginLogs)
                .extracting(LoginLogRecord::username, LoginLogRecord::success, LoginLogRecord::message)
                .containsExactly(tuple("admin", true, "登录成功"));
    }

    @Test
    void loginContinuesWhenLoginLogWriteFails() {
        repository.user = enabledUser();
        repository.loginLogFailure = new RuntimeException("database down");
        AdminAuthController.LoginRequest request = new AdminAuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");

        Result<AdminAuthController.LoginResponse> result = service.login(request, "10.0.0.8");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getAccessToken()).isEqualTo("access-token");
        assertThat(repository.lastLoginUserId).isEqualTo(1L);
        assertThat(repository.loginLogs).isEmpty();
    }

    @Test
    void loginReturnsServiceErrorWhenRepositoryFails() {
        repository.findByUsernameFailure = new RuntimeException("database down");
        AdminAuthController.LoginRequest request = new AdminAuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");

        Result<AdminAuthController.LoginResponse> result = service.login(request, "10.0.0.8");

        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("登录服务暂不可用");
        assertThat(sessionManager.createdDeviceId).isNull();
        assertThat(repository.loginLogs)
                .extracting(LoginLogRecord::username, LoginLogRecord::success, LoginLogRecord::message)
                .containsExactly(tuple("admin", false, "登录服务暂不可用"));
    }

    @Test
    void loginReturnsServiceErrorWhenSessionCreateFails() {
        repository.user = enabledUser();
        sessionManager.createSessionFailure = new RuntimeException("redis down");
        AdminAuthController.LoginRequest request = new AdminAuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");

        Result<AdminAuthController.LoginResponse> result = service.login(request, "10.0.0.8");

        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("登录服务暂不可用");
        assertThat(repository.lastLoginUserId).isNull();
    }

    @Test
    void loginDoesNotCreateSessionWhenMenuQueryFails() {
        repository.user = enabledUser();
        repository.menusFailure = new RuntimeException("database down");
        AdminAuthController.LoginRequest request = new AdminAuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");

        Result<AdminAuthController.LoginResponse> result = service.login(request, "10.0.0.8");

        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("登录服务暂不可用");
        assertThat(sessionManager.createdDeviceId).isNull();
        assertThat(repository.lastLoginUserId).isNull();
        assertThat(repository.loginLogs)
                .extracting(LoginLogRecord::username, LoginLogRecord::success, LoginLogRecord::message)
                .containsExactly(tuple("admin", false, "登录服务暂不可用"));
    }

    @Test
    void loginContinuesWhenLastLoginUpdateFails() {
        repository.user = enabledUser();
        repository.lastLoginFailure = new RuntimeException("database down");
        AdminAuthController.LoginRequest request = new AdminAuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");

        Result<AdminAuthController.LoginResponse> result = service.login(request, "10.0.0.8");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getAccessToken()).isEqualTo("access-token");
        assertThat(repository.loginLogs)
                .extracting(LoginLogRecord::username, LoginLogRecord::success, LoginLogRecord::message)
                .containsExactly(tuple("admin", true, "登录成功"));
    }

    @Test
    void meReturnsCurrentUserMenus() {
        repository.user = enabledUser();
        repository.menus = List.of(menu(1L, "dashboard"));
        UserContextHolder.set(new LoginUser().setUserId(1L));

        Result<AdminAuthController.CurrentUser> result = service.me();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getUserId()).isEqualTo(1L);
        assertThat(result.getData().getMenus()).hasSize(1);
    }

    @Test
    void meReturnsServiceErrorWhenRepositoryFails() {
        repository.findByIdFailure = new RuntimeException("database down");
        UserContextHolder.set(new LoginUser().setUserId(1L));

        Result<AdminAuthController.CurrentUser> result = service.me();

        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("当前用户查询失败");
    }

    @Test
    void logoutPassesPureBearerTokenToSessionManager() {
        Result<String> result = service.logout(FrameworkConstants.TOKEN_PREFIX + "abc.def");

        assertThat(result.isSuccess()).isTrue();
        assertThat(sessionManager.logoutToken).isEqualTo("abc.def");
    }

    @Test
    void logoutReturnsServiceErrorWhenSessionManagerFails() {
        sessionManager.logoutFailure = new RuntimeException("redis down");

        Result<String> result = service.logout(FrameworkConstants.TOKEN_PREFIX + "abc.def");

        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("退出登录失败");
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        repository.user = enabledUser();
        UserContextHolder.set(new LoginUser().setUserId(1L));
        AdminAuthController.ChangePasswordRequest request = changePasswordRequest("wrong", "NewAdmin@123");

        Result<String> result = service.changePassword(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.LOGIN_FAIL.getCode());
        assertThat(repository.resetPasswordUserId).isNull();
        assertThat(sessionManager.forceLogoutAllUserId).isNull();
    }

    @Test
    void changePasswordRejectsWeakNewPassword() {
        UserContextHolder.set(new LoginUser().setUserId(1L));
        AdminAuthController.ChangePasswordRequest request = changePasswordRequest("Admin@123", "weak");

        Result<String> result = service.changePassword(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(repository.resetPasswordUserId).isNull();
    }

    @Test
    void changePasswordUpdatesPasswordMarksDefaultPasswordChangedAndLogsOutSessions() {
        repository.user = enabledUser();
        UserContextHolder.set(new LoginUser().setUserId(1L));
        AdminAuthController.ChangePasswordRequest request = changePasswordRequest("Admin@123", "NewAdmin@123");

        Result<String> result = service.changePassword(request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(repository.resetPasswordUserId).isEqualTo(1L);
        assertThat(PasswordUtils.verify("NewAdmin@123", repository.resetPasswordHash)).isTrue();
        assertThat(repository.configUpdates)
                .containsExactly(new ConfigUpdate("admin.default.password.changed", "true"));
        assertThat(sessionManager.forceLogoutAllUserId).isEqualTo(1L);
    }

    @Test
    void changePasswordReturnsServiceErrorWhenRepositoryFails() {
        repository.findByIdFailure = new RuntimeException("database down");
        UserContextHolder.set(new LoginUser().setUserId(1L));
        AdminAuthController.ChangePasswordRequest request = changePasswordRequest("Admin@123", "NewAdmin@123");

        Result<String> result = service.changePassword(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("密码修改失败");
        assertThat(repository.resetPasswordUserId).isNull();
    }

    @Test
    void changePasswordReturnsServiceErrorWhenResetPasswordFails() {
        repository.user = enabledUser();
        repository.resetPasswordFailure = new RuntimeException("database down");
        UserContextHolder.set(new LoginUser().setUserId(1L));
        AdminAuthController.ChangePasswordRequest request = changePasswordRequest("Admin@123", "NewAdmin@123");

        Result<String> result = service.changePassword(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("密码修改失败");
        assertThat(repository.configUpdates).isEmpty();
        assertThat(sessionManager.forceLogoutAllUserId).isNull();
    }

    @Test
    void changePasswordReturnsUnauthorizedWhenPasswordRowIsMissing() {
        repository.user = enabledUser();
        repository.resetPasswordResult = false;
        UserContextHolder.set(new LoginUser().setUserId(1L));
        AdminAuthController.ChangePasswordRequest request = changePasswordRequest("Admin@123", "NewAdmin@123");

        Result<String> result = service.changePassword(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.UNAUTHORIZED.getCode());
        assertThat(repository.configUpdates).isEmpty();
        assertThat(sessionManager.forceLogoutAllUserId).isNull();
    }

    @Test
    void changePasswordReturnsServiceErrorWhenConfigUpdateFails() {
        repository.user = enabledUser();
        repository.configUpdateFailure = new RuntimeException("database down");
        UserContextHolder.set(new LoginUser().setUserId(1L));
        AdminAuthController.ChangePasswordRequest request = changePasswordRequest("Admin@123", "NewAdmin@123");

        Result<String> result = service.changePassword(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("密码修改失败");
        assertThat(repository.resetPasswordUserId).isEqualTo(1L);
        assertThat(sessionManager.forceLogoutAllUserId).isNull();
    }

    @Test
    void changePasswordReturnsServiceErrorWhenConfigUpdateMissesRow() {
        repository.user = enabledUser();
        repository.configUpdateResult = false;
        UserContextHolder.set(new LoginUser().setUserId(1L));
        AdminAuthController.ChangePasswordRequest request = changePasswordRequest("Admin@123", "NewAdmin@123");

        Result<String> result = service.changePassword(request, null);

        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("密码修改失败");
        assertThat(repository.resetPasswordUserId).isEqualTo(1L);
        assertThat(repository.configUpdates).isEmpty();
        assertThat(sessionManager.forceLogoutAllUserId).isNull();
    }

    @Test
    void changePasswordSucceedsWhenForceLogoutFails() {
        repository.user = enabledUser();
        sessionManager.forceLogoutFailure = new RuntimeException("redis down");
        UserContextHolder.set(new LoginUser().setUserId(1L));
        AdminAuthController.ChangePasswordRequest request = changePasswordRequest("Admin@123", "NewAdmin@123");

        Result<String> result = service.changePassword(request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(repository.resetPasswordUserId).isEqualTo(1L);
        assertThat(repository.configUpdates)
                .containsExactly(new ConfigUpdate("admin.default.password.changed", "true"));
    }

    private static AdminUser enabledUser() {
        return new AdminUser()
                .setId(1L)
                .setTenantId(1L)
                .setUsername("admin")
                .setNickname("系统管理员")
                .setStatus("ENABLED")
                .setPasswordHash(PasswordUtils.hash("Admin@123"))
                .setRoles(List.of("SUPER_ADMIN"))
                .setPermissions(List.of("dashboard:view"));
    }

    private static Menu menu(Long id, String routePath) {
        return new Menu()
                .setId(id)
                .setParentId(0L)
                .setMenuType("MENU")
                .setMenuName(routePath)
                .setRoutePath(routePath)
                .setVisible(true)
                .setChildren(List.of());
    }

    private static AdminAuthController.ChangePasswordRequest changePasswordRequest(String oldPassword,
                                                                                  String newPassword) {
        AdminAuthController.ChangePasswordRequest request = new AdminAuthController.ChangePasswordRequest();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);
        return request;
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

    private static class FakeRepository extends AdminSystemRepository {
        private AdminUser user;
        private List<Menu> menus = List.of();
        private int findByUsernameCalls;
        private Long lastLoginUserId;
        private final List<LoginLogRecord> loginLogs = new ArrayList<>();
        private Long resetPasswordUserId;
        private String resetPasswordHash;
        private final List<ConfigUpdate> configUpdates = new ArrayList<>();
        private RuntimeException loginLogFailure;
        private RuntimeException findByUsernameFailure;
        private RuntimeException findByIdFailure;
        private RuntimeException lastLoginFailure;
        private RuntimeException menusFailure;
        private RuntimeException resetPasswordFailure;
        private RuntimeException configUpdateFailure;
        private boolean resetPasswordResult = true;
        private boolean configUpdateResult = true;

        private FakeRepository() {
            super(null);
        }

        @Override
        public Optional<AdminUser> findUserByUsername(String username) {
            findByUsernameCalls++;
            if (findByUsernameFailure != null) {
                throw findByUsernameFailure;
            }
            return user != null && user.getUsername().equals(username) ? Optional.of(user) : Optional.empty();
        }

        @Override
        public Optional<AdminUser> findUserById(Long id) {
            if (findByIdFailure != null) {
                throw findByIdFailure;
            }
            return user != null && user.getId().equals(id) ? Optional.of(user) : Optional.empty();
        }

        @Override
        public void updateLastLogin(Long userId) {
            if (lastLoginFailure != null) {
                throw lastLoginFailure;
            }
            this.lastLoginUserId = userId;
        }

        @Override
        public void insertLoginLog(String username, Long userId, String clientIp, boolean success, String message) {
            if (loginLogFailure != null) {
                throw loginLogFailure;
            }
            loginLogs.add(new LoginLogRecord(username, userId, clientIp, success, message));
        }

        @Override
        public List<Menu> listMenusByUserId(Long userId) {
            if (menusFailure != null) {
                throw menusFailure;
            }
            return menus;
        }

        @Override
        public boolean resetPassword(Long userId, String passwordHash) {
            if (resetPasswordFailure != null) {
                throw resetPasswordFailure;
            }
            if (!resetPasswordResult) {
                return false;
            }
            this.resetPasswordUserId = userId;
            this.resetPasswordHash = passwordHash;
            return true;
        }

        @Override
        public boolean updateConfigValue(String configKey, String configValue) {
            if (configUpdateFailure != null) {
                throw configUpdateFailure;
            }
            if (!configUpdateResult) {
                return false;
            }
            configUpdates.add(new ConfigUpdate(configKey, configValue));
            return true;
        }
    }

    private static class FakeSessionManager extends SessionManager {
        private String createdDeviceId;
        private List<String> createdRoles = List.of();
        private List<String> createdPermissions = List.of();
        private String logoutToken;
        private Long forceLogoutAllUserId;
        private RuntimeException createSessionFailure;
        private RuntimeException logoutFailure;
        private RuntimeException forceLogoutFailure;

        private FakeSessionManager() {
            super(null, null, 0);
        }

        @Override
        public LoginUser createSession(Long userId, String username, String tenantId, String deviceId,
                                       String[] roles, String[] permissions) {
            if (createSessionFailure != null) {
                throw createSessionFailure;
            }
            this.createdDeviceId = deviceId;
            this.createdRoles = List.of(roles);
            this.createdPermissions = List.of(permissions);
            return new LoginUser()
                    .setUserId(userId)
                    .setUsername(username)
                    .setTenantId(tenantId)
                    .setDeviceId(deviceId)
                    .setAccessToken("access-token")
                    .setRoles(roles)
                    .setPermissions(permissions);
        }

        @Override
        public void logout(String accessToken) {
            if (logoutFailure != null) {
                throw logoutFailure;
            }
            this.logoutToken = accessToken;
        }

        @Override
        public void forceLogoutAll(Long userId) {
            if (forceLogoutFailure != null) {
                throw forceLogoutFailure;
            }
            this.forceLogoutAllUserId = userId;
        }
    }

    private static class FakeLoginSecurityService extends LoginSecurityService {
        private String lockedMessage;

        private FakeLoginSecurityService() {
            super(null, 3, 30);
        }

        @Override
        public void checkAccountLocked(String username) {
            if (lockedMessage != null) {
                throw new AuthException(ResultCode.ACCOUNT_LOCKED, lockedMessage);
            }
        }
    }

    private record LoginLogRecord(String username, Long userId, String clientIp, boolean success, String message) {
    }

    private record ConfigUpdate(String configKey, String configValue) {
    }
}
