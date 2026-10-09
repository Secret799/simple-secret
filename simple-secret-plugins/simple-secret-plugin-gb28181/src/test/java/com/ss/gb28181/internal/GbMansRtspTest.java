package com.ss.gb28181.internal;

import com.ss.gb28181.GbPlaybackControl;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class GbMansRtspTest {
    @Test
    void encodesAnnexBCommandsWithIndependentCseqAndExactFraming() {
        assertEquals("PAUSE RTSP/1.0\r\nCSeq: 1\r\nPauseTime: now\r\n\r\n", request(GbPlaybackControl.pause(), 1));
        assertEquals("PLAY RTSP/1.0\r\nCSeq: 2\r\nRange: npt=now-\r\n\r\n", request(GbPlaybackControl.resume(), 2));
        assertEquals("PLAY RTSP/1.0\r\nCSeq: 3\r\nRange: npt=100-\r\n\r\n", request(GbPlaybackControl.seek(Duration.ofSeconds(100)), 3));
        assertEquals("PLAY RTSP/1.0\r\nCSeq: 4\r\nRange: npt=0-\r\n\r\n", request(GbPlaybackControl.seek(Duration.ZERO), 4));
        for (double scale : new double[]{0.25, 0.5, 1, 2, 4}) {
            String message = request(GbPlaybackControl.speed(scale), 5);
            assertEquals("PLAY RTSP/1.0\r\nCSeq: 5\r\nScale: " + scale + "\r\n\r\n", message);
            assertFalse(message.contains("Range:"));
        }
    }

    @Test
    void requiresPositiveSequenceAndNonNullControl() {
        assertThrows(NullPointerException.class, () -> GbMansRtsp.request(null, 1));
        assertThrows(IllegalArgumentException.class, () -> GbMansRtsp.request(GbPlaybackControl.pause(), 0));
        assertThrows(IllegalArgumentException.class, () -> GbMansRtsp.request(GbPlaybackControl.pause(), -1));
        assertTrue(request(GbPlaybackControl.pause(), Long.MAX_VALUE).contains("CSeq: 9223372036854775807\r\n"));
    }

    @Test
    void acceptsCorrelatedSuccessWithOptionalPlaybackHeadersAndBody() {
        assertDoesNotThrow(() -> validate("RTSP/1.0 200 OK\r\nCSeq: 42\r\n\r\n"));
        assertDoesNotThrow(() -> validate("RTSP/1.0 299 Accepted\r\ncseq: 42\r\nRange: npt=100-\r\n"
                + "RTP-Info: seq=18139;rtptime=3119600838\r\nScale: 2.0\r\nContent-Length: 0\r\n\r\n"));
        assertDoesNotThrow(() -> validate("RTSP/1.0 200 OK\r\nCSeq: 42\r\nContent-Length: 3\r\n\r\nabc"));
        assertDoesNotThrow(() -> GbMansRtsp.validateResponse(bytes("RTSP/1.0 200 OK\r\nCSeq: 9223372036854775807\r\n\r\n"), Long.MAX_VALUE, 8192));
    }

    @Test
    void rejectsMissingDuplicateMismatchedAndInvalidCseq() {
        for (String value : new String[]{"", "0", "-1", "+42", "4.2", "43", "9223372036854775808", "42 INFO", "42\r\nCSeq: 42", "42\r\ncseq: 42"}) {
            assertThrows(IllegalArgumentException.class, () -> validate("RTSP/1.0 200 OK\r\nCSeq: " + value + "\r\n\r\n"));
        }
        assertThrows(IllegalArgumentException.class, () -> validate("RTSP/1.0 200 OK\r\nScale: 2.0\r\n\r\n"));
        assertThrows(IllegalArgumentException.class, () -> GbMansRtsp.validateResponse(bytes("RTSP/1.0 200 OK\r\nCSeq: 0\r\n\r\n"), 0, 8192));
    }

    @Test
    void rejectsNonSuccessMalformedFramingAndHeaderSmuggling() {
        for (String status : new String[]{"RTSP/1.0 100 Continue", "RTSP/1.0 300 Redirect", "RTSP/1.0 400 Bad Request",
                "RTSP/1.0 500 Error", "SIP/2.0 200 OK", "RTSP/2.0 200 OK", "RTSP/1.0 20 OK", "RTSP/1.0 2000 OK", "RTSP/1.0 200"}) {
            assertThrows(IllegalArgumentException.class, () -> validate(status + "\r\nCSeq: 42\r\n\r\n"));
        }
        for (String body : new String[]{"", "RTSP/1.0 200 OK\nCSeq: 42\n\n", "RTSP/1.0 200 OK\rCSeq: 42\r\r",
                "RTSP/1.0 200 OK\r\nCSeq: 42\r\n", "RTSP/1.0 200 OK\r\n CSeq: 42\r\n\r\n",
                "RTSP/1.0 200 OK\r\nCSeq : 42\r\n\r\n", "RTSP/1.0 200 OK\r\nCSeq: 42\r\ninvalid\r\n\r\n",
                "RTSP/1.0 200 OK\r\nCSeq: 42\r\n\r\ntrailing", "RTSP/1.0 200 OK\r\nCSeq: 42\r\nX: bad\u0000\r\n\r\n"}) {
            assertThrows(IllegalArgumentException.class, () -> validate(body));
        }
        for (String length : new String[]{"1", "-1", "x", "9999999999999999999999", "0\r\ncontent-length: 0"}) {
            assertThrows(IllegalArgumentException.class, () -> validate("RTSP/1.0 200 OK\r\nCSeq: 42\r\nContent-Length: " + length + "\r\n\r\n"));
        }
    }

    @Test
    void boundsBytesHeadersAndLinesAndDoesNotLeakMalformedBody() {
        byte[] body = bytes("RTSP/1.0 200 OK\r\nCSeq: 42\r\n\r\n");
        assertDoesNotThrow(() -> GbMansRtsp.validateResponse(body, 42, body.length));
        assertThrows(IllegalArgumentException.class, () -> GbMansRtsp.validateResponse(body, 42, body.length - 1));
        assertThrows(IllegalArgumentException.class, () -> GbMansRtsp.validateResponse(body, 42, 0));
        assertThrows(IllegalArgumentException.class, () -> GbMansRtsp.validateResponse(null, 42, 8192));
        assertThrows(IllegalArgumentException.class, () -> validate("RTSP/1.0 200 OK\r\nCSeq: 42\r\nX: " + "x".repeat(1024) + "\r\n\r\n"));
        StringBuilder headers = new StringBuilder("RTSP/1.0 200 OK\r\nCSeq: 42\r\n");
        for (int i = 0; i < 64; i++) headers.append("X-").append(i).append(": x\r\n");
        assertThrows(IllegalArgumentException.class, () -> validate(headers + "\r\n"));
        byte[] malformed = body.clone();
        malformed[13] = (byte) 0xc3;
        assertThrows(IllegalArgumentException.class, () -> GbMansRtsp.validateResponse(malformed, 42, 8192));
        var error = assertThrows(IllegalArgumentException.class, () -> validate("SENSITIVE-MARKER"));
        assertFalse(error.getMessage().contains("SENSITIVE-MARKER"));
    }

    private static String request(GbPlaybackControl control, long cseq) {
        return new String(GbMansRtsp.request(control, cseq), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String body) { return body.getBytes(StandardCharsets.UTF_8); }

    private static void validate(String body) { GbMansRtsp.validateResponse(bytes(body), 42, 8192); }
}
