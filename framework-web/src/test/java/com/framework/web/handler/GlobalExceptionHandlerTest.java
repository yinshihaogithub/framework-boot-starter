package com.framework.web.handler;

import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentReturnsParamErrorResult() {
        Result<Void> result = handler.handleIllegalArgument(new IllegalArgumentException("状态只能是 ENABLED 或 DISABLED"));

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("状态只能是 ENABLED 或 DISABLED");
    }

    @Test
    void blankIllegalArgumentMessageReturnsDefaultParamErrorMessage() {
        Result<Void> result = handler.handleIllegalArgument(new IllegalArgumentException(" "));

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo(ResultCode.PARAM_ERROR.getMessage());
    }

    @Test
    void typeMismatchReturnsParamErrorResult() {
        Result<Void> result = handler.handleTypeMismatch(new MethodArgumentTypeMismatchException(
                "unknown", TestStatus.class, "status", null, null));

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("参数 status 类型错误");
    }

    @Test
    void noHandlerFoundReturnsNotFoundResult() {
        Result<Void> result = handler.handleNotFound(new NoHandlerFoundException(
                "GET", "/admin/missing", null));

        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("接口不存在: /admin/missing");
    }

    @Test
    void noResourceFoundReturnsNotFoundResult() {
        Result<Void> result = handler.handleNoResourceFound(new NoResourceFoundException(
                HttpMethod.POST, "admin/excel/tasks/demo-export"));

        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("接口不存在: /admin/excel/tasks/demo-export");
    }

    private enum TestStatus {
        PENDING
    }
}
