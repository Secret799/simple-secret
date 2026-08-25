package com.ss.dji.camera.diagnostic;

import com.ss.dji.camera.config.DjiSeiProperties;
import com.ss.dji.camera.event.DjiSeiPacketParsedEvent;
import com.ss.dji.camera.parser.H26xSeiParser;
import com.ss.dji.camera.parser.VideoCodec;
import com.ss.easymedia.callback.TrackDelegateCallback.TackDelegateInfo;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.domain.TrackDomain;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DjiSeiPacketEventPublishingTest {

    @Test
    void shouldPublishEveryCompleteSeiMessageWithFullPayload() {
        List<DjiSeiPacketParsedEvent> events = new ArrayList<>();
        DjiSeiProperties properties = new DjiSeiProperties();
        properties.setMaxMessageLogs(1);
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T07:00:00Z"), ZoneOffset.UTC);
        DjiSeiTrackCallback callback = new DjiSeiTrackCallback(new H26xSeiParser(), properties, clock,
                List.of(), event -> events.add((DjiSeiPacketParsedEvent) event));
        MediaSourceDomain source = source();
        callback.onMediaSourceRegistered(source);

        callback.callback(source, videoTrack(), frame(bytes(
                0, 0, 0, 1, 6,
                1, 3, 'D', 'J', 'I',
                5, 4, 1, 2, 3, 4,
                0x80)));

        assertThat(events).hasSize(2);
        assertThat(events).extracting(DjiSeiPacketParsedEvent::getPayloadType)
                .containsExactly(1, 5);
        DjiSeiPacketParsedEvent first = events.get(0);
        assertThat(first.getParsedAt()).isEqualTo(Instant.parse("2026-08-25T07:00:00Z"));
        assertThat(first.getSchema()).isEqualTo("rtmp");
        assertThat(first.getApp()).isEqualTo("live");
        assertThat(first.getStream()).isEqualTo("dji-01");
        assertThat(first.getVhost()).isEqualTo("__defaultVhost__");
        assertThat(first.getCodec()).isEqualTo(VideoCodec.H264);
        assertThat(first.getPts()).isEqualTo(12L);
        assertThat(first.getDts()).isEqualTo(10L);
        assertThat(first.getPayloadBytes()).isEqualTo(3);
        assertThat(first.getPayload()).containsExactly((byte) 'D', (byte) 'J', (byte) 'I');

        byte[] returnedPayload = first.getPayload();
        returnedPayload[0] = 0;
        assertThat(first.getPayload()).containsExactly((byte) 'D', (byte) 'J', (byte) 'I');
    }

    @Test
    void shouldNotPublishTruncatedSeiMessage() {
        List<Object> events = new ArrayList<>();
        DjiSeiTrackCallback callback = new DjiSeiTrackCallback(new H26xSeiParser(),
                new DjiSeiProperties(), Clock.systemUTC(), List.of(), events::add);
        MediaSourceDomain source = source();
        callback.onMediaSourceRegistered(source);

        callback.callback(source, videoTrack(), frame(bytes(0, 0, 0, 1, 6, 5, 10, 1, 2)));

        assertThat(events).isEmpty();
    }

    @Test
    void shouldIsolateApplicationEventListenerFailure() {
        DjiSeiTrackCallback callback = new DjiSeiTrackCallback(new H26xSeiParser(),
                new DjiSeiProperties(), Clock.systemUTC(), List.of(), event -> {
                    throw new IllegalStateException("downstream unavailable");
                });
        MediaSourceDomain source = source();
        callback.onMediaSourceRegistered(source);

        assertThatCode(() -> callback.callback(source, videoTrack(),
                frame(bytes(0, 0, 0, 1, 6, 1, 1, 'A', 0x80))))
                .doesNotThrowAnyException();
    }

    private static MediaSourceDomain source() {
        MediaSourceDomain source = new MediaSourceDomain();
        source.setSchema("rtmp");
        source.setApp("live");
        source.setStream("dji-01");
        source.setVhost("__defaultVhost__");
        return source;
    }

    private static TrackDomain videoTrack() {
        TrackDomain track = new TrackDomain();
        track.setIsVideo(1);
        track.setCodecIdName("H264");
        return track;
    }

    private static TackDelegateInfo frame(byte[] data) {
        return new TackDelegateInfo().setData(data).setPts(12L).setDts(10L);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
