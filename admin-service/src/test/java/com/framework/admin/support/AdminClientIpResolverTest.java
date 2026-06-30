package com.framework.admin.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AdminClientIpResolverTest {

    @Test
    void resolvesFirstTrustedForwardedIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "\u00A0unknown\u3000, \u300010.0.0.8\u00A0, 10.0.0.9");

        assertThat(AdminClientIpResolver.resolve(request)).isEqualTo("10.0.0.8");
    }

    @Test
    void fallsBackToRemoteAddressWhenForwardedHeaderIsUnsafe() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("\u00A0127.0.0.1\u3000");
        request.addHeader("X-Forwarded-For", "bad\nip, \u00A0unknown\u3000, \u00A0\u3000");

        assertThat(AdminClientIpResolver.resolve(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void returnsNullWhenNoSafeAddressExists() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("bad\rremote");
        request.addHeader("X-Forwarded-For", "unknown");

        assertThat(AdminClientIpResolver.resolve(request)).isNull();
        assertThat(AdminClientIpResolver.resolve(null)).isNull();
    }
}
