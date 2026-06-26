package com.framework.web.config;

import com.framework.core.constant.FrameworkConstants;
import com.framework.core.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void restoresPreviousTraceContextAfterRequest() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(FrameworkConstants.TRACE_ID_HEADER, "request-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();
        TraceContext.putTraceId("caller-trace");

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(TraceContext.getTraceId()).isEqualTo("request-trace"));

        assertThat(response.getHeader(FrameworkConstants.TRACE_ID_HEADER)).isEqualTo("request-trace");
        assertThat(TraceContext.getTraceId()).isEqualTo("caller-trace");
    }
}
