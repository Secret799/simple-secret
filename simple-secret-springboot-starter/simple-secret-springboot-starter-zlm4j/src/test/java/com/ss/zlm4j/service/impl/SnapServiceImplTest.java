package com.ss.zlm4j.service.impl;

import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.exception.ZlmOperationException;
import com.ss.zlm4j.security.MediaResourcePolicy;
import com.ss.zlm4j.security.MediaResourceUsage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnapServiceImplTest {

    @Test
    void keyFrameFlagShouldUseBitMaskSemantics() throws Exception {
        Method isKeyFrame = SnapServiceImpl.class.getDeclaredMethod("isKeyFrame", int.class);
        isKeyFrame.setAccessible(true);

        assertThat(isKeyFrame.invoke(null, org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY))
                .isEqualTo(true);
        assertThat(isKeyFrame.invoke(null,
                org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY | 0x10))
                .isEqualTo(true);
        assertThat(isKeyFrame.invoke(null, 0x10)).isEqualTo(false);
    }

    @TempDir
    Path tempDir;

    @Test
    void deletesTemporaryImageAfterSuccessfulCapture() throws Exception {
        ZlmMediaProperties properties = properties();
        SnapServiceImpl service = new SnapServiceImpl(passThroughPolicy(), properties,
                (url, output, timeoutMs) -> Files.writeString(output, "jpeg"));

        assertThat(service.snapToBase64("https://camera.test/snapshot"))
                .isEqualTo(Base64.getEncoder().encodeToString("jpeg".getBytes()));
        assertThat(Files.list(tempDir.resolve("snap"))).isEmpty();
    }

    @Test
    void deletesTemporaryImageWhenCaptureFails() throws Exception {
        ZlmMediaProperties properties = properties();
        SnapServiceImpl service = new SnapServiceImpl(passThroughPolicy(), properties,
                (url, output, timeoutMs) -> {
                    Files.writeString(output, "partial");
                    throw new IllegalStateException("capture failed");
                });

        assertThatThrownBy(() -> service.snapToBase64("https://camera.test/snapshot"))
                .isInstanceOf(ZlmOperationException.class);
        assertThat(Files.list(tempDir.resolve("snap"))).isEmpty();
    }

    @Test
    void passesConfiguredTimeoutToNativeCapture() {
        ZlmMediaProperties properties = properties();
        properties.setSnapTimeoutMs(4321);
        AtomicInteger capturedTimeout = new AtomicInteger();
        SnapServiceImpl service = new SnapServiceImpl(passThroughPolicy(), properties,
                (url, output, timeoutMs) -> {
                    capturedTimeout.set(timeoutMs);
                    Files.write(output, new byte[]{1});
                });

        service.snapToBase64("rtsp://camera.test/live");

        assertThat(capturedTimeout).hasValue(4321);
    }

    private ZlmMediaProperties properties() {
        ZlmMediaProperties properties = new ZlmMediaProperties();
        properties.setRootPath(tempDir.toString());
        return properties;
    }

    private static MediaResourcePolicy passThroughPolicy() {
        return new MediaResourcePolicy() {
            @Override
            public URI requireAllowed(String value, MediaResourceUsage usage) {
                return URI.create(value);
            }

            @Override
            public Path requireRecordingPath(String value) {
                return Path.of(value);
            }
        };
    }
}
