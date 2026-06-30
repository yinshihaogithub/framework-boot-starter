package com.framework.log.config;

import com.framework.log.entity.OperationLogEntity;
import com.framework.log.service.OperationLogStorageService;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ApiLogFilterTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void recordsApiLogWhenSamplingIsEnabled() throws Exception {
        CapturingStorageService storageService = new CapturingStorageService(true);
        ApiLogFilter filter = filter(storageService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setQueryString("username=admin&password=secret");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            chainCalls.incrementAndGet();
            ((HttpServletResponse) servletResponse).setStatus(201);
        });

        assertThat(chainCalls).hasValue(1);
        assertThat(storageService.shouldLogApiCalls).hasValue(1);
        assertThat(storageService.saveAsyncCalls).hasValue(1);
        assertThat(storageService.savedEntity.getLogType()).isEqualTo("API");
        assertThat(storageService.savedEntity.getAction()).isEqualTo("GET /api/users");
        assertThat(storageService.savedEntity.getClientIp()).isEqualTo("10.0.0.1");
        assertThat(storageService.savedEntity.getParams()).contains("username=admin").doesNotContain("secret");
        assertThat(storageService.savedEntity.getTraceId()).isNotBlank();
        assertThat(storageService.savedEntity.getSuccess()).isTrue();
    }

    @Test
    void skipsApiLogWhenSamplingCheckFailsWithoutBreakingRequest() {
        CapturingStorageService storageService = new CapturingStorageService(true);
        storageService.throwOnShouldLogApi = true;
        ApiLogFilter filter = filter(storageService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        assertThatCode(() -> filter.doFilter(request, response, countingChain(chainCalls)))
                .doesNotThrowAnyException();

        assertThat(chainCalls).hasValue(1);
        assertThat(storageService.shouldLogApiCalls).hasValue(1);
        assertThat(storageService.saveAsyncCalls).hasValue(0);
    }

    @Test
    void skipsApiLogWhenStorageServiceIsMissingWithoutBreakingRequest() {
        ApiLogFilter filter = filter(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        assertThatCode(() -> filter.doFilter(request, response, countingChain(chainCalls)))
                .doesNotThrowAnyException();

        assertThat(chainCalls).hasValue(1);
    }

    private static jakarta.servlet.FilterChain countingChain(AtomicInteger chainCalls) {
        return (ServletRequest servletRequest, ServletResponse servletResponse) -> chainCalls.incrementAndGet();
    }

    private static ApiLogFilter filter(OperationLogStorageService storageService) {
        ApiLogFilter filter = new ApiLogFilter();
        try {
            Field field = ApiLogFilter.class.getDeclaredField("storageService");
            field.setAccessible(true);
            field.set(filter, storageService);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return filter;
    }

    private static class CapturingStorageService extends OperationLogStorageService {

        private final AtomicInteger shouldLogApiCalls = new AtomicInteger();
        private final AtomicInteger saveAsyncCalls = new AtomicInteger();
        private final boolean shouldLogApi;
        private boolean throwOnShouldLogApi;
        private OperationLogEntity savedEntity;

        private CapturingStorageService(boolean shouldLogApi) {
            super(new LogProperties());
            this.shouldLogApi = shouldLogApi;
        }

        @Override
        public boolean shouldLogApi() {
            shouldLogApiCalls.incrementAndGet();
            if (throwOnShouldLogApi) {
                throw new IllegalStateException("sample unavailable");
            }
            return shouldLogApi;
        }

        @Override
        public void saveAsync(OperationLogEntity entity) {
            saveAsyncCalls.incrementAndGet();
            savedEntity = entity;
        }
    }
}
