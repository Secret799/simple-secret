package com.ss.common.toolbox.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafeUriFormatterTest {

    @Test
    void removesCredentialsQueryAndFragment() {
        assertEquals("rtsp://camera.example:8554/live/main",
                SafeUriFormatter.forLog(
                        "rtsp://user:password@camera.example:8554/live/main?token=secret#track"));
    }

    @Test
    void doesNotEchoInvalidInput() {
        assertEquals("<invalid-uri>", SafeUriFormatter.forLog("secret value with spaces"));
    }

    @Test
    void handlesMissingInput() {
        assertEquals("<null-uri>", SafeUriFormatter.forLog(null));
    }
}
