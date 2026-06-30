package com.framework.log.aspect;

import com.framework.core.trace.TraceContext;
import com.framework.log.annotation.OperationLog;
import com.framework.log.config.LogProperties;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.service.OperationLogStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationLogAspectTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void persistsOperationLogEntityWithDesensitizedPayloadAndRequestInfo() throws Exception {
        RecordingStorageService storageService = new RecordingStorageService();
        AuditedService service = proxy(new AuditedService(), storageService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TraceContext.putTraceId("trace-order");

        Map<String, Object> result = service.create(Map.of(
                "orderNo", "O-100",
                "password", "plain-secret",
                "mobile", "13800000000"
        ));

        OperationLogEntity entity = storageService.saved.get(3, TimeUnit.SECONDS);
        assertThat(result).containsEntry("status", "OK");
        assertThat(entity.getLogType()).isEqualTo("OPERATION");
        assertThat(entity.getModule()).isEqualTo("订单中心");
        assertThat(entity.getAction()).isEqualTo("创建订单");
        assertThat(entity.getOperationType()).isEqualTo("INSERT");
        assertThat(entity.getMethod()).isEqualTo("AuditedService.create");
        assertThat(entity.getUri()).isEqualTo("/orders");
        assertThat(entity.getHttpMethod()).isEqualTo("POST");
        assertThat(entity.getClientIp()).isEqualTo("203.0.113.7");
        assertThat(entity.getTraceId()).isEqualTo("trace-order");
        assertThat(entity.getSuccess()).isTrue();
        assertThat(entity.getParams())
                .contains("\"password\":\"***\"")
                .contains("138****0000")
                .doesNotContain("plain-secret");
        assertThat(entity.getResult())
                .contains("\"token\":\"***\"")
                .doesNotContain("server-token");
        assertThat(entity.getCreateTime()).isNotNull();
    }

    @Test
    void persistsExceptionLogEntityWithoutMaskingBusinessException() throws Exception {
        RecordingStorageService storageService = new RecordingStorageService();
        AuditedService service = proxy(new AuditedService(), storageService);
        TraceContext.putTraceId("trace-failed");

        assertThatThrownBy(service::fail)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        OperationLogEntity entity = storageService.saved.get(3, TimeUnit.SECONDS);
        assertThat(entity.getLogType()).isEqualTo("EXCEPTION");
        assertThat(entity.getOperationType()).isEqualTo("UPDATE");
        assertThat(entity.getSuccess()).isFalse();
        assertThat(entity.getErrorMessage()).isEqualTo("boom");
        assertThat(entity.getTraceId()).isEqualTo("trace-failed");
    }

    private static AuditedService proxy(AuditedService target, RecordingStorageService storageService) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new OperationLogAspect(storageService));
        return factory.getProxy();
    }

    public static class AuditedService {

        @OperationLog(module = "订单中心", action = "创建订单",
                type = OperationLog.LogType.INSERT, saveResult = true)
        public Map<String, Object> create(Map<String, Object> request) {
            return Map.of(
                    "status", "OK",
                    "token", "server-token",
                    "orderNo", request.get("orderNo")
            );
        }

        @OperationLog(module = "订单中心", action = "更新订单", type = OperationLog.LogType.UPDATE)
        public void fail() {
            throw new IllegalStateException("boom");
        }
    }

    private static class RecordingStorageService extends OperationLogStorageService {

        private final CompletableFuture<OperationLogEntity> saved = new CompletableFuture<>();

        RecordingStorageService() {
            super(new LogProperties());
        }

        @Override
        public void saveAsync(OperationLogEntity entity) {
            saved.complete(entity);
        }
    }
}
