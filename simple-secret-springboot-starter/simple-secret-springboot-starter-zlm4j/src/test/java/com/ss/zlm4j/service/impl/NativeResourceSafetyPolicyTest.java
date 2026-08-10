package com.ss.zlm4j.service.impl;

import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FFmpeg native 资源与敏感日志的源码安全回归测试。
 *
 * @author junpzx
 * @since 2026-08-10
 */
class NativeResourceSafetyPolicyTest {

    @Test
    void snapshotNativeCaptureUsesOneFinallyCleanupPath() throws IOException {
        String source = readSource("service/impl/SnapServiceImpl.java");
        String captureMethod = between(source,
                "private void captureNative", "\n    private void openSnapshotInput");

        assertThat(captureMethod).contains("finally {");
        assertThat(countOccurrences(captureMethod, "free(url,")).isEqualTo(1);
        assertThat(captureMethod.lines().count()).isLessThanOrEqualTo(80);
    }

    @Test
    void transcodeLogsSanitizedInputUrl() throws IOException {
        String source = readSource("context/TranscodeContext.java");

        assertThat(source).contains("SafeUriFormatter.forLog(param.getUrl())");
        assertThat(source).doesNotContain("输入流不包含视频轨：{}\", param.getUrl()");
    }

    @Test
    void snapshotCleanupReleasesEveryNativeResourceOnceInSafeOrder() throws Exception {
        AVCodecContext decoder = new AVCodecContext(pointerAt(1L));
        AVCodecContext encoder = new AVCodecContext(pointerAt(2L));
        AVFormatContext input = new AVFormatContext(pointerAt(3L));
        AVFormatContext output = new AVFormatContext(pointerAt(4L));
        AVIOContext outputIo = new AVIOContext(pointerAt(5L));
        AVFrame frame = new AVFrame(pointerAt(6L));
        AVPacket sourcePacket = new AVPacket(pointerAt(7L));
        AVPacket outputPacket = new AVPacket(pointerAt(8L));
        RecordingResourceReleaser releaser = new RecordingResourceReleaser(encoder, outputPacket);
        SnapServiceImpl service = new SnapServiceImpl(
                null, new ZlmMediaProperties(), (url, path, timeout) -> { }, releaser);

        service.free("rtsp://user:secret@127.0.0.1/live", decoder, encoder,
                input, output, outputIo, frame, sourcePacket, outputPacket);

        assertThat(releaser.releases).containsExactly(
                "output-packet", "source-packet", "frame", "encoder", "decoder",
                "input-format", "output-io", "output-format");
    }

    private static String readSource(String relativePath) throws IOException {
        Path moduleDirectory = Path.of(System.getProperty("basedir"));
        return Files.readString(moduleDirectory.resolve("src/main/java/com/ss/zlm4j").resolve(relativePath));
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertThat(start).isNotNegative();
        assertThat(end).isGreaterThan(start);
        return source.substring(start, end);
    }

    private static int countOccurrences(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static Pointer pointerAt(long address) {
        return new TestPointer(address);
    }

    private static final class TestPointer extends Pointer {
        private TestPointer(long address) {
            this.address = address;
        }
    }

    private static final class RecordingResourceReleaser
            implements SnapServiceImpl.NativeResourceReleaser {
        private final List<String> releases = new ArrayList<>();
        private final AVCodecContext encoder;
        private final AVPacket outputPacket;

        private RecordingResourceReleaser(AVCodecContext encoder, AVPacket outputPacket) {
            this.encoder = encoder;
            this.outputPacket = outputPacket;
        }

        @Override
        public void freePacket(AVPacket packet) {
            releases.add(packet == outputPacket ? "output-packet" : "source-packet");
        }

        @Override
        public void freeFrame(AVFrame frame) {
            releases.add("frame");
        }

        @Override
        public void freeCodecContext(AVCodecContext codecContext) {
            releases.add(codecContext == encoder ? "encoder" : "decoder");
        }

        @Override
        public void closeInput(AVFormatContext inputContext) {
            releases.add("input-format");
        }

        @Override
        public void closeOutput(AVIOContext outputContext) {
            releases.add("output-io");
        }

        @Override
        public void freeFormatContext(AVFormatContext formatContext) {
            releases.add("output-format");
        }
    }
}
