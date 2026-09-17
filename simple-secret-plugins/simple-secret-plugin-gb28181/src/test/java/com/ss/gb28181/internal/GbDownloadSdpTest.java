package com.ss.gb28181.internal;

import com.ss.gb28181.GbDownloadRequest;
import com.ss.gb28181.GbPlaybackRange;
import com.ss.gb28181.GbRtpTarget;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class GbDownloadSdpTest {
    private static final String SERVER = "34020000002000000001";
    private static final String CHANNEL = "34020000001320000001";
    private static final String SSRC = "1200000001";
    private static final GbRtpTarget UDP = new GbRtpTarget("192.0.2.1", 30000, GbRtpTarget.Transport.UDP);
    private static final GbRtpTarget TCP = new GbRtpTarget("2001:db8::1", 30001, GbRtpTarget.Transport.TCP_PASSIVE);
    private static final GbPlaybackRange RANGE = new GbPlaybackRange(Instant.ofEpochSecond(1799971200), Instant.ofEpochSecond(1799974800));
    private static final GbDownloadRequest REQUEST = new GbDownloadRequest(RANGE, 4);

    @Test
    void offersDownloadWithUnixRangeSpeedAndHistoricalSsrc() {
        String offer = new String(GbSdp.downloadOffer(SERVER, CHANNEL, UDP, SSRC, REQUEST), StandardCharsets.UTF_8);
        assertTrue(offer.contains("s=Download\r\nu=" + CHANNEL + ":0\r\n"));
        assertTrue(offer.contains("t=1799971200 1799974800\r\n"));
        assertTrue(offer.contains("m=video 30000 RTP/AVP 96\r\na=recvonly\r\na=rtpmap:96 PS/90000\r\n"));
        assertTrue(offer.contains("a=downloadspeed:4\r\n"));
        assertTrue(offer.contains("y=" + SSRC + "\r\n"));
        assertFalse(offer.contains("filesize"));
        assertFalse(offer.replace("\r\n", "").contains("\n"));
        String tcp = new String(GbSdp.downloadOffer(SERVER, CHANNEL, TCP, SSRC, GbDownloadRequest.normal(RANGE)), StandardCharsets.UTF_8);
        assertTrue(tcp.contains("c=IN IP6 2001:db8::1\r\n"));
        assertTrue(tcp.contains("m=video 30001 TCP/RTP/AVP 96\r\n"));
        assertTrue(tcp.contains("a=setup:passive\r\na=connection:new\r\n"));
        assertTrue(tcp.contains("a=downloadspeed:1\r\n"));
    }

    @Test
    void acceptsOptionalFileSizeInEitherScopeIncludingZeroAndMaximumLong() {
        assertEquals(OptionalLong.empty(), validate(answer(), UDP));
        for (long size : new long[]{0, 1, 123456789L, Long.MAX_VALUE}) {
            String attr = "a=filesize:" + size + "\r\n";
            assertEquals(OptionalLong.of(size), validate(answer() + attr, UDP));
            assertEquals(OptionalLong.of(size), validate(answer().replace("m=video", attr + "m=video"), UDP));
        }
        assertEquals(OptionalLong.of(42), validate(answer().replace("RTP/AVP", "TCP/RTP/AVP")
                + "a=setup:active\r\na=connection:new\r\na=filesize:42\r\n", TCP));
    }

    @Test
    void acceptsDifferentSelectedSpeedAndLfOnlyAnswers() {
        for (int speed : new int[]{1, 2, 128}) {
            String attr = "a=downloadspeed:" + speed + "\r\n";
            assertEquals(OptionalLong.empty(), validate(answer() + attr, UDP));
            assertEquals(OptionalLong.empty(), validate(answer().replace("m=video", attr + "m=video"), UDP));
        }
        assertEquals(OptionalLong.of(100), validate((answer() + "a=filesize:100\r\n").replace("\r\n", "\n"), UDP));
    }

    @Test
    void rejectsDuplicateFileSizeWithinOrAcrossScopes() {
        for (String second : new String[]{"10", "11"}) {
            String attr = "a=filesize:10\r\n";
            assertInvalid(answer() + attr + "a=filesize:" + second + "\r\n");
            assertInvalid(answer().replace("m=video", attr + attr + "m=video"));
            assertInvalid(answer().replace("m=video", attr + "m=video") + "a=filesize:" + second + "\r\n");
        }
    }

    @Test
    void rejectsInvalidFileSizeAndSpeedWithoutLeakingDeviceContent() {
        for (String size : new String[]{"", "-1", "+1", "1.5", "9223372036854775808", "x", " 1", "1 ", "１２"}) {
            assertInvalid(answer() + "a=filesize:" + size + "\r\n");
            assertInvalid(answer().replace("m=video", "a=filesize:" + size + "\r\nm=video"));
        }
        assertInvalid(answer() + "a=filesize\r\n");
        for (String speed : new String[]{"", "0", "-1", "129", "+1", "1.5", "2147483648", "x", " 1", "1 "}) {
            assertInvalid(answer() + "a=downloadspeed:" + speed + "\r\n");
        }
        assertInvalid(answer() + "a=downloadspeed\r\n");
        assertInvalid(answer() + "a=downloadspeed:1\r\na=downloadspeed:2\r\n");
        assertInvalid(answer().replace("m=video", "a=downloadspeed:1\r\nm=video") + "a=downloadspeed:2\r\n");
        var exception = assertThrows(IllegalArgumentException.class, () -> validate(answer() + "a=filesize:SENSITIVE-MARKER\r\n", UDP));
        assertFalse(exception.getMessage().contains("SENSITIVE-MARKER"));
    }

    @Test
    void rejectsWrongModeRangeMediaAndTcpRoles() {
        for (String[] change : new String[][]{{"s=Download", "s=Play"}, {"s=Download", "s=Playback"},
                {"1799971200 1799974800", "0 0"}, {"1799971200", "1799971201"}, {"1799974800", "1799974801"},
                {"y=" + SSRC, "y=0200000001"}, {"PS/90000", "H264/90000"}, {"sendonly", "recvonly"},
                {"RTP/AVP", "TCP/RTP/AVP"}, {"31000", "0"}, {"RTP/AVP 96", "RTP/AVP 97"}}) {
            assertInvalid(answer().replace(change[0], change[1]));
        }
        String tcp = answer().replace("RTP/AVP", "TCP/RTP/AVP");
        assertThrows(IllegalArgumentException.class, () -> validate(tcp, TCP));
        assertThrows(IllegalArgumentException.class, () -> validate(tcp + "a=setup:passive\r\na=connection:new\r\n", TCP));
        assertInvalid(answer() + "a=setup:active\r\na=connection:new\r\n");
    }

    @Test
    void preservesBoundedParserAndChecksRequiredRequestArguments() {
        byte[] bytes = answer().getBytes(StandardCharsets.UTF_8);
        assertEquals(OptionalLong.empty(), GbSdp.validateDownloadAnswer(bytes, UDP, SSRC, bytes.length, REQUEST));
        assertThrows(IllegalArgumentException.class, () -> GbSdp.validateDownloadAnswer(bytes, UDP, SSRC, bytes.length - 1, REQUEST));
        assertThrows(IllegalArgumentException.class, () -> GbSdp.validateDownloadAnswer(null, UDP, SSRC, 8192, REQUEST));
        assertInvalid(answer() + "a=x:" + "x".repeat(1024) + "\r\n");
        assertInvalid(answer() + "a=x\r\n".repeat(256));
        assertInvalid(answer() + "a=x:\u0000\r\n");
        byte[] malformed = bytes.clone();
        malformed[malformed.length - 2] = (byte) 0xc3;
        assertThrows(IllegalArgumentException.class, () -> GbSdp.validateDownloadAnswer(malformed, UDP, SSRC, 8192, REQUEST));
        assertThrows(NullPointerException.class, () -> GbSdp.downloadOffer(SERVER, CHANNEL, UDP, SSRC, null));
        assertThrows(NullPointerException.class, () -> GbSdp.validateDownloadAnswer(bytes, UDP, SSRC, 8192, null));
        assertThrows(IllegalArgumentException.class, () -> GbSdp.downloadOffer(SERVER, CHANNEL, UDP, "0200000001", REQUEST));
        assertThrows(IllegalArgumentException.class, () -> GbSdp.downloadOffer(SERVER, CHANNEL, null, SSRC, REQUEST));
        assertThrows(IllegalArgumentException.class, () -> GbSdp.downloadOffer(SERVER, "invalid", UDP, SSRC, REQUEST));
    }

    @Test
    void keepsUnrelatedDownloadAttributesIgnoredByLegacyAnswerValidation() {
        String unrelated = "a=filesize:-1\r\na=filesize:garbage\r\na=downloadspeed:0\r\na=downloadspeed:bad\r\n";
        String historical = answer().replace("s=Download", "s=Playback") + unrelated;
        assertDoesNotThrow(() -> GbSdp.validateAnswer(historical.getBytes(StandardCharsets.UTF_8), UDP, SSRC, 8192, RANGE));
        String live = historical.replace("s=Playback", "s=Play").replace("1799971200 1799974800", "0 0").replace(SSRC, "0200000001");
        assertDoesNotThrow(() -> GbSdp.validateAnswer(live.getBytes(StandardCharsets.UTF_8), UDP, "0200000001", 8192, null));
        assertArrayEquals(GbSdp.offer(SERVER, CHANNEL, UDP, "0200000001"), GbSdp.offer(SERVER, CHANNEL, UDP, "0200000001", null));
    }

    private static String answer() {
        return "v=0\r\no=" + CHANNEL + " 0 0 IN IP4 192.0.2.2\r\ns=Download\r\n"
                + "c=IN IP4 192.0.2.2\r\nt=1799971200 1799974800\r\nm=video 31000 RTP/AVP 96\r\n"
                + "a=sendonly\r\na=rtpmap:96 PS/90000\r\ny=" + SSRC + "\r\n";
    }

    private static OptionalLong validate(String answer, GbRtpTarget target) {
        return GbSdp.validateDownloadAnswer(answer.getBytes(StandardCharsets.UTF_8), target, SSRC, 8192, REQUEST);
    }

    private static void assertInvalid(String answer) {
        assertThrows(IllegalArgumentException.class, () -> validate(answer, UDP));
    }
}
