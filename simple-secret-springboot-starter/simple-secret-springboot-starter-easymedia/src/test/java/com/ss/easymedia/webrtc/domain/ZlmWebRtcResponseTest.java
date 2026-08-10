package com.ss.easymedia.webrtc.domain;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZlmWebRtcResponseTest {

    @Test
    void shouldTreatLegacyConstructorAsManagedSession() {
        ZlmWebRtcResponse response = new ZlmWebRtcResponse(
                URI.create("http://127.0.0.1:7080/index/api/whip"), HttpStatus.CREATED,
                new HttpHeaders(), new byte[0]);

        assertTrue(response.managedSession());
    }

    @Test
    void shouldExposeUnmanagedSessionResponse() {
        ZlmWebRtcResponse response = new ZlmWebRtcResponse(
                URI.create("rtc://__defaultVhost/live/cam-01"), HttpStatus.CREATED,
                new HttpHeaders(), new byte[0], false);

        assertFalse(response.managedSession());
    }
}
