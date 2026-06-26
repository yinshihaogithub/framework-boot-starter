package com.framework.auth.filter;

import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.auth.jwt.JwtUtils;
import com.framework.auth.service.SessionManager;
import com.framework.core.constant.FrameworkConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenAuthFilterTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-chars";

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void restoresExistingUserContextWhenRequestIsUnauthorized() throws Exception {
        LoginUser previous = new LoginUser().setUserId(99L).setUsername("previous");
        UserContextHolder.set(previous);
        TokenAuthFilter filter = new TokenAuthFilter(new JwtUtils(SECRET, 3600, 86400),
                new StubSessionManager(null), Set.of());

        filter.doFilter(new MockHttpServletRequest("GET", "/secure"),
                new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(UserContextHolder.get()).isSameAs(previous);
    }

    @Test
    void injectsLoginUserFromSessionAndClearsItAfterRequest() throws Exception {
        LoginUser loginUser = new LoginUser()
                .setUserId(1L)
                .setUsername("alice")
                .setTenantId("tenant-a")
                .setDeviceId("web")
                .setAccessToken("token")
                .setRoles(new String[]{"ADMIN"})
                .setPermissions(new String[]{"user:view"});
        TokenAuthFilter filter = new TokenAuthFilter(new JwtUtils(SECRET, 3600, 86400),
                new StubSessionManager(loginUser), Set.of());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure");
        request.addHeader(FrameworkConstants.AUTH_HEADER, FrameworkConstants.TOKEN_PREFIX + "token");
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.userDuringRequest).isSameAs(loginUser);
        assertThat(UserContextHolder.get()).isNull();
    }

    @Test
    void restoresPreviousUserContextAfterAuthenticatedRequest() throws Exception {
        LoginUser previous = new LoginUser().setUserId(99L).setUsername("previous");
        UserContextHolder.set(previous);
        LoginUser loginUser = new LoginUser()
                .setUserId(1L)
                .setUsername("alice")
                .setTenantId("tenant-a")
                .setDeviceId("web")
                .setAccessToken("token")
                .setRoles(new String[]{"ADMIN"})
                .setPermissions(new String[]{"user:view"});
        TokenAuthFilter filter = new TokenAuthFilter(new JwtUtils(SECRET, 3600, 86400),
                new StubSessionManager(loginUser), Set.of());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure");
        request.addHeader(FrameworkConstants.AUTH_HEADER, FrameworkConstants.TOKEN_PREFIX + "token");
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.userDuringRequest).isSameAs(loginUser);
        assertThat(UserContextHolder.get()).isSameAs(previous);
    }

    private static final class CapturingFilterChain extends MockFilterChain {
        private LoginUser userDuringRequest;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response) {
            userDuringRequest = UserContextHolder.get();
        }
    }

    private static final class StubSessionManager extends SessionManager {

        private final LoginUser loginUser;

        private StubSessionManager(LoginUser loginUser) {
            super(null, null, 0);
            this.loginUser = loginUser;
        }

        @Override
        public boolean validateAccessToken(String accessToken) {
            return loginUser != null;
        }

        @Override
        public LoginUser getLoginUser(String accessToken) {
            return loginUser;
        }
    }
}
