package com.framework.admin.session;

import com.framework.admin.audit.AdminAuditService;
import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.auth.service.SessionManager;
import com.framework.core.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSessionServiceTest {

    private final FakeSessionManager sessionManager = new FakeSessionManager();
    private final RecordingAuditService auditService = new RecordingAuditService();
    private final AdminSessionService sessionService = new AdminSessionService(sessionManager, auditService);

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void listSessionsReturnsOnlineSessionsFromSessionManager() {
        List<SessionManager.OnlineSession> sessions = List.of(
                new SessionManager.OnlineSession(1L, "admin", "1", "admin-web", 100L, 3600L));
        sessionManager.sessions = sessions;

        assertThat(sessionService.listSessions()).containsExactlyElementsOf(sessions);
    }

    @Test
    void kickSessionRemovesExistingSessionAndWritesAudit() {
        HttpServletRequest request = new MockHttpServletRequest("DELETE", "/admin/sessions/2/web");
        sessionManager.sessions = List.of(new SessionManager.OnlineSession(2L, "bob", "1", "web", 100L, 3600L));

        AdminSessionService.ActionResult<String> result = sessionService.kickSession(2L, "web", request);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo("已强制下线");
        assertThat(sessionManager.kicked).containsExactly("2:web");
        assertThat(auditService.successActions).containsExactly("在线会话:强制下线:DELETE");
    }

    @Test
    void kickSessionRejectsCurrentSession() {
        UserContextHolder.set(new LoginUser().setUserId(1L).setDeviceId("admin-web"));

        AdminSessionService.ActionResult<String> result = sessionService.kickSession(1L, "admin-web", null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.message()).contains("当前会话");
        assertThat(sessionManager.kicked).isEmpty();
    }

    @Test
    void kickSessionReturnsNotFoundWhenSessionDoesNotExist() {
        sessionManager.sessions = List.of();

        AdminSessionService.ActionResult<String> result = sessionService.kickSession(3L, "web", null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(sessionManager.kicked).isEmpty();
    }

    private static final class FakeSessionManager extends SessionManager {

        private List<OnlineSession> sessions = List.of();
        private final List<String> kicked = new ArrayList<>();

        private FakeSessionManager() {
            super(null, null, 0);
        }

        @Override
        public List<OnlineSession> listOnlineSessions() {
            return sessions;
        }

        @Override
        public void forceLogout(Long userId, String deviceId) {
            kicked.add(userId + ":" + deviceId);
        }
    }

    private static final class RecordingAuditService extends AdminAuditService {

        private final List<String> successActions = new ArrayList<>();

        private RecordingAuditService() {
            super(null, null);
        }

        @Override
        public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            successActions.add(module + ":" + action + ":" + operationType);
        }
    }
}
