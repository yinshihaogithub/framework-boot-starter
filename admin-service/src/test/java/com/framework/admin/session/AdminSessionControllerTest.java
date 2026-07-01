package com.framework.admin.session;

import com.framework.auth.service.SessionManager;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSessionControllerTest {

    @Test
    void listSessionsReturnsPagedResultWithRequestParams() {
        RecordingSessionService service = new RecordingSessionService();
        AdminSessionController controller = new AdminSessionController(service);

        Result<PageResult<SessionManager.OnlineSession>> result = controller.listSessions(2, 50);

        assertThat(result.getData().getRecords())
                .extracting(SessionManager.OnlineSession::userId)
                .containsExactly(7L);
        assertThat(result.getData().getTotal()).isEqualTo(70);
        assertThat(service.pageNum).isEqualTo(2);
        assertThat(service.pageSize).isEqualTo(50);
    }

    private static final class RecordingSessionService extends AdminSessionService {

        private int pageNum;
        private int pageSize;

        private RecordingSessionService() {
            super(null, null);
        }

        @Override
        public PageResult<SessionManager.OnlineSession> listSessions(int pageNum, int pageSize) {
            this.pageNum = pageNum;
            this.pageSize = pageSize;
            return PageResult.of(List.of(new SessionManager.OnlineSession(
                    7L, "admin", "1", "web", 100L, 3600L)), 70, pageNum, pageSize);
        }
    }
}
