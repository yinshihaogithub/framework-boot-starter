package com.framework.admin.excel;

import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.core.result.PageResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelAdminControllerTest {

    @Test
    void exportTaskReportsServiceUnavailableWhenExportRuntimeIsMissing() {
        ExcelAdminController controller = new ExcelAdminController(
                new ExcelAdminService(null, new EmptyObjectProvider<>(), null));

        Result<ExcelAdminModels.TaskResult> result = controller.createExportTask(null, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("Excel导出服务未启用");
    }

    @Test
    void errorsEndpointPassesPagingToService() {
        RecordingExcelAdminService service = new RecordingExcelAdminService();
        ExcelAdminController controller = new ExcelAdminController(service);

        Result<PageResult<ExcelAdminModels.ErrorRecord>> result = controller.errors(7L, 2, 50);

        assertThat(result.isSuccess()).isTrue();
        assertThat(service.taskId).isEqualTo(7L);
        assertThat(service.pageNum).isEqualTo(2);
        assertThat(service.pageSize).isEqualTo(50);
        assertThat(result.getData().getPageNum()).isEqualTo(2);
        assertThat(result.getData().getPageSize()).isEqualTo(50);
    }

    private static class RecordingExcelAdminService extends ExcelAdminService {
        private Long taskId;
        private int pageNum;
        private int pageSize;

        private RecordingExcelAdminService() {
            super(null, new EmptyObjectProvider<>(), null);
        }

        @Override
        public PageResult<ExcelAdminModels.ErrorRecord> errors(Long taskId, int pageNum, int pageSize) {
            this.taskId = taskId;
            this.pageNum = pageNum;
            this.pageSize = pageSize;
            return PageResult.empty(pageNum, pageSize);
        }
    }

    private static class EmptyObjectProvider<T> implements org.springframework.beans.factory.ObjectProvider<T> {
        @Override
        public T getObject(Object... args) {
            return null;
        }

        @Override
        public T getIfAvailable() {
            return null;
        }

        @Override
        public T getIfUnique() {
            return null;
        }

        @Override
        public T getObject() {
            return null;
        }
    }
}
