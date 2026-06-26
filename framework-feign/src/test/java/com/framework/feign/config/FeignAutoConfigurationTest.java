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

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                .containsEntry(FrameworkConstants.AUTH_HEADER, List.of("Bearer token"))
                .containsEntry(FrameworkConstants.TRACE_ID_HEADER, List.of("trace-from-mdc"));
    }

    @Test
    void interceptorSanitizesTraceHeaderBeforeRelaying() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(FrameworkConstants.TRACE_ID_HEADER, "bad\ntrace");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TraceContext.putTraceId("safe-mdc-trace");
        RequestTemplate template = new RequestTemplate();

        new FrameworkFeignRequestInterceptor(new FeignProperties()).apply(template);

        assertThat(template.headers())
                .containsEntry(FrameworkConstants.TRACE_ID_HEADER, List.of("safe-mdc-trace"));
    }

    @Test
    void interceptorRelaysGatewayUserHeadersByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(FrameworkConstants.USER_ID_HEADER, "1001");
        request.addHeader(FrameworkConstants.USER_NAME_HEADER, "alice");
        request.addHeader(FrameworkConstants.USER_ROLES_HEADER, "admin");
        request.addHeader(FrameworkConstants.USER_PERMISSIONS_HEADER, "order:create");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();

        new FrameworkFeignRequestInterceptor(new FeignProperties()).apply(template);

        assertThat(template.headers())
                .containsEntry(FrameworkConstants.USER_ID_HEADER, List.of("1001"))
                .containsEntry(FrameworkConstants.USER_NAME_HEADER, List.of("alice"))
                .containsEntry(FrameworkConstants.USER_ROLES_HEADER, List.of("admin"))
                .containsEntry(FrameworkConstants.USER_PERMISSIONS_HEADER, List.of("order:create"));
    }

    @Test
    void interceptorAddsTraceIdFromMdcWhenThereIsNoServletRequest() {
        TraceContext.putTraceId("background-trace");
        RequestTemplate template = new RequestTemplate();

        new FrameworkFeignRequestInterceptor(new FeignProperties()).apply(template);

        assertThat(template.headers())
                .containsEntry(FrameworkConstants.TRACE_ID_HEADER, List.of("background-trace"));
    }

    @Test
    void interceptorFallsBackToTraceHeaderWhenRelayHeadersAreNull() {
        FeignProperties properties = new FeignProperties();
        properties.setRelayHeaders(null);
        TraceContext.putTraceId("fallback-trace");
        RequestTemplate template = new RequestTemplate();

        new FrameworkFeignRequestInterceptor(properties).apply(template);

        assertThat(template.headers())
                .containsEntry(FrameworkConstants.TRACE_ID_HEADER, List.of("fallback-trace"));
    }

    @Test
    void interceptorFallsBackToTraceHeaderWhenRelayHeadersAreBlank() {
        FeignProperties properties = new FeignProperties();
        properties.setRelayHeaders(List.of(" "));
        TraceContext.putTraceId("blank-config-trace");
        RequestTemplate template = new RequestTemplate();

        new FrameworkFeignRequestInterceptor(properties).apply(template);

        assertThat(template.headers())
                .containsEntry(FrameworkConstants.TRACE_ID_HEADER, List.of("blank-config-trace"));
    }

    @Test
    void propertiesNormalizeRelayHeaderNamesAtStartup() {
        FeignProperties properties = new FeignProperties();
        properties.setRelayHeaders(Arrays.asList(" X-Custom-Trace ", " ", null));

        properties.afterPropertiesSet();

        assertThat(properties.getRelayHeaders()).containsExactly("X-Custom-Trace");
    }

    @Test
    void interceptorRejectsInvalidRelayHeaderName() {
        FeignProperties properties = new FeignProperties();
        properties.setRelayHeaders(List.of("X-Trace-Id", "X Bad\nHeader"));

        assertThatThrownBy(() -> new FrameworkFeignRequestInterceptor(properties).apply(new RequestTemplate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("framework.feign.relay-headers contains invalid header name: X Bad\\nHeader");
    }

    @Test
    void propertiesRejectInvalidRelayHeaderNameAtStartup() {
        FeignProperties properties = new FeignProperties();
        properties.setRelayHeaders(List.of("X-Trace-Id", "X Bad\nHeader"));

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("framework.feign.relay-headers contains invalid header name: X Bad\\nHeader");
    }
}
