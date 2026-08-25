package com.ss.application.easymedia.sei;

import com.ss.application.djisei.config.DjiSeiProperties;
import com.ss.application.djisei.parser.SeiMessage;
import com.ss.application.djisei.parser.SeiParseIssue;
import com.ss.application.djisei.parser.SeiParseResult;
import com.ss.application.djisei.parser.VideoCodec;
import com.ss.easymedia.callback.TrackDelegateCallback.TackDelegateInfo;
import com.ss.zlm4j.domain.MediaSourceDomain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DjiSeiEventHubTest {

    private DjiSeiEventHub hub;

    @AfterEach
    void tearDown() {
        if (hub != null) {
            hub.close();
        }
    }

    @Test
    void shouldExposeStatsUtf8PayloadAndIssuesWithoutUuidPrefixInText() throws Exception {
        DjiSeiProperties properties = new DjiSeiProperties();
        properties.setPreviewBytes(8);
        hub = new DjiSeiEventHub(Clock.fixed(
                Instant.parse("2026-08-25T01:00:00Z"), ZoneOffset.UTC), properties);
        MediaSourceDomain source = new MediaSourceDomain();
        source.setSchema("rtmp");
        source.setApp("live");
        source.setStream("dji-01");
        UUID uuid = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        byte[] json = "{\"latitude\":31.2,\"status\":\"飞行\"}".getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[16 + json.length];
        putLong(payload, 0, uuid.getMostSignificantBits());
        putLong(payload, 8, uuid.getLeastSignificantBits());
        System.arraycopy(json, 0, payload, 16, json.length);

        hub.onStreamRegistered(source);
        hub.onFrame(source, new TackDelegateInfo().setPts(1200L).setDts(1160L), VideoCodec.H264,
                new SeiParseResult(List.of(new SeiMessage(5, payload)), 1,
                        List.of(new SeiParseIssue("TRUNCATED_PAYLOAD", "payload is truncated"))));

        DjiSeiEventHub.InitialSnapshot snapshot = awaitMessages("live", "dji-01", 1);
        assertThat(snapshot.stats().active()).isTrue();
        assertThat(snapshot.stats().codec()).isEqualTo("H264");
        assertThat(snapshot.stats().videoFrames()).isEqualTo(1);
        assertThat(snapshot.stats().seiNalUnits()).isEqualTo(1);
        assertThat(snapshot.stats().seiMessages()).isEqualTo(1);
        assertThat(snapshot.stats().parseIssues()).isEqualTo(1);
        assertThat(snapshot.recentEvents()).hasSize(2);
        DjiSeiEventHub.DiagnosticEvent event = snapshot.recentEvents().get(0);
        assertThat(event.payloadType()).isEqualTo(5);
        assertThat(event.uuid()).isEqualTo(uuid);
        assertThat(event.pts()).isEqualTo(1200L);
        assertThat(event.text()).isEqualTo(new String(json, StandardCharsets.UTF_8));
        assertThat(event.hex()).hasSize(payload.length * 2);
        assertThat(event.truncated()).isFalse();
        assertThat(snapshot.recentEvents().get(1).issueCode()).isEqualTo("TRUNCATED_PAYLOAD");
    }

    @Test
    void shouldOnlyRetainTenMostRecentEvents() throws Exception {
        hub = new DjiSeiEventHub(Clock.fixed(
                Instant.parse("2026-08-25T01:00:00Z"), ZoneOffset.UTC), new DjiSeiProperties());
        MediaSourceDomain source = new MediaSourceDomain();
        source.setSchema("rtmp");
        source.setApp("live");
        source.setStream("dji-02");

        hub.onStreamRegistered(source);
        for (int index = 0; index < 12; index++) {
            hub.onFrame(source, new TackDelegateInfo().setPts((long) index).setDts((long) index), VideoCodec.H265,
                    new SeiParseResult(List.of(new SeiMessage(4, new byte[]{(byte) index})), 1, List.of()));
        }

        DjiSeiEventHub.InitialSnapshot snapshot = awaitMessages("live", "dji-02", 12);
        assertThat(snapshot.recentEvents()).hasSize(10);
        assertThat(snapshot.recentEvents()).extracting(DjiSeiEventHub.DiagnosticEvent::pts)
                .containsExactly(2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
    }

    private DjiSeiEventHub.InitialSnapshot awaitMessages(String app, String stream, long count) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        DjiSeiEventHub.InitialSnapshot snapshot;
        do {
            snapshot = hub.snapshot(app, stream);
            if (snapshot.stats().seiMessages() >= count) {
                return snapshot;
            }
            Thread.sleep(10L);
        } while (System.nanoTime() < deadline);
        return snapshot;
    }

    private static void putLong(byte[] target, int offset, long value) {
        for (int index = 7; index >= 0; index--) {
            target[offset + index] = (byte) value;
            value >>>= 8;
        }
    }
}
