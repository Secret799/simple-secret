package com.ss.zlm4j.service.impl;

import com.ss.zlm4j.security.MediaResourcePolicy;
import com.ss.zlm4j.security.MediaResourceUsage;
import com.ss.zlm4j.service.domain.bo.StartRecordBO;
import com.ss.zlm4j.service.domain.bo.StreamProxyPullerBO;
import com.ss.zlm4j.service.domain.bo.StreamProxyPusherBO;
import com.ss.zlm4j.service.domain.bo.TranscodeBO;
import com.ss.zlm4j.service.domain.bo.VideoStackBO;
import com.ss.zlm4j.service.domain.bo.VideoStackWindowBO;
import com.ss.zlm4j.config.properties.VideoStackValidationProperties;
import com.ss.zlm4j.service.validation.VideoStackValidator;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaResourcePolicyIntegrationTest {

    private final MediaResourcePolicy rejectingPolicy = new MediaResourcePolicy() {
        @Override
        public URI requireAllowed(String value, MediaResourceUsage usage) {
            throw new PolicyRejectedException(usage.name());
        }

        @Override
        public Path requireRecordingPath(String value) {
            throw new PolicyRejectedException("RECORDING");
        }
    };

    @Test
    void pullAndPushProxyValidateUrlBeforeNativeAccess() {
        ZlmMediaServiceImpl service = new ZlmMediaServiceImpl(rejectingPolicy);

        assertThatThrownBy(() -> service.addStreamPullerProxy(new StreamProxyPullerBO().setUrl("rtsp://camera/live")))
                .isInstanceOf(PolicyRejectedException.class)
                .hasMessage("PULL");
        StreamProxyPusherBO pusher = new StreamProxyPusherBO();
        pusher.setUrl("rtmp://remote/live");
        assertThatThrownBy(() -> service.addStreamPusherProxy(pusher))
                .isInstanceOf(PolicyRejectedException.class)
                .hasMessage("PUSH");
    }

    @Test
    void recordingPathIsValidatedBeforeNativeAccess() {
        ZlmMediaServiceImpl service = new ZlmMediaServiceImpl(rejectingPolicy);
        StartRecordBO record = new StartRecordBO().setCustomizedPath("../outside");

        assertThatThrownBy(() -> service.startRecord(record))
                .isInstanceOf(PolicyRejectedException.class)
                .hasMessage("RECORDING");
    }

    @Test
    void snapshotAndTranscodeValidateInputBeforeNativeAccess() {
        assertThatThrownBy(() -> new SnapServiceImpl(rejectingPolicy).snapToBase64("http://camera/snap"))
                .isInstanceOf(PolicyRejectedException.class)
                .hasMessage("SNAPSHOT");
        assertThatThrownBy(() -> new TranscodeServiceImpl(rejectingPolicy)
                .transcode(new TranscodeBO().setUrl("rtsp://camera/live")))
                .isInstanceOf(PolicyRejectedException.class)
                .hasMessage("TRANSCODE");
    }

    @Test
    void videoStackValidatesEveryExternalResourceBeforeNativeAccess() {
        VideoStackBO stack = new VideoStackBO()
                .setId("wall")
                .setRow(1)
                .setCol(1)
                .setWidth(640)
                .setHeight(480)
                .setPushUrl("rtmp://remote/live")
                .setFillImgUrl("https://images/fill.jpg")
                .setWindowList(List.of(new VideoStackWindowBO()
                        .setSpan(List.of(1))
                        .setVideoUrl("rtsp://camera/live")));

        assertThatThrownBy(() -> new VideoStackServiceImpl(rejectingPolicy,
                new VideoStackValidator(new VideoStackValidationProperties())).startStack(stack))
                .isInstanceOf(PolicyRejectedException.class)
                .hasMessage("STACK_OUTPUT");
    }

    private static final class PolicyRejectedException extends RuntimeException {
        private PolicyRejectedException(String message) {
            super(message);
        }
    }
}
