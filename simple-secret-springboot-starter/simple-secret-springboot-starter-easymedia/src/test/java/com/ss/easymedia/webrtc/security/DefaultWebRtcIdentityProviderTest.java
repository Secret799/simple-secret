package com.ss.easymedia.webrtc.security;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultWebRtcIdentityProviderTest {

    @Test
    void shouldRejectAnonymousByDefault() {
        WebRtcProperties properties = new WebRtcProperties();
        DefaultWebRtcIdentityProvider provider = new DefaultWebRtcIdentityProvider(properties);

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> provider.current("10.0.0.8"));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
    }

    @Test
    void shouldCreateHashedAnonymousIdentityWhenAuthenticationIsDisabled() {
        WebRtcProperties properties = new WebRtcProperties();
        properties.getSecurity().setAuthenticationRequired(false);
        DefaultWebRtcIdentityProvider provider = new DefaultWebRtcIdentityProvider(properties);

        WebRtcIdentity identity = provider.current("10.0.0.8");

        assertEquals("anonymous", identity.tenantId());
        assertFalse(identity.authenticated());
        assertTrue(identity.subject().startsWith("anonymous:"));
        assertFalse(identity.subject().contains("10.0.0.8"));
    }

    @Test
    void shouldUseConfiguredSubjectAsAuthenticatedIdentity() {
        WebRtcProperties properties = new WebRtcProperties();
        properties.getSecurity().setDefaultSubject("internal-gateway");
        properties.getSecurity().setDefaultTenantId("t1");
        DefaultWebRtcIdentityProvider provider = new DefaultWebRtcIdentityProvider(properties);

        WebRtcIdentity identity = provider.current("10.0.0.8");

        assertEquals("t1", identity.tenantId());
        assertEquals("internal-gateway", identity.subject());
        assertTrue(identity.authenticated());
    }
}
