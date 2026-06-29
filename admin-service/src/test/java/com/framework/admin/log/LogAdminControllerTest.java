package com.framework.admin.log;

import com.framework.admin.system.AdminSystemModels.LoginLog;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.log.entity.OperationLogEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogAdminControllerTest {

    @Test
    void traceRejectsBlankTraceId() {
        RecordingLogAdminService service = new RecordingLogAdminService();
        LogAdminController controller = new LogAdminController(service);

        Result<PageResult<OperationLogEntity>> result = controller.trace(" ", 1, 50);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("traceId 不能为空");
        assertThat(service.traceId).isNull();
    }

    @Test
    void traceRejectsUnsafeTraceIdBeforeQueryingLogs() {
        RecordingLogAdminService service = new RecordingLogAdminService();
        LogAdminController controller = new LogAdminController(service);

        Result<PageResult<OperationLogEntity>> result = controller.trace("bad\ntrace", 1, 50);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("traceId 不合法");
        assertThat(service.traceId).isNull();
    }

    @Test
    void traceTrimsTraceIdBeforeQueryingLogs() {
        RecordingLogAdminService service = new RecordingLogAdminService();
        LogAdminController controller = new LogAdminController(service);

        Result<PageResult<OperationLogEntity>> result = controller.trace(" trace-a ", -1, 500);

        assertThat(result.isSuccess()).isTrue();
        assertThat(service.traceId).isEqualTo("trace-a");
        assertThat(service.pageNum).isEqualTo(-1);
        assertThat(service.pageSize).isEqualTo(500);
    }

    private static final class RecordingLogAdminService extends LogAdminService {
        private String traceId;
        private int pageNum;
        private int pageSize;

        private RecordingLogAdminService() {
            super(null, null);
        }

        @Override
        public PageResult<OperationLogEntity> trace(String traceId, int pageNum, int pageSize) {
            this.traceId = traceId;
            this.pageNum = pageNum;
            this.pageSize = pageSize;
            return PageResult.empty(1, 20);
        }

        @Override
        public Map<String, Long> stats() {
            return Map.of();
        }

        @Override
        public PageResult<LoginLog> loginLogs(String username, Boolean success, int pageNum, int pageSize) {
            return PageResult.empty(1, 20);
        }
    }
}
