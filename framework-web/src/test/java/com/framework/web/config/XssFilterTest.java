package com.framework.web.config;

import com.framework.core.constant.FrameworkConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class XssFilterTest {

    @Test
    void escapesParametersButKeepsHeadersUnchanged() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("name", "<script>alert(1)</script>");
        request.addHeader(FrameworkConstants.AUTH_HEADER, "Bearer abc/def==");
        XssFilter.XssRequestWrapper wrapper = new XssFilter.XssRequestWrapper(request);

        assertThat(wrapper.getParameter("name"))
                .isEqualTo("&lt;script&gt;alert(1)&lt;&#x2F;script&gt;");
        assertThat(wrapper.getHeader(FrameworkConstants.AUTH_HEADER))
                .isEqualTo("Bearer abc/def==");
    }
}
