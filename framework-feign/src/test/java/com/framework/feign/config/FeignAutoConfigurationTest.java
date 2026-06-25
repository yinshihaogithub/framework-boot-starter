package com.framework.feign.config;

import com.framework.core.constant.FrameworkConstants;
import com.framework.core.trace.TraceContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class FeignAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class));

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void autoConfigurationRegistersFeignInterceptor() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(FeignProperties.class)
                .hasSingleBean(RequestInterceptor.class));
    }

    @Test
    void interceptorRelaysGeneratedTraceIdFromMdcWhenRequestHeaderIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(FrameworkConstants.AUTH_HEADER, "Bearer token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TraceContext.putTraceId("trace-from-mdc");
        RequestTemplate template = new RequestTemplate();

        new FrameworkFeignRequestInterceptor(new FeignProperties()).apply(template);

        assertThat(template.headers())
                .containsEntry(FrameworkConstants.AUTH_HEADER, java.util.List.of("Bearer token"))
                .containsEntry(FrameworkConstants.TRACE_ID_HEADER, java.util.List.of("trace-from-mdc"));
    }

    @Test
    void interceptorAddsTraceIdFromMdcWhenThereIsNoServletRequest() {
        TraceContext.putTraceId("background-trace");
        RequestTemplate template = new RequestTemplate();

        new FrameworkFeignRequestInterceptor(new FeignProperties()).apply(template);

        assertThat(template.headers())
                .containsEntry(FrameworkConstants.TRACE_ID_HEADER, java.util.List.of("background-trace"));
    }
}
