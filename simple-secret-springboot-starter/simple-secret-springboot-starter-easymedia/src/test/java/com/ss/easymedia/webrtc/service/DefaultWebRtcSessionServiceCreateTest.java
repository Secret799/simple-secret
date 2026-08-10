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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultWebRtcSessionServiceCreateTest {

    private static final byte[] OFFER = "offer-sdp".getBytes(StandardCharsets.UTF_8);
    private static final URI CREATE_URI = URI.create(
            "http://127.0.0.1:7080/index/api/whep?app=live&stream=cam-01");
    private static final URI UPSTREAM_LOCATION = URI.create(
            "http://127.0.0.1:7080/index/api/whep/upstream-1");

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

    private WebRtcProperties properties;
    private DefaultWebRtcSessionService service;
    private WebRtcIdentity identity;

    @BeforeEach
    void setUp() {
        properties = new WebRtcProperties();
        identity = new WebRtcIdentity("tenant-1", "user-1", true);
        service = new DefaultWebRtcSessionService(
                identityProvider, accessPolicy, rateLimiter, client, uriPolicy,
                repository, idGenerator, properties,
                Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC),
                new NoopWebRtcSessionMetrics());
    }

    @Test
    void shouldCreateWhepSessionAndRewriteLocation() {
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        when(client.create(eq(WebRtcSessionType.WHEP), eq("live"), eq("cam-01"),
                any(HttpHeaders.class), eq(OFFER))).thenReturn(createdResponse());
        when(uriPolicy.requireTrustedLocation(CREATE_URI, URI.create("/index/api/whep/upstream-1")))
                .thenReturn(UPSTREAM_LOCATION);
        when(idGenerator.generate()).thenReturn("abcdefghijklmnopqrstuvwxyzABCDEF");
        when(repository.create(any(), eq(Duration.ofHours(1)))).thenReturn(true);

        WebRtcGatewayResponse response = service.create(
                WebRtcSessionType.WHEP, "live", "cam-01", sdpHeaders(), OFFER, "10.0.0.8");

        assertEquals(HttpStatus.CREATED, response.status());
        assertEquals("/easyMedia/api/webrtc/sessions/abcdefghijklmnopqrstuvwxyzABCDEF",
                response.headers().getFirst(HttpHeaders.LOCATION));
        assertEquals("\"v1\"", response.headers().getFirst(HttpHeaders.ETAG));
        assertArrayEquals("answer-sdp".getBytes(StandardCharsets.UTF_8), response.body());
        verify(repository).create(argThat(record ->
                        record.getSessionType() == WebRtcSessionType.WHEP
                                && record.getState() == WebRtcSessionState.ACTIVE
                                && record.getUpstreamLocation().equals(UPSTREAM_LOCATION.toString())
                                && record.getTenantId().equals("tenant-1")
                                && record.getSubject().equals("user-1")
                                && record.getCreatedAt() == 1000L),
                eq(Duration.ofHours(1)));
        InOrder order = inOrder(identityProvider, accessPolicy, rateLimiter, client, repository);
        order.verify(identityProvider).current("10.0.0.8");
        order.verify(accessPolicy).authorizeCreate(identity, WebRtcSessionType.WHEP, "live", "cam-01");
        order.verify(rateLimiter).check(WebRtcOperation.PLAY, identity, "10.0.0.8");
        order.verify(client).create(eq(WebRtcSessionType.WHEP), eq("live"), eq("cam-01"),
                any(HttpHeaders.class), eq(OFFER));
        order.verify(repository).create(any(), eq(Duration.ofHours(1)));
    }

    @Test
    void shouldReturnUpstreamErrorWithoutCreatingSession() {
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setLocation(URI.create("http://internal.example/session"));
        when(client.create(any(), anyString(), anyString(), any(), any()))
                .thenReturn(new ZlmWebRtcResponse(CREATE_URI, HttpStatus.CONFLICT,
                        headers, "not-ready".getBytes(StandardCharsets.UTF_8)));

        WebRtcGatewayResponse response = service.create(
                WebRtcSessionType.WHEP, "live", "cam-01", sdpHeaders(), OFFER, "10.0.0.8");

        assertEquals(HttpStatus.CONFLICT, response.status());
        assertEquals(null, response.headers().getLocation());
        assertArrayEquals("not-ready".getBytes(StandardCharsets.UTF_8), response.body());
        verifyNoInteractions(repository, idGenerator, uriPolicy);
    }

    @Test
    void shouldReturnUnmanagedLocalAnswerWithoutLocationOrPersistingSession() {
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(WebRtcMediaTypes.APPLICATION_SDP);
        when(client.create(any(), anyString(), anyString(), any(), any()))
                .thenReturn(new ZlmWebRtcResponse(
                        URI.create("rtc://__defaultVhost/live/cam-01"), HttpStatus.CREATED,
                        headers, "answer-sdp".getBytes(StandardCharsets.UTF_8), false));
        WebRtcGatewayResponse response = service.create(
                WebRtcSessionType.WHIP, "live", "cam-01", sdpHeaders(), OFFER, "10.0.0.8");

        assertEquals(HttpStatus.CREATED, response.status());
        assertEquals(null, response.headers().getLocation());
        assertArrayEquals("answer-sdp".getBytes(StandardCharsets.UTF_8), response.body());
        verifyNoInteractions(repository, uriPolicy, idGenerator);
    }

    @Test
    void shouldMapUpstreamConnectionFailureToBadGateway() {
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        when(client.create(any(), anyString(), anyString(), any(), any()))
                .thenThrow(new IllegalStateException("connection refused"));

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> service.create(WebRtcSessionType.WHEP, "live", "cam-01",
                        sdpHeaders(), OFFER, "10.0.0.8"));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
        assertEquals("WEBRTC_UPSTREAM_CREATE_FAILED", error.getErrorCode());
        verifyNoInteractions(repository);
    }

    @Test
    void shouldFailClosedWhenRateLimiterStorageIsUnavailable() {
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                .when(rateLimiter).check(WebRtcOperation.PLAY, identity, "10.0.0.8");

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> service.create(WebRtcSessionType.WHEP, "live", "cam-01",
                        sdpHeaders(), OFFER, "10.0.0.8"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("WEBRTC_RATE_LIMIT_UNAVAILABLE", error.getErrorCode());
        verifyNoInteractions(client, repository);
    }

    @Test
    void shouldRejectOversizedSdpBeforeAuthenticationOrUpstreamAccess() {
        properties.setMaxSdpBytes(4);

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> service.create(WebRtcSessionType.WHIP, "live", "cam-01",
                        sdpHeaders(), OFFER, "10.0.0.8"));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatus());
        verifyNoInteractions(identityProvider, accessPolicy, rateLimiter, client, repository);
    }

    @Test
    void shouldRejectInvalidStreamNameBeforeAuthentication() {
        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> service.create(WebRtcSessionType.WHEP, "live", "../cam",
                        sdpHeaders(), OFFER, "10.0.0.8"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("WEBRTC_STREAM_INVALID", error.getErrorCode());
        verifyNoInteractions(identityProvider, client, repository);
    }

    @Test
    void shouldRejectCreatedResponseWithoutSdpContentType() {
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setLocation(URI.create("/index/api/whep/upstream-1"));
        when(client.create(any(), anyString(), anyString(), any(), any()))
                .thenReturn(new ZlmWebRtcResponse(CREATE_URI, HttpStatus.CREATED,
                        headers, "{}".getBytes(StandardCharsets.UTF_8)));
        when(uriPolicy.requireTrustedLocation(CREATE_URI, URI.create("/index/api/whep/upstream-1")))
                .thenReturn(UPSTREAM_LOCATION);

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> service.create(WebRtcSessionType.WHEP, "live", "cam-01",
                        sdpHeaders(), OFFER, "10.0.0.8"));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
        verify(client).exchange(eq(UPSTREAM_LOCATION), eq(HttpMethod.DELETE),
                any(HttpHeaders.class), argThat(bytes -> bytes.length == 0));
        verifyNoInteractions(repository);
    }

    @Test
    void shouldCleanupUpstreamWhenRedisWriteFails() {
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        when(client.create(any(), anyString(), anyString(), any(), any())).thenReturn(createdResponse());
        when(uriPolicy.requireTrustedLocation(any(), any())).thenReturn(UPSTREAM_LOCATION);
        when(idGenerator.generate()).thenReturn("abcdefghijklmnopqrstuvwxyzABCDEF");
        when(repository.create(any(), any())).thenThrow(new IllegalStateException("redis down"));

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> service.create(WebRtcSessionType.WHEP, "live", "cam-01",
                        sdpHeaders(), OFFER, "10.0.0.8"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("WEBRTC_SESSION_STORAGE_UNAVAILABLE", error.getErrorCode());
        verify(client).exchange(eq(UPSTREAM_LOCATION), eq(HttpMethod.DELETE),
                any(HttpHeaders.class), argThat(bytes -> bytes.length == 0));
    }

    @Test
    void shouldCleanupUpstreamAfterThreeSessionIdCollisions() {
        when(identityProvider.current("10.0.0.8")).thenReturn(identity);
        when(client.create(any(), anyString(), anyString(), any(), any())).thenReturn(createdResponse());
        when(uriPolicy.requireTrustedLocation(any(), any())).thenReturn(UPSTREAM_LOCATION);
        when(idGenerator.generate()).thenReturn(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "cccccccccccccccccccccccccccccccc");
        when(repository.create(any(), any())).thenReturn(false);

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> service.create(WebRtcSessionType.WHEP, "live", "cam-01",
                        sdpHeaders(), OFFER, "10.0.0.8"));

        assertEquals("WEBRTC_SESSION_ID_COLLISION", error.getErrorCode());
        verify(repository, times(3)).create(any(), eq(Duration.ofHours(1)));
        verify(client).exchange(eq(UPSTREAM_LOCATION), eq(HttpMethod.DELETE),
                any(HttpHeaders.class), argThat(bytes -> bytes.length == 0));
    }

    private static ZlmWebRtcResponse createdResponse() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(WebRtcMediaTypes.APPLICATION_SDP);
        headers.setLocation(URI.create("/index/api/whep/upstream-1"));
        headers.setETag("\"v1\"");
        return new ZlmWebRtcResponse(CREATE_URI, HttpStatus.CREATED,
                headers, "answer-sdp".getBytes(StandardCharsets.UTF_8));
    }

    private static HttpHeaders sdpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(WebRtcMediaTypes.APPLICATION_SDP);
        return headers;
    }
}
