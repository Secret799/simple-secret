package com.ss.easymedia.controller;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.domain.WebRtcGatewayResponse;
import com.ss.easymedia.webrtc.domain.WebRtcMediaTypes;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;
import com.ss.easymedia.webrtc.service.WebRtcSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class Zlm4jWebRTCControllerTest {

    private static final String SESSION_ID = "abcdefghijklmnopqrstuvwxyzABCDEF";

    @Mock
    private WebRtcSessionService service;

    private WebRtcProperties properties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        properties = new WebRtcProperties();
        rebuildMockMvc();
    }

    @Test
    void shouldExposeManagedWhepSession() throws Exception {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(WebRtcMediaTypes.APPLICATION_SDP);
        responseHeaders.setLocation(URI.create("/easyMedia/api/webrtc/sessions/" + SESSION_ID));
        responseHeaders.setETag("\"v1\"");
        when(service.create(eq(WebRtcSessionType.WHEP), eq("live"), eq("cam-01"),
                any(HttpHeaders.class), any(byte[].class), eq("10.0.0.8")))
                .thenReturn(new WebRtcGatewayResponse(
                        HttpStatus.CREATED, responseHeaders, bytes("answer")));

        mockMvc.perform(post("/easyMedia/api/webrtc/whep")
                        .param("app", "live")
                        .param("stream", "cam-01")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.8");
                            return request;
                        })
                        .contentType(WebRtcMediaTypes.APPLICATION_SDP)
                        .content("offer"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "/easyMedia/api/webrtc/sessions/" + SESSION_ID))
                .andExpect(header().string(HttpHeaders.ETAG, "\"v1\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().contentTypeCompatibleWith(WebRtcMediaTypes.APPLICATION_SDP))
                .andExpect(content().bytes(bytes("answer")));
    }

    @Test
    void shouldDelegateWhipWithRawSdpAndClientIp() throws Exception {
        when(service.create(eq(WebRtcSessionType.WHIP), eq("live"), eq("cam-01"),
                any(HttpHeaders.class), any(byte[].class), eq("10.0.0.9")))
                .thenReturn(new WebRtcGatewayResponse(
                        HttpStatus.CREATED, new HttpHeaders(), bytes("answer")));

        mockMvc.perform(post("/easyMedia/api/webrtc/whip")
                        .param("app", "live")
                        .param("stream", "cam-01")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.9");
                            return request;
                        })
                        .contentType(WebRtcMediaTypes.APPLICATION_SDP)
                        .content(bytes("offer")))
                .andExpect(status().isCreated());

        verify(service).create(eq(WebRtcSessionType.WHIP), eq("live"), eq("cam-01"),
                any(HttpHeaders.class), eq(bytes("offer")), eq("10.0.0.9"));
    }

    @Test
    void shouldRejectPatchWhenUpstreamTrickleIceIsDisabled() throws Exception {
        mockMvc.perform(patch("/easyMedia/api/webrtc/sessions/{sessionId}", SESSION_ID)
                        .contentType(WebRtcMediaTypes.TRICKLE_ICE_SDPFRAG)
                        .content("ice-fragment"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("WEBRTC_TRICKLE_ICE_UNSUPPORTED"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldPatchManagedSessionWhenCompatibleUpstreamIsEnabled() throws Exception {
        properties.setTrickleIceEnabled(true);
        rebuildMockMvc();
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setETag("\"v2\"");
        when(service.patch(eq(SESSION_ID), any(HttpHeaders.class),
                any(byte[].class), eq("10.0.0.8")))
                .thenReturn(new WebRtcGatewayResponse(
                        HttpStatus.NO_CONTENT, responseHeaders, new byte[0]));

        mockMvc.perform(patch("/easyMedia/api/webrtc/sessions/{sessionId}", SESSION_ID)
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.8");
                            return request;
                        })
                        .contentType(WebRtcMediaTypes.TRICKLE_ICE_SDPFRAG)
                        .header(HttpHeaders.IF_MATCH, "\"v1\"")
                        .content("ice-fragment"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.ETAG, "\"v2\""));
    }

    @Test
    void shouldDeleteManagedSessionIdempotently() throws Exception {
        mockMvc.perform(delete("/easyMedia/api/webrtc/sessions/{sessionId}", SESSION_ID)
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.8");
                            return request;
                        }))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(""));

        verify(service).delete(SESSION_ID, "10.0.0.8");
    }

    @Test
    void shouldRejectPatchAndDeleteWhenUsingLocalZlmSignaling() throws Exception {
        properties.setLocalZlmEnabled(true);
        properties.setTrickleIceEnabled(true);
        rebuildMockMvc();

        mockMvc.perform(patch("/easyMedia/api/webrtc/sessions/{sessionId}", SESSION_ID)
                        .contentType(WebRtcMediaTypes.TRICKLE_ICE_SDPFRAG)
                        .content("ice-fragment"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(
                        "WEBRTC_LOCAL_ZLM_SESSION_OPERATION_UNSUPPORTED"));

        mockMvc.perform(delete("/easyMedia/api/webrtc/sessions/{sessionId}", SESSION_ID))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(
                        "WEBRTC_LOCAL_ZLM_SESSION_OPERATION_UNSUPPORTED"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnBadRequestForMissingStreamParameter() throws Exception {
        mockMvc.perform(post("/easyMedia/api/webrtc/whep")
                        .param("app", "live")
                        .contentType(WebRtcMediaTypes.APPLICATION_SDP)
                        .content("offer"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("WEBRTC_REQUEST_PARAMETER_MISSING"));
    }

    @Test
    void shouldReturnUnsupportedMediaTypeForWrongContentType() throws Exception {
        mockMvc.perform(post("/easyMedia/api/webrtc/whep")
                        .param("app", "live")
                        .param("stream", "cam-01")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("WEBRTC_CONTENT_TYPE_UNSUPPORTED"));
    }

    @Test
    void shouldRejectOversizedSdpBeforeCallingSessionService() throws Exception {
        properties.setMaxSdpBytes(4);
        rebuildMockMvc();

        mockMvc.perform(post("/easyMedia/api/webrtc/whep")
                        .param("app", "live")
                        .param("stream", "cam-01")
                        .contentType(WebRtcMediaTypes.APPLICATION_SDP)
                        .content("offer"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("WEBRTC_SDP_TOO_LARGE"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnPayloadTooLargeFromSessionException() throws Exception {
        when(service.create(any(), anyString(), anyString(), any(), any(), anyString()))
                .thenThrow(new WebRtcSessionException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "WEBRTC_SDP_TOO_LARGE", "SDP body exceeds configured limit"));

        mockMvc.perform(post("/easyMedia/api/webrtc/whep")
                        .param("app", "live")
                        .param("stream", "cam-01")
                        .contentType(WebRtcMediaTypes.APPLICATION_SDP)
                        .content("offer"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("WEBRTC_SDP_TOO_LARGE"));
    }

    @Test
    void shouldReturnUnauthorizedFromSessionException() throws Exception {
        when(service.create(any(), anyString(), anyString(), any(), any(), anyString()))
                .thenThrow(WebRtcSessionException.unauthorized("WEBRTC_AUTH_REQUIRED"));

        mockMvc.perform(post("/easyMedia/api/webrtc/whep")
                        .param("app", "live")
                        .param("stream", "cam-01")
                        .contentType(WebRtcMediaTypes.APPLICATION_SDP)
                        .content("offer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBRTC_AUTH_REQUIRED"));
    }

    @Test
    void shouldRetireLegacyUnmanagedEndpoints() throws Exception {
        mockMvc.perform(post("/easyMedia/api/webrtc/sdp")
                        .contentType(WebRtcMediaTypes.APPLICATION_SDP)
                        .content("offer"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("WEBRTC_LEGACY_SESSION_API_REMOVED"));

        mockMvc.perform(delete("/easyMedia/api/webrtc/deleteWebrtc"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("WEBRTC_LEGACY_SESSION_API_REMOVED"));
    }

    private void rebuildMockMvc() {
        mockMvc = standaloneSetup(new Zlm4jWebRTCController(service, properties))
                .setControllerAdvice(new WebRtcSessionExceptionHandler())
                .build();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
