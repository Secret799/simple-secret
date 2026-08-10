package com.ss.easymedia.webrtc.security;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.domain.WebRtcSessionRecord;
import com.ss.easymedia.webrtc.domain.WebRtcSessionState;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultWebRtcAccessPolicyTest {

    private final WebRtcProperties properties = new WebRtcProperties();
    private final DefaultWebRtcAccessPolicy policy = new DefaultWebRtcAccessPolicy(properties);

    @Test
    void shouldAllowAuthenticatedIdentityToCreateSessions() {
        WebRtcIdentity identity = new WebRtcIdentity("tenant-a", "user-1", true);

        assertDoesNotThrow(() -> policy.authorizeCreate(
                identity, WebRtcSessionType.WHIP, "live", "cam-01"));
        assertDoesNotThrow(() -> policy.authorizeCreate(
                identity, WebRtcSessionType.WHEP, "live", "cam-01"));
    }

    @Test
    void shouldRejectAnonymousIdentityWhenAuthenticationIsRequired() {
        WebRtcIdentity identity = new WebRtcIdentity("anonymous", "subject", false);

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> policy.authorizeCreate(identity, WebRtcSessionType.WHEP, "live", "cam-01"));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        assertEquals("WEBRTC_AUTH_REQUIRED", error.getErrorCode());
    }

    @Test
    void shouldAllowAnonymousIdentityWhenAuthenticationIsDisabled() {
        properties.getSecurity().setAuthenticationRequired(false);
        WebRtcIdentity identity = new WebRtcIdentity("anonymous", "subject", false);

        assertDoesNotThrow(() -> policy.authorizeCreate(
                identity, WebRtcSessionType.WHEP, "live", "cam-01"));
    }

    @Test
    void shouldAllowSessionOwner() {
        WebRtcIdentity identity = new WebRtcIdentity("tenant-a", "user-1", true);

        assertDoesNotThrow(() -> policy.authorizeSession(identity, record("tenant-a", "user-1")));
    }

    @Test
    void shouldRejectCrossTenantSessionMutation() {
        WebRtcIdentity caller = new WebRtcIdentity("tenant-b", "user-1", true);

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> policy.authorizeSession(caller, record("tenant-a", "user-1")));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertEquals("WEBRTC_SESSION_FORBIDDEN", error.getErrorCode());
    }

    @Test
    void shouldRejectDifferentSubjectSessionMutation() {
        WebRtcIdentity caller = new WebRtcIdentity("tenant-a", "user-2", true);

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> policy.authorizeSession(caller, record("tenant-a", "user-1")));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    private static WebRtcSessionRecord record(String tenantId, String subject) {
        return new WebRtcSessionRecord(
                "sid", WebRtcSessionType.WHEP, WebRtcSessionState.ACTIVE,
                tenantId, subject, "live", "cam-01", "local-zlm",
                "http://127.0.0.1:7080/index/api/whep/upstream", "\"v1\"",
                1L, 1L, 0L, 0);
    }
}
