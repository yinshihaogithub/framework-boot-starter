package com.framework.web.config;

import com.framework.core.constant.FrameworkConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void corsResponseExposesTraceIdHeaderToBrowser() throws Exception {
        CorsFilter filter = new CorsConfig().corsFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/auth/me");
        request.addHeader("Origin", "http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(response.getHeader("Access-Control-Expose-Headers"))
                .contains(FrameworkConstants.TRACE_ID_HEADER);
    }
}
