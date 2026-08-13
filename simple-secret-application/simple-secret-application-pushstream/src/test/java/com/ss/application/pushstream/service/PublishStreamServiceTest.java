package com.ss.application.pushstream.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ss.application.pushstream.process.ManagedStreamProcesses;
import com.ss.application.pushstream.scan.MediaFile;
import com.ss.application.pushstream.scan.MediaFileScanner;
import com.ss.application.pushstream.status.PublishStreamState;
import com.ss.application.pushstream.status.PublishStreamStatusView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 推流应用服务测试。
 *
 * @author junpzx
 * @since 2026-08-12
 */
class PublishStreamServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldRetryFailedStartOnNextSynchronization() throws IOException {
        Files.writeString(temporaryDirectory.resolve("demo.mp4"), "demo");
        RecordingProcesses processes = new RecordingProcesses();
        processes.failNextStart = true;
        PublishStreamService service = createService(processes, Set.of());

        service.synchronize();
        assertThat(service.status().getStreams()).singleElement()
                .extracting(status -> status.getState())
                .isEqualTo(PublishStreamState.FAILED);

        service.synchronize();

        assertThat(processes.startCount).isEqualTo(2);
        assertThat(service.status().getStreams()).singleElement()
                .extracting(status -> status.getState())
                .isEqualTo(PublishStreamState.STARTING);
    }

    @Test
    void shouldRestartUnexpectedlyExitedProcessAndExposeNoAbsolutePath() throws Exception {
        Files.writeString(temporaryDirectory.resolve("secret-location.mp4"), "demo");
        RecordingProcesses processes = new RecordingProcesses();
        PublishStreamService service = createService(processes, Set.of());
        service.synchronize();
        processes.runningStreamIds.clear();

        service.synchronize();

        PublishStreamStatusView statusView = service.status();
        assertThat(processes.startCount).isEqualTo(2);
        assertStatusDoesNotLeakPath(statusView);
    }

    @Test
    void shouldExposeMediaServerQueryFailureWithoutLeakingDetails() throws Exception {
        Files.writeString(temporaryDirectory.resolve("demo.mp4"), "demo");
        RecordingProcesses processes = new RecordingProcesses();
        PublishStreamService service = new PublishStreamService(
                new MediaFileScanner(temporaryDirectory, Set.of("mp4"), false),
                processes, app -> {
                    throw new IllegalStateException("secret native detail");
                }, "publish", Clock.systemUTC());
        service.synchronize();

        PublishStreamStatusView status = service.status();
        String json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(status);

        assertThat(status.isMediaServerReachable()).isFalse();
        assertThat(json).contains("\"mediaServerReachable\":false");
        assertThat(json).doesNotContain("secret native detail");
    }

    private PublishStreamService createService(RecordingProcesses processes, Set<String> onlineStreams) {
        MediaFileScanner scanner = new MediaFileScanner(temporaryDirectory, Set.of("mp4"), false);
        Clock clock = Clock.fixed(Instant.parse("2026-08-12T08:00:00Z"), ZoneOffset.UTC);
        return new PublishStreamService(scanner, processes, app -> onlineStreams, "publish", clock);
    }

    private void assertStatusDoesNotLeakPath(PublishStreamStatusView statusView)
            throws JsonProcessingException {
        String json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(statusView);
        assertThat(json).contains("secret-location.mp4");
        assertThat(json).doesNotContain(temporaryDirectory.toString());
        assertThat(json).doesNotContain("path");
    }

    private static final class RecordingProcesses implements ManagedStreamProcesses {
        private final Set<String> runningStreamIds = new LinkedHashSet<>();
        private int startCount;
        private boolean failNextStart;

        @Override
        public void start(MediaFile mediaFile) {
            startCount++;
            if (failNextStart) {
                failNextStart = false;
                throw new IllegalStateException("expected failure");
            }
            runningStreamIds.add(mediaFile.streamId());
        }

        @Override
        public void restart(MediaFile mediaFile) {
            stop(mediaFile.streamId());
            start(mediaFile);
        }

        @Override
        public void stop(String streamId) {
            runningStreamIds.remove(streamId);
        }

        @Override
        public Set<String> runningStreamIds() {
            return Set.copyOf(runningStreamIds);
        }

        @Override
        public void close() {
            runningStreamIds.clear();
        }
    }
}
