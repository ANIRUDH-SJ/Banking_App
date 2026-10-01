package com.netbanking.loginaudit.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class LoginAttemptContextTest {

    @Test
    void usesTheConnectionAddressInsteadOfSpoofableForwardingHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.8");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        request.addHeader("User-Agent", "test-agent");

        LoginAttemptContext context = LoginAttemptContext.from(request);

        assertThat(context.clientIpAddress()).isEqualTo("203.0.113.8");
        assertThat(context.userAgent()).isEqualTo("test-agent");
    }
}
