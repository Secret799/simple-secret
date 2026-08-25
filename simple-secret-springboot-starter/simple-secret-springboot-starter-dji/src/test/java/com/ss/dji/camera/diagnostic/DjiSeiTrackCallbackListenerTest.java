package com.ss.dji.camera.diagnostic;

import com.ss.dji.camera.config.DjiSeiProperties;
import com.ss.dji.camera.parser.H26xSeiParser;
import com.ss.dji.camera.parser.SeiParseResult;
import com.ss.dji.camera.parser.VideoCodec;
import com.ss.easymedia.callback.TrackDelegateCallback.TackDelegateInfo;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.domain.TrackDomain;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DjiSeiTrackCallbackListenerTest {

    @Test
    void shouldNotifyListenerAndIsolateItsFailure() {
        AtomicInteger registrations = new AtomicInteger();
        AtomicInteger frames = new AtomicInteger();
        AtomicInteger deregistrations = new AtomicInteger();
        DjiSeiEventListener listener = new DjiSeiEventListener() {
            @Override
            public void onStreamRegistered(MediaSourceDomain source) {
                registrations.incrementAndGet();
            }

            @Override
            public void onFrame(MediaSourceDomain source, TackDelegateInfo frame,
                                VideoCodec codec, SeiParseResult result) {
                frames.incrementAndGet();
                throw new IllegalStateException("listener unavailable");
            }

            @Override
            public void onStreamDeregistered(MediaSourceDomain source) {
                deregistrations.incrementAndGet();
            }
        };
        DjiSeiTrackCallback callback = new DjiSeiTrackCallback(new H26xSeiParser(),
                new DjiSeiProperties(), Clock.systemUTC(), List.of(listener));
        MediaSourceDomain source = new MediaSourceDomain();
        source.setSchema("rtmp");
        source.setApp("live");
        source.setStream("dji-01");
        TrackDomain track = new TrackDomain();
        track.setIsVideo(1);
        track.setCodecIdName("H264");

        callback.onMediaSourceRegistered(source);
        assertThatCode(() -> callback.callback(source, track,
                new TackDelegateInfo().setData(new byte[]{0, 0, 0, 1, 0x65, 1})))
                .doesNotThrowAnyException();
        callback.onMediaSourceDeregistered(source);

        assertThat(registrations).hasValue(1);
        assertThat(frames).hasValue(1);
        assertThat(deregistrations).hasValue(1);
    }
}
