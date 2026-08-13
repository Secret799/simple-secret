package com.ss.application.pushstream.process;

import com.ss.application.pushstream.scan.MediaFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FFmpeg 命令工厂测试。
 *
 * @author junpzx
 * @since 2026-08-12
 */
class FfmpegCommandFactoryTest {

    @Test
    void shouldKeepPathsAndUrlsAsIndependentArguments() {
        MediaFile mediaFile = new MediaFile(
                Path.of("/media/folder with space/demo.mp4"),
                "demo.mp4", "demo-a1b2c3d4", 10L, Instant.EPOCH);
        FfmpegCommandFactory factory = new FfmpegCommandFactory(
                "/opt/ffmpeg custom", "127.0.0.1", 7554, "publish");

        List<String> command = factory.create(mediaFile);

        assertThat(command.get(0)).isEqualTo("/opt/ffmpeg custom");
        assertThat(command).containsSubsequence(
                "-i", "/media/folder with space/demo.mp4", "-c", "copy");
        assertThat(command.get(command.size() - 1))
                .isEqualTo("rtsp://127.0.0.1:7554/publish/demo-a1b2c3d4");
        assertThat(command).noneMatch(argument -> argument.contains("nohup")
                || argument.contains(" > ") || argument.equals("&"));
    }

    @Test
    void shouldBracketIpv6RtspHost() {
        MediaFile mediaFile = new MediaFile(Path.of("/media/demo.mp4"),
                "demo.mp4", "demo-a1b2c3d4", 10L, Instant.EPOCH);
        FfmpegCommandFactory factory = new FfmpegCommandFactory(
                "ffmpeg", "::1", 7554, "publish");

        List<String> command = factory.create(mediaFile);

        assertThat(command.get(command.size() - 1))
                .isEqualTo("rtsp://[::1]:7554/publish/demo-a1b2c3d4");
    }
}
