package com.framework.admin.excel;

import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
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
