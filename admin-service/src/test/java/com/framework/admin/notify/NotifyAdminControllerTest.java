package com.framework.admin.notify;

import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotifyAdminControllerTest {

    @Test
    void updateTemplateReportsNotFoundWhenTemplateDoesNotExist() {
        NotifyAdminController controller = new NotifyAdminController(notFoundService());

        Result<String> result = controller.updateTemplate(99L, new NotifyAdminModels.TemplateRequest(), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("模板不存在");
    }

    @Test
    void sendTestReportsNotFoundWhenTemplateDoesNotExist() {
        NotifyAdminController controller = new NotifyAdminController(notFoundService());

        Result<NotifyAdminModels.Record> result = controller.sendTest(99L, null, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("模板不存在");
    }

    private static NotifyAdminService notFoundService() {
        return new NotifyAdminService(null, null, null) {
            @Override
            public ActionResult<String> updateTemplate(Long id, NotifyAdminModels.TemplateRequest request,
                                                       jakarta.servlet.http.HttpServletRequest servletRequest) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "模板不存在");
            }

            @Override
            public ActionResult<NotifyAdminModels.Record> sendTest(Long id, NotifyAdminModels.SendRequest request,
                                                                   jakarta.servlet.http.HttpServletRequest servletRequest) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "模板不存在");
            }
        };
    }
}
