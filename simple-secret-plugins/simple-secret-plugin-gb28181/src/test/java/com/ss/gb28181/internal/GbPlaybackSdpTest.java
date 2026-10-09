package com.ss.gb28181.internal;

import com.ss.gb28181.GbPlaybackRange;
import com.ss.gb28181.GbRtpTarget;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class GbPlaybackSdpTest {
    private static final String SERVER = "34020000002000000001";
    private static final String CHANNEL = "34020000001320000001";
    private static final String SSRC = "1200000001";
    private static final GbRtpTarget UDP = new GbRtpTarget("192.0.2.1", 30000, GbRtpTarget.Transport.UDP);
    private static final GbPlaybackRange RANGE = new GbPlaybackRange(Instant.ofEpochSecond(1799971200), Instant.ofEpochSecond(1799974800));

    @Test
    void offersPlaybackAllRecordingsWithUnixSecondsAndHistoricalSsrc() {
        String offer = new String(GbSdp.offer(SERVER, CHANNEL, UDP, SSRC, RANGE), StandardCharsets.UTF_8);
        assertTrue(offer.contains("s=Playback\r\nu=" + CHANNEL + ":0\r\n"));
        assertTrue(offer.contains("t=1799971200 1799974800\r\n"));
        assertTrue(offer.contains("m=video 30000 RTP/AVP 96\r\na=recvonly\r\na=rtpmap:96 PS/90000\r\ny=" + SSRC));
        assertFalse(offer.replace("\r\n", "").contains("\n"));
        var fullRange = new GbPlaybackRange(Instant.EPOCH, Instant.ofEpochSecond(253402300799L));
        assertTrue(new String(GbSdp.offer(SERVER, CHANNEL, UDP, SSRC, fullRange), StandardCharsets.UTF_8)
                .contains("t=0 253402300799\r\n"));
    }

    @Test
    void acceptsMatchingUdpAndActiveTcpAnswers() {
        assertDoesNotThrow(() -> validate(answer(), UDP));
        var tcp = new GbRtpTarget(UDP.address(), UDP.port(), GbRtpTarget.Transport.TCP_PASSIVE);
        String tcpOffer = new String(GbSdp.offer(SERVER, CHANNEL, tcp, SSRC, RANGE), StandardCharsets.UTF_8);
        assertTrue(tcpOffer.contains("a=setup:passive\r\na=connection:new\r\n"));
        assertDoesNotThrow(() -> validate(answer().replace("RTP/AVP", "TCP/RTP/AVP")
                + "a=setup:active\r\na=connection:new\r\n", tcp));
    }

    @Test
    void rejectsWrongModeRangeTransportDirectionAndSsrc() {
        for (String[] change : new String[][]{{"s=Playback", "s=Play"}, {"s=Playback", "s=Download"},
                {"1799971200 1799974800", "0 0"}, {"1799971200", "1799971201"},
                {"1799974800", "1799974801"}, {"1799971200", "4008960000"},
                {"y=" + SSRC, "y=0200000001"}, {"PS/90000", "H264/90000"},
                {"sendonly", "recvonly"}, {"RTP/AVP", "TCP/RTP/AVP"}}) {
            assertThrows(IllegalArgumentException.class, () -> validate(answer().replace(change[0], change[1]), UDP));
        }
        assertThrows(IllegalArgumentException.class, () -> validate(answer().replace("t=1799971200 1799974800\r\n", ""), UDP));
    }

    @Test
    void preservesLiveOverloadsAndRequiresModeSpecificSsrc() {
        assertArrayEquals(GbSdp.offer(SERVER, CHANNEL, UDP, "0200000001"),
                GbSdp.offer(SERVER, CHANNEL, UDP, "0200000001", null));
        assertThrows(IllegalArgumentException.class, () -> GbSdp.offer(SERVER, CHANNEL, UDP, "0200000001", RANGE));
        assertThrows(IllegalArgumentException.class, () -> GbSdp.offer(SERVER, CHANNEL, UDP, SSRC, null));
        assertThrows(IllegalArgumentException.class, () -> GbSdp.validateAnswer(answer().getBytes(StandardCharsets.UTF_8), UDP, SSRC, 8192));
        assertThrows(IllegalArgumentException.class, () -> GbSdp.validateAnswer(answer().getBytes(StandardCharsets.UTF_8), UDP, "0200000001", 8192, RANGE));
    }

    private static String answer() {
        return "v=0\r\no=" + CHANNEL + " 0 0 IN IP4 192.0.2.2\r\ns=Playback\r\n"
                + "c=IN IP4 192.0.2.2\r\nt=1799971200 1799974800\r\nm=video 31000 RTP/AVP 96\r\n"
                + "a=sendonly\r\na=rtpmap:96 PS/90000\r\ny=" + SSRC + "\r\n";
    }

    private static void validate(String answer, GbRtpTarget target) {
        GbSdp.validateAnswer(answer.getBytes(StandardCharsets.UTF_8), target, SSRC, 8192, RANGE);
    }
}
