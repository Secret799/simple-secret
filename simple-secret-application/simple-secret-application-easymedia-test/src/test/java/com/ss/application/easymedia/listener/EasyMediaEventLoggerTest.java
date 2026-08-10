package com.ss.application.easymedia.listener;

import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.domain.RecordInfoDomain;
import com.ss.zlm4j.event.RecordShardingFileEvent;
import com.ss.zlm4j.event.StreamRegisteredEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class EasyMediaEventLoggerTest {

    private final EasyMediaEventLogger listener = new EasyMediaEventLogger();

    @Test
    void acceptsStreamRegisteredEvent() {
        MediaSourceDomain source = new MediaSourceDomain();
        source.setApp("live");
        source.setStream("camera-01");
        source.setSchema("rtsp");

        assertThatCode(() -> listener.onStreamRegistered(new StreamRegisteredEvent(source)))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsRecordShardingEvent() {
        RecordShardingFileEvent event = new RecordShardingFileEvent(
                RecordShardingFileEvent.RecordSource.MP4, new RecordInfoDomain());

        assertThatCode(() -> listener.onRecordSharding(event))
                .doesNotThrowAnyException();
    }
}
