package com.framework.admin.auth;

import com.framework.admin.system.AdminSystemModels.AdminUser;
import com.framework.admin.system.AdminSystemModels.Menu;
import com.framework.admin.system.AdminSystemRepository;
import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.auth.service.LoginSecurityService;
import com.framework.auth.service.SessionManager;
import com.framework.core.constant.FrameworkConstants;
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
    void logoutPassesPureBearerTokenToSessionManager() {
        Result<String> result = service.logout(FrameworkConstants.TOKEN_PREFIX + "abc.def");

        assertThat(result.isSuccess()).isTrue();
        assertThat(sessionManager.logoutToken).isEqualTo("abc.def");
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

    private static class FakeRepository extends AdminSystemRepository {
        private AdminUser user;
        private List<Menu> menus = List.of();
        private Long lastLoginUserId;
        private final List<LoginLogRecord> loginLogs = new ArrayList<>();

        private FakeRepository() {
            super(null);
        }

        @Override
        public Optional<AdminUser> findUserByUsername(String username) {
            return user != null && user.getUsername().equals(username) ? Optional.of(user) : Optional.empty();
        }

        @Override
        public Optional<AdminUser> findUserById(Long id) {
            return user != null && user.getId().equals(id) ? Optional.of(user) : Optional.empty();
        }

        @Override
        public void updateLastLogin(Long userId) {
            this.lastLoginUserId = userId;
        }

        @Override
        public void insertLoginLog(String username, Long userId, String clientIp, boolean success, String message) {
            loginLogs.add(new LoginLogRecord(username, userId, clientIp, success, message));
        }

        @Override
        public List<Menu> listMenusByUserId(Long userId) {
            return menus;
        }
    }

    private static class FakeSessionManager extends SessionManager {
        private String createdDeviceId;
        private String logoutToken;

        private FakeSessionManager() {
            super(null, null, 0);
        }

        @Override
        public LoginUser createSession(Long userId, String username, String tenantId, String deviceId,
                                       String[] roles, String[] permissions) {
            this.createdDeviceId = deviceId;
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
            this.logoutToken = accessToken;
        }
    }

    private record LoginLogRecord(String username, Long userId, String clientIp, boolean success, String message) {
    }
}
