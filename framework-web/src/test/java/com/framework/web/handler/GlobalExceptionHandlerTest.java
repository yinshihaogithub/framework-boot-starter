package com.framework.web.handler;

import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Set;

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
    void bindExceptionReturnsGlobalErrorMessage() {
        BindException exception = new BindException(new Object(), "request");
        exception.addError(new ObjectError("request", "参数组合无效"));

        Result<Void> result = handler.handleBindException(exception);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("参数组合无效");
    }

    @Test
    void emptyBindExceptionReturnsDefaultParamErrorMessage() {
        Result<Void> result = handler.handleBindException(new BindException(new Object(), "request"));

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo(ResultCode.PARAM_ERROR.getMessage());
    }

    @Test
    void emptyConstraintViolationReturnsDefaultParamErrorMessage() {
        Result<Void> result = handler.handleConstraintViolation(new ConstraintViolationException(Set.of()));

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo(ResultCode.PARAM_ERROR.getMessage());
    }

    @Test
    void missingHeaderReturnsParamErrorResult() {
        Result<Void> result = handler.handleMissingHeader(
                new MissingRequestHeaderException("X-Tenant-Id", null));

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("缺少必需请求头: X-Tenant-Id");
    }

    @Test
    void mediaTypeNotSupportedReturnsBadRequestResult() {
        Result<Void> result = handler.handleMediaTypeNotSupported(new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertThat(result.getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode());
        assertThat(result.getMessage()).isEqualTo("Content-Type 不支持");
    }

    @Test
    void mediaTypeNotAcceptableReturnsBadRequestResult() {
        Result<Void> result = handler.handleMediaTypeNotAcceptable(new HttpMediaTypeNotAcceptableException(
                List.of(MediaType.APPLICATION_JSON)));

        assertThat(result.getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode());
        assertThat(result.getMessage()).isEqualTo("Accept 不支持");
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
