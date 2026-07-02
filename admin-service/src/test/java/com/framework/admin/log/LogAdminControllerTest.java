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
    void statsReturnsServiceStats() {
        RecordingLogAdminService service = new RecordingLogAdminService();
        service.stats = Map.of("total", 4L, "operation", 2L, "api", 1L, "exception", 1L);
        LogAdminController controller = new LogAdminController(service);

        Result<Map<String, Long>> result = controller.stats();

        assertThat(result.isSuccess()).isTrue();
        assertThat(service.statsCalls).isEqualTo(1);
        assertThat(result.getData()).containsAllEntriesOf(service.stats);
    }

    @Test
    void detailReturnsOperationLogFromService() {
        RecordingLogAdminService service = new RecordingLogAdminService();
        service.detailResult = LogAdminService.ActionResult.success(operationLog(9L));
        LogAdminController controller = new LogAdminController(service);

        Result<OperationLogEntity> result = controller.detail(9L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(service.detailId).isEqualTo(9L);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(9L);
    }

    @Test
    void detailMapsServiceFailureToResult() {
        RecordingLogAdminService service = new RecordingLogAdminService();
        service.detailResult = LogAdminService.ActionResult.fail(ResultCode.NOT_FOUND, "日志不存在");
        LogAdminController controller = new LogAdminController(service);

        Result<OperationLogEntity> result = controller.detail(404L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(service.detailId).isEqualTo(404L);
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("日志不存在");
    }

    @Test
    void traceRejectsBlankTraceId() {
        RecordingLogAdminService service = new RecordingLogAdminService();
        LogAdminController controller = new LogAdminController(service);

        Result<PageResult<OperationLogEntity>> result = controller.trace("\u00A0\u3000", 1, 50);

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

        Result<PageResult<OperationLogEntity>> result = controller.trace("\u00A0trace-a\u3000", -1, 500);

        assertThat(result.isSuccess()).isTrue();
        assertThat(service.traceId).isEqualTo("trace-a");
        assertThat(service.pageNum).isEqualTo(-1);
        assertThat(service.pageSize).isEqualTo(500);
    }

    private static final class RecordingLogAdminService extends LogAdminService {
        private Map<String, Long> stats = Map.of();
        private int statsCalls;
        private Long detailId;
        private ActionResult<OperationLogEntity> detailResult = ActionResult.success(operationLog(1L));
        private String traceId;
        private int pageNum;
        private int pageSize;

        private RecordingLogAdminService() {
            super(null, null);
        }

        @Override
        public ActionResult<OperationLogEntity> detail(Long id) {
            this.detailId = id;
            return detailResult;
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
            statsCalls++;
            return stats;
        }

        @Override
        public PageResult<LoginLog> loginLogs(String username, Boolean success, int pageNum, int pageSize) {
            return PageResult.empty(1, 20);
        }
    }

    private static OperationLogEntity operationLog(Long id) {
        OperationLogEntity log = new OperationLogEntity();
        log.setId(id);
        return log;
    }
}
