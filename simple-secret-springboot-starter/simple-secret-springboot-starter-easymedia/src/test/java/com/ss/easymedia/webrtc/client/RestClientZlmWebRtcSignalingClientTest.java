package com.ss.easymedia.webrtc.client;

import com.ss.easymedia.webrtc.domain.WebRtcMediaTypes;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import com.ss.easymedia.webrtc.domain.ZlmWebRtcResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class RestClientZlmWebRtcSignalingClientTest {

    private static final URI BASE_URI = URI.create("http://127.0.0.1:17080");

    private MockRestServiceServer server;
    private RestClientZlmWebRtcSignalingClient client;
    private ZlmWebRtcUriPolicy uriPolicy;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        uriPolicy = new ZlmWebRtcUriPolicy(BASE_URI);
        client = new RestClientZlmWebRtcSignalingClient(builder.build(), BASE_URI, uriPolicy);
    }

    @Test
    void shouldPostWhipAndReturnLocationEtagAndSdp() {
        server.expect(once(), requestTo(
                        "http://127.0.0.1:17080/index/api/whip?app=live&stream=cam-01"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, WebRtcMediaTypes.APPLICATION_SDP_VALUE))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(WebRtcMediaTypes.APPLICATION_SDP)
                        .header(HttpHeaders.LOCATION, "/index/api/whip/session-1")
                        .header(HttpHeaders.ETAG, "\"v1\"")
                        .body("answer-sdp"));

        ZlmWebRtcResponse response = client.create(
                WebRtcSessionType.WHIP, "live", "cam-01", sdpHeaders(), bytes("offer-sdp"));

        assertEquals(HttpStatus.CREATED, response.status());
        assertEquals(URI.create("http://127.0.0.1:17080/index/api/whip?app=live&stream=cam-01"),
                response.requestUri());
        assertEquals("/index/api/whip/session-1",
                response.headers().getFirst(HttpHeaders.LOCATION));
        assertEquals("\"v1\"", response.headers().getFirst(HttpHeaders.ETAG));
        assertArrayEquals(bytes("answer-sdp"), response.body());
        server.verify();
    }

    @Test
    void shouldEncodeQueryParametersAndForwardOnlyAllowedHeaders() {
        HttpHeaders requestHeaders = sdpHeaders();
        requestHeaders.setBearerAuth("external-token");
        requestHeaders.add(HttpHeaders.COOKIE, "SESSION=secret");
        requestHeaders.add(HttpHeaders.HOST, "attacker.example");
        requestHeaders.add("Forwarded", "host=attacker.example");
        requestHeaders.add("X-Forwarded-Host", "attacker.example");
        requestHeaders.setIfMatch("\"v1\"");

        server.expect(request -> {
                    assertEquals(URI.create("http://127.0.0.1:17080/index/api/whep"
                                    + "?app=live%2F%E6%B5%8B%E8%AF%95&stream=cam%20%3F%201"),
                            request.getURI());
                    assertEquals(WebRtcMediaTypes.APPLICATION_SDP_VALUE,
                            request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
                    assertEquals("\"v1\"", request.getHeaders().getFirst(HttpHeaders.IF_MATCH));
                    assertTrue(!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION));
                    assertTrue(!request.getHeaders().containsKey(HttpHeaders.COOKIE));
                    assertTrue(!request.getHeaders().containsKey(HttpHeaders.HOST));
                    assertTrue(!request.getHeaders().containsKey("Forwarded"));
                    assertTrue(!request.getHeaders().containsKey("X-Forwarded-Host"));
                })
                .andRespond(withStatus(HttpStatus.CONFLICT).body("not-ready"));

        ZlmWebRtcResponse response = client.create(
                WebRtcSessionType.WHEP, "live/测试", "cam ? 1", requestHeaders, bytes("offer"));

        assertEquals(HttpStatus.CONFLICT, response.status());
        assertArrayEquals(bytes("not-ready"), response.body());
        server.verify();
    }

    @Test
    void shouldForwardPatchToExactTrustedLocation() {
        URI sessionUri = URI.create("http://127.0.0.1:17080/index/api/whep/session-2");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(WebRtcMediaTypes.TRICKLE_ICE_SDPFRAG);
        headers.setIfMatch("\"v2\"");

        server.expect(requestTo(sessionUri))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"v2\""))
                .andRespond(withStatus(HttpStatus.NO_CONTENT)
                        .header(HttpHeaders.ETAG, "\"v3\""));

        ZlmWebRtcResponse response = client.exchange(
                sessionUri, HttpMethod.PATCH, headers, bytes("ice-fragment"));

        assertEquals(HttpStatus.NO_CONTENT, response.status());
        assertEquals("\"v3\"", response.headers().getFirst(HttpHeaders.ETAG));
        server.verify();
    }

    @Test
    void shouldPassThroughUpstreamServerErrorWithoutThrowing() {
        URI sessionUri = URI.create("http://127.0.0.1:17080/index/api/whep/session-3");
        server.expect(requestTo(sessionUri))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("temporary failure"));

        ZlmWebRtcResponse response = client.exchange(
                sessionUri, HttpMethod.DELETE, new HttpHeaders(), new byte[0]);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status());
        assertArrayEquals(bytes("temporary failure"), response.body());
        server.verify();
    }

    @Test
    void shouldResolveRelativeLocationAndRejectExternalLocation() {
        assertEquals(URI.create("http://127.0.0.1:17080/index/api/whep/session-4"),
                uriPolicy.requireTrustedLocation(
                        URI.create("http://127.0.0.1:17080/index/api/whep?app=live&stream=cam"),
                        URI.create("/index/api/whep/session-4")));

        assertThrows(IllegalArgumentException.class, () -> uriPolicy.requireTrustedLocation(
                BASE_URI, URI.create("https://attacker.example/index/api/whep/session-4")));
        assertThrows(IllegalArgumentException.class, () -> uriPolicy.requireTrustedLocation(
                BASE_URI, URI.create("http://127.0.0.1:17080/other/path")));
        assertThrows(IllegalArgumentException.class, () -> uriPolicy.requireTrustedLocation(
                BASE_URI, URI.create("http://user@127.0.0.1:17080/index/api/whep/session-4")));
    }

    private static HttpHeaders sdpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(WebRtcMediaTypes.APPLICATION_SDP);
        headers.setAccept(java.util.List.of(WebRtcMediaTypes.APPLICATION_SDP));
        return headers;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
