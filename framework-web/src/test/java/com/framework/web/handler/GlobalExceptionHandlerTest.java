package com.framework.web.handler;

import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

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
}
