package com.ss.easymedia.webrtc.client;

import com.aizuda.zlm4j.callback.IMKWebRtcGetAnwerSdpCallBack;
import com.aizuda.zlm4j.core.ZLMApi;
import com.ss.easymedia.webrtc.domain.WebRtcMediaTypes;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import com.ss.easymedia.webrtc.domain.ZlmWebRtcResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LocalZlmWebRtcSignalingClientTest {

    @Mock
    private ZLMApi zlmApi;

    private LocalZlmWebRtcSignalingClient client;

    @BeforeEach
    void setUp() {
        client = new LocalZlmWebRtcSignalingClient(zlmApi, Duration.ofSeconds(1));
    }

    @Test
    void shouldCreateWhipThroughNativePushSignaling() {
        answerWith("answer-sdp", null);

        ZlmWebRtcResponse response = client.create(WebRtcSessionType.WHIP, "live", "cam-01",
                sdpHeaders(), bytes("offer-sdp"));

        verifyNativeInvocation("push", "offer-sdp", "rtc://__defaultVhost__/live/cam-01");
        assertEquals(HttpStatus.CREATED, response.status());
        assertEquals(WebRtcMediaTypes.APPLICATION_SDP, response.headers().getContentType());
        assertArrayEquals(bytes("answer-sdp"), response.body());
        assertFalse(response.managedSession());
        assertNull(response.headers().getLocation());
    }

    @Test
    void shouldCreateWhepThroughNativePlaySignaling() {
        answerWith("answer-sdp", null);

        client.create(WebRtcSessionType.WHEP, "live", "cam-01", sdpHeaders(), bytes("offer-sdp"));

        verifyNativeInvocation("play", "offer-sdp", "rtc://__defaultVhost__/live/cam-01");
    }

    @Test
    void shouldFailWhenNativeSignalingReturnsError() {
        answerWith(null, "native failure");

        assertThrows(IllegalStateException.class, () -> client.create(
                WebRtcSessionType.WHIP, "live", "cam-01", sdpHeaders(), bytes("offer-sdp")));
    }

    @Test
    void shouldFailWhenNativeSignalingTimesOut() {
        LocalZlmWebRtcSignalingClient shortTimeoutClient =
                new LocalZlmWebRtcSignalingClient(zlmApi, Duration.ofMillis(1));

        assertThrows(IllegalStateException.class, () -> shortTimeoutClient.create(
                WebRtcSessionType.WHIP, "live", "cam-01", sdpHeaders(), bytes("offer-sdp")));
    }

    @Test
    void shouldRejectSessionMutationBecauseNativeSignalingHasNoSessionResource() {
        assertThrows(IllegalStateException.class, () -> client.exchange(
                URI.create("rtc://__defaultVhost__/live/cam-01"), HttpMethod.PATCH,
                sdpHeaders(), bytes("candidate")));
    }

    private void answerWith(String answer, String error) {
        doAnswer(invocation -> {
            IMKWebRtcGetAnwerSdpCallBack callback = invocation.getArgument(1);
            callback.invoke(null, answer, error);
            return null;
        }).when(zlmApi).mk_webrtc_get_answer_sdp(isNull(), any(), anyString(), anyString(), anyString());
    }

    private void verifyNativeInvocation(String type, String offer, String url) {
        ArgumentCaptor<IMKWebRtcGetAnwerSdpCallBack> callback =
                ArgumentCaptor.forClass(IMKWebRtcGetAnwerSdpCallBack.class);
        verify(zlmApi).mk_webrtc_get_answer_sdp(isNull(), callback.capture(),
                org.mockito.ArgumentMatchers.eq(type), org.mockito.ArgumentMatchers.eq(offer),
                org.mockito.ArgumentMatchers.eq(url));
    }

    private static HttpHeaders sdpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(WebRtcMediaTypes.APPLICATION_SDP);
        return headers;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
