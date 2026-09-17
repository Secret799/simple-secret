package com.ss.consumer.gb28181zlm;

import com.ss.gb28181.Gb28181Server;
import com.ss.gb28181.GbGuardCommand;
import com.ss.gb28181.GbRecordControlCommand;
import com.ss.gb28181.GbTeleBootCommand;
import com.ss.gb28181.GbDownloadRequest;
import com.ss.gb28181.GbPlaySession;
import com.ss.gb28181.GbPlaybackControl;
import com.ss.gb28181.GbPlaybackRange;
import com.ss.gb28181.GbRtpTarget;
import com.ss.gb28181.zlm.GbZlmPlayer;
import com.ss.gb28181.zlm.GbZlmPlaySession;
import com.ss.zlm4j.context.ZlmMediaContext;
import com.ss.zlm4j.service.IZlmMediaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GbZlmConsumerTest {
    @Test
    void exposesDeviceControlOperationsThroughPublishedPlugin() throws Exception {
        assertThat(GbTeleBootCommand.BOOT).isNotNull();
        assertThat(GbRecordControlCommand.RECORD).isNotNull();
        assertThat(GbGuardCommand.SET_GUARD).isNotNull();
        assertThat(Gb28181Server.class.getMethod("teleBoot", String.class)).isNotNull();
        assertThat(Gb28181Server.class.getMethod("controlRecording", String.class, String.class,
                GbRecordControlCommand.class)).isNotNull();
        assertThat(Gb28181Server.class.getMethod("controlGuard", String.class, String.class,
                GbGuardCommand.class)).isNotNull();
    }
    @Test
    void exposesDownloadRequestAndSessionMetadata() throws Exception {
        Instant start = Instant.parse("2026-09-15T00:00:00Z");
        GbPlaybackRange range = new GbPlaybackRange(start, start.plusSeconds(3600));
        assertThat(GbDownloadRequest.normal(range).range()).isSameAs(range);
        assertThat(GbDownloadRequest.normal(range).speed()).isEqualTo(1);
        assertThat(new GbDownloadRequest(range, 128).speed()).isEqualTo(128);
        assertThatThrownBy(() -> GbDownloadRequest.normal(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GbDownloadRequest(range, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GbDownloadRequest(range, 129)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Gb28181Server.class.getMethod("download", String.class, String.class,
                GbRtpTarget.class, GbDownloadRequest.class).getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + GbPlaySession.class.getName() + ">");
        assertThat(GbPlaySession.class.getMethod("downloadRequest").getGenericReturnType().getTypeName())
                .isEqualTo(Optional.class.getName() + "<" + GbDownloadRequest.class.getName() + ">");
        assertThat(GbPlaySession.class.getMethod("downloadFileSize").getReturnType()).isEqualTo(OptionalLong.class);
        assertThat(GbZlmPlayer.class.getMethod("download", String.class, String.class, GbDownloadRequest.class)
                .getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + GbZlmPlaySession.class.getName() + ">");
    }

    @Test
    void exposesPublicPlaybackAndMediaTypes() throws Exception {
        assertThat(GbZlmPlayer.class.getMethod("play", String.class, String.class)
                .getGenericReturnType().getTypeName()).contains(CompletableFuture.class.getName())
                .contains(GbZlmPlaySession.class.getName());
        assertThat(GbZlmPlaySession.class.getMethod("sipSession").getReturnType()).isEqualTo(GbPlaySession.class);
        assertThat(GbZlmPlayer.class.getConstructor(Gb28181Server.class, IZlmMediaService.class,
                String.class, GbRtpTarget.Transport.class, int.class)).isNotNull();
    }

    @Test
    void exposesHistoryPlaybackAndControlsThroughSipSession() throws Exception {
        Instant start = Instant.parse("2026-09-15T00:00:00Z");
        assertThat(new GbPlaybackRange(start, start.plusSeconds(3600)).startTime()).isEqualTo(start);
        assertThat(GbPlaybackControl.pause().type()).isEqualTo(GbPlaybackControl.Type.PAUSE);
        assertThat(GbPlaybackControl.resume().type()).isEqualTo(GbPlaybackControl.Type.RESUME);
        assertThat(GbPlaybackControl.seek(Duration.ofSeconds(30)).position()).isEqualTo(Duration.ofSeconds(30));
        assertThat(GbPlaybackControl.speed(4.0).scale()).isEqualTo(4.0);
        assertThat(GbZlmPlayer.class.getMethod("playback", String.class, String.class, GbPlaybackRange.class)
                .getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + GbZlmPlaySession.class.getName() + ">");
        assertThat(GbPlaySession.class.getMethod("playbackRange").getGenericReturnType().getTypeName())
                .isEqualTo(Optional.class.getName() + "<" + GbPlaybackRange.class.getName() + ">");
        assertThat(GbPlaySession.class.getMethod("control", GbPlaybackControl.class).getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + Void.class.getName() + ">");
    }

    @Test
    void staysDisabledAndDoesNotLoadNative() {
        new ApplicationContextRunner().withUserConfiguration(ConsumerApplication.class).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(GbZlmPlayer.class)
                    .doesNotHaveBean(Gb28181Server.class).doesNotHaveBean(ZlmMediaContext.class);
        });
    }

    @Test
    void publishedJarContainsAutoConfigurationAndPropertyMetadata() throws Exception {
        assertThat(matchingResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
                "com.ss.gb28181.zlm.autoconfigure.SimpleSecretGbZlmAutoConfiguration")).isNotEmpty();
        assertThat(matchingResource("META-INF/spring-configuration-metadata.json", "simple-secret.gb28181-zlm.enabled"))
                .contains("simple-secret.gb28181-zlm.advertised-address")
                .contains("simple-secret.gb28181-zlm.transport")
                .contains("simple-secret.gb28181-zlm.max-sessions");
    }

    private static String matchingResource(String name, String marker) throws Exception {
        var resources = GbZlmConsumerTest.class.getClassLoader().getResources(name);
        while (resources.hasMoreElements()) {
            try (InputStream input = resources.nextElement().openStream()) {
                String contents = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                if (contents.contains(marker)) return contents;
            }
        }
        throw new AssertionError("Missing published resource: " + marker);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication { }
}
