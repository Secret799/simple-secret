package com.ss.idempotent.key;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证请求身份不会隐式信任代理头，并具有稳定回退顺序。 */
class DefaultRequestIdentityResolverTest {

    @Test
    void shouldPreferConfiguredHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Idempotency-Identity", "  user-7  ");
        request.setRemoteAddr("192.0.2.10");

        assertThat(new DefaultRequestIdentityResolver("X-Idempotency-Identity")
                .resolve(request)).isEqualTo("user-7");
    }

    @Test
    void shouldUseExistingSessionThenRemoteAddressWithoutTrustingForwardedFor() {
        MockHttpServletRequest sessionRequest = new MockHttpServletRequest();
        String sessionId = sessionRequest.getSession().getId();

        assertThat(new DefaultRequestIdentityResolver("Authorization")
                .resolve(sessionRequest)).isEqualTo("session:" + sessionId);

        MockHttpServletRequest remoteRequest = new MockHttpServletRequest();
        remoteRequest.setRemoteAddr("192.0.2.20");
        remoteRequest.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(new DefaultRequestIdentityResolver("Authorization")
                .resolve(remoteRequest)).isEqualTo("remote:192.0.2.20");
    }
}
