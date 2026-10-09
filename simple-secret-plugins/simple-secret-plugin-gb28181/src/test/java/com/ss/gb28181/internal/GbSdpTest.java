package com.ss.gb28181.internal;

import com.ss.gb28181.GbRtpTarget;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GbSdpTest {
    private static final String SERVER = "34020000002000000001";
    private static final String CHANNEL = "34020000001320000001";
    private static final String SSRC = "0200000001";
    private static final GbRtpTarget UDP = new GbRtpTarget("192.0.2.1", 30000, GbRtpTarget.Transport.UDP);
    private static final GbRtpTarget TCP = new GbRtpTarget("192.0.2.1", 30000, GbRtpTarget.Transport.TCP_PASSIVE);

    @Test
    void offersLivePsOverUdpWithReceiverAddressAndSsrc() {
        String offer = new String(GbSdp.offer(SERVER, CHANNEL, UDP, SSRC), StandardCharsets.UTF_8);
        assertTrue(offer.startsWith("v=0\r\no=" + SERVER + " "));
        assertTrue(offer.contains(" IN IP4 192.0.2.1\r\n"));
        assertTrue(offer.contains("s=Play\r\n"));
        assertTrue(offer.contains("c=IN IP4 192.0.2.1\r\n"));
        assertTrue(offer.contains("t=0 0\r\n"));
        assertTrue(offer.contains("m=video 30000 RTP/AVP 96\r\n"));
        assertTrue(offer.contains("a=recvonly\r\n"));
        assertTrue(offer.contains("a=rtpmap:96 PS/90000\r\n"));
        assertTrue(offer.contains("y=" + SSRC + "\r\n"));
        assertFalse(offer.contains("setup:"));
        assertFalse(offer.replace("\r\n", "").contains("\n"));
    }

    @Test
    void offersIpv6AndPassiveTcpReceiver() {
        var target = new GbRtpTarget("2001:db8::1", 30001, GbRtpTarget.Transport.TCP_PASSIVE);
        String offer = new String(GbSdp.offer(SERVER, CHANNEL, target, SSRC), StandardCharsets.UTF_8);
        assertTrue(offer.contains("c=IN IP6 2001:db8::1\r\n"));
        assertTrue(offer.contains("m=video 30001 TCP/RTP/AVP 96\r\n"));
        assertTrue(offer.contains("a=setup:passive\r\n"));
        assertTrue(offer.contains("a=connection:new\r\n"));
    }

    @Test
    void acceptsDeviceSendonlyAnswerAndMatchingActiveTcp() {
        assertDoesNotThrow(() -> validate(answer(), UDP));
        assertDoesNotThrow(() -> validate(answer().replace("RTP/AVP", "TCP/RTP/AVP")
                + "a=setup:active\r\na=connection:new\r\n", TCP));
        assertDoesNotThrow(() -> validate(answer().replace("\r\n", "\n"), UDP));
        assertDoesNotThrow(() -> validate(answer().replace("PS/90000", "ps/90000"), UDP));
    }

    @Test
    void acceptsZeroSsrcAsAnUnsignedRtpIdentifier() {
        assertDoesNotThrow(() -> GbSdp.offer(SERVER, CHANNEL, UDP, "0000000000"));
        assertDoesNotThrow(() -> GbSdp.validateAnswer(answer().replace(SSRC, "0000000000")
                .getBytes(StandardCharsets.UTF_8), UDP, "0000000000", 8192));
    }

    @Test
    void acceptsMediaConnectionAndInheritedSessionDirection() {
        String answer = answer().replace("c=IN IP4 192.0.2.2\r\n", "")
                .replace("m=video", "a=sendonly\r\nm=video")
                .replace("a=sendonly\r\na=rtpmap", "c=IN IP4 192.0.2.2\r\na=rtpmap");
        assertDoesNotThrow(() -> validate(answer, UDP));
    }

    @Test
    void rejectsMismatchedOrRejectedMediaAndWrongSendDirection() {
        for (String[] change : new String[][]{{"video", "audio"}, {"31000", "0"}, {"31000", "65536"},
                {"RTP/AVP", "TCP/RTP/AVP"}, {"PS/90000", "H264/90000"}, {"PS/90000", "PS/8000"},
                {"RTP/AVP 96", "RTP/AVP 97"}, {"RTP/AVP 96", "RTP/AVP 96 97"},
                {"sendonly", "recvonly"}, {"sendonly", "sendrecv"}, {"sendonly", "inactive"},
                {"y=" + SSRC, "y=0200000002"}, {"s=Play", "s=Playback"}}) {
            assertInvalid(answer().replace(change[0], change[1]));
        }
    }

    @Test
    void rejectsMissingAndConflictingCriticalFields() {
        for (String line : answer().split("\r\n")) {
            assertInvalid(answer().replace(line + "\r\n", ""));
        }
        for (String line : new String[]{"v=0", "o=" + CHANNEL + " 0 0 IN IP4 192.0.2.2", "s=Play",
                "c=IN IP4 192.0.2.2", "t=0 0", "m=video 31000 RTP/AVP 96", "a=sendonly",
                "a=rtpmap:96 PS/90000", "y=" + SSRC}) {
            assertInvalid(answer().replace(line + "\r\n", line + "\r\n" + line + "\r\n"));
        }
        assertInvalid(answer() + "a=recvonly\r\n");
    }

    @Test
    void rejectsUnsafeConnectionAddressesAndMalformedNumericFields() {
        for (String address : new String[]{"example.invalid", "0.0.0.0", "224.0.0.1", "192.0.2.2/127", "::1"}) {
            assertInvalid(answer().replace("192.0.2.2", address));
        }
        assertInvalid(answer().replace("t=0 0", "t=-1 0"));
        assertInvalid(answer().replace(" 0 0 IN", " x 0 IN"));
        assertInvalid(answer().replace("31000", "+31000"));
        assertInvalid(answer().replace("v=0", "v=1"));
    }

    @Test
    void rejectsIncorrectTcpRolesOrConnectionReuse() {
        String tcp = answer().replace("RTP/AVP", "TCP/RTP/AVP");
        for (String attrs : new String[]{"", "a=setup:passive\r\na=connection:new\r\n",
                "a=setup:actpass\r\na=connection:new\r\n", "a=setup:active\r\na=connection:existing\r\n"}) {
            assertThrows(IllegalArgumentException.class, () -> validate(tcp + attrs, TCP));
        }
        assertInvalid(answer() + "a=setup:active\r\na=connection:new\r\n");
    }

    @Test
    void boundsBodyLinesAndRejectsMalformedEncodingWithoutLeakingBody() {
        byte[] body = answer().getBytes(StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> GbSdp.validateAnswer(body, UDP, SSRC, body.length));
        assertThrows(IllegalArgumentException.class, () -> GbSdp.validateAnswer(body, UDP, SSRC, body.length - 1));
        assertThrows(IllegalArgumentException.class, () -> GbSdp.validateAnswer(body, UDP, SSRC, 0));
        assertThrows(IllegalArgumentException.class, () -> GbSdp.validateAnswer(null, UDP, SSRC, 8192));
        assertInvalid(answer() + "a=x:" + "x".repeat(1024) + "\r\n");
        assertInvalid(answer() + "a=x\r\n".repeat(256));
        assertInvalid(answer().replace("\r\n", "\r"));
        assertInvalid(answer() + "a=x:\u0000\r\n");
        byte[] malformed = body.clone();
        malformed[malformed.length - 2] = (byte) 0xc3;
        assertThrows(IllegalArgumentException.class, () -> GbSdp.validateAnswer(malformed, UDP, SSRC, 8192));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> validate(answer().replace("PS/90000", "SENSITIVE-MARKER"), UDP));
        assertFalse(exception.getMessage().contains("SENSITIVE-MARKER"));
    }

    @Test
    void validatesOfferAndExpectedAnswerArguments() {
        for (String value : new String[]{null, "", "123", SERVER + "\r\na=x"}) {
            assertThrows(IllegalArgumentException.class, () -> GbSdp.offer(value, CHANNEL, UDP, SSRC));
            assertThrows(IllegalArgumentException.class, () -> GbSdp.offer(SERVER, value, UDP, SSRC));
        }
        for (String ssrc : new String[]{null, "", "020000001", "1200000001", "020000000x"}) {
            assertThrows(IllegalArgumentException.class, () -> GbSdp.offer(SERVER, CHANNEL, UDP, ssrc));
            assertThrows(IllegalArgumentException.class,
                    () -> GbSdp.validateAnswer(answer().getBytes(StandardCharsets.UTF_8), UDP, ssrc, 8192));
        }
        assertThrows(IllegalArgumentException.class, () -> GbSdp.offer(SERVER, CHANNEL, null, SSRC));
    }

    private static String answer() {
        return "v=0\r\no=" + CHANNEL + " 0 0 IN IP4 192.0.2.2\r\ns=Play\r\n"
                + "c=IN IP4 192.0.2.2\r\nt=0 0\r\nm=video 31000 RTP/AVP 96\r\n"
                + "a=sendonly\r\na=rtpmap:96 PS/90000\r\ny=" + SSRC + "\r\n";
    }

    private static void validate(String answer, GbRtpTarget target) {
        GbSdp.validateAnswer(answer.getBytes(StandardCharsets.UTF_8), target, SSRC, 8192);
    }

    private static void assertInvalid(String answer) {
        assertThrows(IllegalArgumentException.class, () -> validate(answer, UDP));
    }
}
