package com.ss.easymedia.webrtc.service;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.client.ZlmWebRtcSignalingClient;
import com.ss.easymedia.webrtc.client.ZlmWebRtcUriPolicy;
import com.ss.easymedia.webrtc.domain.WebRtcGatewayResponse;
import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.domain.WebRtcMediaTypes;
import com.ss.easymedia.webrtc.domain.WebRtcOperation;
import com.ss.easymedia.webrtc.domain.WebRtcSessionRecord;
import com.ss.easymedia.webrtc.domain.WebRtcSessionState;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import com.ss.easymedia.webrtc.domain.ZlmWebRtcResponse;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;
import com.ss.easymedia.webrtc.id.WebRtcSessionIdGenerator;
import com.ss.easymedia.webrtc.metrics.NoopWebRtcSessionMetrics;
import com.ss.easymedia.webrtc.repository.WebRtcSessionRepository;
import com.ss.easymedia.webrtc.security.WebRtcAccessPolicy;
import com.ss.easymedia.webrtc.security.WebRtcIdentityProvider;
import com.ss.easymedia.webrtc.security.WebRtcRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultWebRtcSessionServiceMutationTest {

    private static final String SESSION_ID = "abcdefghijklmnopqrstuvwxyzABCDEF";
    private static final URI UPSTREAM = URI.create(
            "http://127.0.0.1:7080/index/api/whep/upstream-1");
    private static final byte[] FRAGMENT = "ice-fragment".getBytes(StandardCharsets.UTF_8);

    @Mock
    private WebRtcIdentityProvider identityProvider;
    @Mock
    private WebRtcAccessPolicy accessPolicy;
    @Mock
    private WebRtcRateLimiter rateLimiter;
    @Mock
    private ZlmWebRtcSignalingClient client;
    @Mock
    private ZlmWebRtcUriPolicy uriPolicy;
    @Mock
    private WebRtcSessionRepository repository;
    @Mock
    private WebRtcSessionIdGenerator idGenerator;

    private DefaultWebRtcSessionService service;
    private WebRtcIdentity identity;
    private WebRtcProperties properties;
    private AtomicBoolean insideSessionLock;

    @BeforeEach
    void setUp() {
        properties = new WebRtcProperties();
        insideSessionLock = new AtomicBoolean();
        identity = new WebRtcIdentity("tenant-1", "user-1", true);
        service = new DefaultWebRtcSessionService(
                identityProvider, accessPolicy, rateLimiter, client, uriPolicy,
                repository, idGenerator, properties,
                Clock.fixed(Instant.ofEpochMilli(10_000), ZoneOffset.UTC),
                new NoopWebRtcSessionMetrics());
        lenient().when(repository.withSessionLock(eq(SESSION_ID), any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(1);
            insideSessionLock.set(true);
            try {
                return action.get();
            } finally {
                insideSessionLock.set(false);
            }
        });
    }

    @Test
    void shouldPatchActiveSessionAndPersistNewEtag() {
        WebRtcSessionRecord record = activeRecord();
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        when(repository.find(SESSION_ID)).thenReturn(Optional.of(record));
        HttpHeaders upstreamHeaders = new HttpHeaders();
        upstreamHeaders.setETag("\"v2\"");
        when(client.exchange(eq(UPSTREAM), eq(HttpMethod.PATCH), any(), eq(FRAGMENT)))
                .thenReturn(new ZlmWebRtcResponse(UPSTREAM, HttpStatus.NO_CONTENT,
                        upstreamHeaders, new byte[0]));
        when(repository.savePreservingTtl(any())).thenReturn(true);

        WebRtcGatewayResponse response = service.patch(
                SESSION_ID, patchHeaders(), FRAGMENT, "10.0.0.8");

        assertEquals(HttpStatus.NO_CONTENT, response.status());
        assertEquals("\"v2\"", response.headers().getETag());
        verify(accessPolicy).authorizeSession(identity, record);
        verify(rateLimiter).check(WebRtcOperation.PATCH, identity, "10.0.0.8");
        verify(repository).savePreservingTtl(argThat(updated ->
                "\"v2\"".equals(updated.getUpstreamEtag())
                        && updated.getUpdatedAt() == 10_000L));
    }

    @Test
    void shouldRejectPatchForMissingSession() {
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        when(repository.find(SESSION_ID)).thenReturn(Optional.empty());

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> service.patch(SESSION_ID, patchHeaders(), FRAGMENT, "10.0.0.8"));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        verifyNoInteractions(client);
    }

    @Test
    void shouldRejectPatchForClosingSession() {
        WebRtcSessionRecord record = activeRecord();
        record.setState(WebRtcSessionState.CLOSING);
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        when(repository.find(SESSION_ID)).thenReturn(Optional.of(record));

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> service.patch(SESSION_ID, patchHeaders(), FRAGMENT, "10.0.0.8"));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verifyNoInteractions(client);
    }

    @Test
    void shouldRejectMalformedSessionIdBeforeRedis() {
        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> service.patch("../sid", patchHeaders(), FRAGMENT, "10.0.0.8"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verifyNoInteractions(identityProvider, repository, client);
    }

    @Test
    void shouldMapSessionRepositoryFailureToServiceUnavailable() {
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        when(repository.withSessionLock(eq(SESSION_ID), any()))
                .thenThrow(new IllegalStateException("redis down"));

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> service.delete(SESSION_ID, "10.0.0.8"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("WEBRTC_SESSION_STORAGE_UNAVAILABLE", error.getErrorCode());
        verifyNoInteractions(client);
    }

    @Test
    void shouldDeleteActiveSessionAndRemoveMappingOnUpstreamSuccess() {
        WebRtcSessionRecord record = activeRecord();
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        when(repository.find(SESSION_ID)).thenReturn(Optional.of(record));
        when(client.exchange(eq(UPSTREAM), eq(HttpMethod.DELETE), any(), any()))
                .thenReturn(new ZlmWebRtcResponse(UPSTREAM, HttpStatus.NO_CONTENT,
                        new HttpHeaders(), new byte[0]));

        service.delete(SESSION_ID, "10.0.0.8");

        verify(repository).save(argThat(closing ->
                closing.getState() == WebRtcSessionState.CLOSING
                        && closing.getUpdatedAt() == 10_000L),
                eq(Duration.ofMinutes(5)));
        verify(repository).scheduleClosingRetry(eq(SESSION_ID), eq(Instant.ofEpochMilli(10_000L)));
        verify(repository).delete(SESSION_ID);
    }

    @Test
    void shouldDeleteUpstreamWhileHoldingSessionLock() {
        WebRtcSessionRecord record = activeRecord();
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        when(repository.find(SESSION_ID)).thenReturn(Optional.of(record));
        when(client.exchange(eq(UPSTREAM), eq(HttpMethod.DELETE), any(), any()))
                .thenAnswer(invocation -> {
                    assertTrue(insideSessionLock.get());
                    return new ZlmWebRtcResponse(UPSTREAM, HttpStatus.NO_CONTENT,
                            new HttpHeaders(), new byte[0]);
                });

        service.delete(SESSION_ID, "10.0.0.8");
    }

    @Test
    void shouldNotDeleteUpstreamTwiceWhenSessionIsAlreadyClosing() {
        WebRtcSessionRecord record = activeRecord();
        record.setState(WebRtcSessionState.CLOSING);
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        when(repository.find(SESSION_ID)).thenReturn(Optional.of(record));

        service.delete(SESSION_ID, "10.0.0.8");

        verifyNoInteractions(client);
        verify(repository, never()).save(any(), any());
    }

    @Test
    void shouldTreatMissingDeleteAsIdempotent() {
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        when(repository.find(SESSION_ID)).thenReturn(Optional.empty());

        service.delete(SESSION_ID, "10.0.0.8");

        verifyNoInteractions(client);
        verify(repository, never()).save(any(), any());
    }

    @Test
    void shouldScheduleRetryWhenUpstreamDeleteIsTransientFailure() {
        WebRtcSessionRecord record = activeRecord();
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        when(repository.find(SESSION_ID)).thenReturn(Optional.of(record));
        when(client.exchange(eq(UPSTREAM), eq(HttpMethod.DELETE), any(), any()))
                .thenReturn(new ZlmWebRtcResponse(UPSTREAM, HttpStatus.INTERNAL_SERVER_ERROR,
                        new HttpHeaders(), new byte[0]));

        service.delete(SESSION_ID, "10.0.0.8");

        verify(repository, atLeastOnce()).save(argThat(closing ->
                        closing.getState() == WebRtcSessionState.CLOSING
                                && closing.getDeleteRetryCount() == 1
                                && closing.getNextDeleteRetryAt() == 12_000L),
                eq(Duration.ofMinutes(5)));
        verify(repository).scheduleClosingRetry(SESSION_ID, Instant.ofEpochMilli(12_000L));
        verify(repository, never()).delete(SESSION_ID);
    }

    @Test
    void shouldStopRetryingPermanentUpstreamClientError() {
        WebRtcSessionRecord record = activeRecord();
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        when(repository.find(SESSION_ID)).thenReturn(Optional.of(record));
        when(client.exchange(eq(UPSTREAM), eq(HttpMethod.DELETE), any(), any()))
                .thenReturn(new ZlmWebRtcResponse(UPSTREAM, HttpStatus.BAD_REQUEST,
                        new HttpHeaders(), new byte[0]));

        service.delete(SESSION_ID, "10.0.0.8");

        verify(repository).removeClosingRetry(SESSION_ID);
        verify(repository, never()).delete(SESSION_ID);
    }

    @Test
    void shouldRetryClosingSessionWithoutUserContext() {
        WebRtcSessionRecord record = activeRecord();
        record.setState(WebRtcSessionState.CLOSING);
        when(repository.find(SESSION_ID)).thenReturn(Optional.of(record));
        when(client.exchange(eq(UPSTREAM), eq(HttpMethod.DELETE), any(), any()))
                .thenReturn(new ZlmWebRtcResponse(UPSTREAM, HttpStatus.NOT_FOUND,
                        new HttpHeaders(), new byte[0]));

        service.retryDelete(SESSION_ID);

        verify(repository).delete(SESSION_ID);
        verifyNoInteractions(identityProvider, accessPolicy, rateLimiter);
    }

    @Test
    void shouldSkipClosingRetryThatIsNoLongerDue() {
        WebRtcSessionRecord record = activeRecord();
        record.setState(WebRtcSessionState.CLOSING);
        record.setNextDeleteRetryAt(20_000L);
        when(repository.find(SESSION_ID)).thenReturn(Optional.of(record));

        service.retryDelete(SESSION_ID);

        verifyNoInteractions(client);
        verify(repository, never()).scheduleClosingRetry(any(), any());
        verify(repository, never()).delete(any());
    }

    private static HttpHeaders patchHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(WebRtcMediaTypes.TRICKLE_ICE_SDPFRAG);
        headers.setIfMatch("\"v1\"");
        return headers;
    }

    private static WebRtcSessionRecord activeRecord() {
        return new WebRtcSessionRecord(
                SESSION_ID, WebRtcSessionType.WHEP, WebRtcSessionState.ACTIVE,
                "tenant-1", "user-1", "live", "cam-01", "local-zlm",
                UPSTREAM.toString(), "\"v1\"", 1_000L, 1_000L, 0L, 0);
    }
}
