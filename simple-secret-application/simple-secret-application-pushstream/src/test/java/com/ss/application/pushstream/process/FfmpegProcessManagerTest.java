package com.ss.application.pushstream.process;

import com.ss.application.pushstream.scan.MediaFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FFmpeg 进程管理器测试。
 *
 * @author junpzx
 * @since 2026-08-12
 */
class FfmpegProcessManagerTest {

    @Test
    void shouldEnforceCapacityRestartAndStopProcesses() {
        List<FakeProcess> processes = new ArrayList<>();
        ProcessLauncher launcher = command -> {
            FakeProcess process = new FakeProcess();
            processes.add(process);
            return process;
        };
        FfmpegProcessManager manager = new FfmpegProcessManager(
                mediaFile -> List.of("ffmpeg", mediaFile.path().toString()),
                launcher, 1, Duration.ofMillis(10));
        MediaFile first = mediaFile("first.mp4", "first");
        MediaFile second = mediaFile("second.mp4", "second");

        manager.start(first);

        assertThatThrownBy(() -> manager.start(second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maximum");

        manager.restart(first);
        assertThat(processes.get(0).destroyed).isTrue();
        assertThat(processes).hasSize(2);

        manager.stop(first.streamId());
        assertThat(processes.get(1).destroyed).isTrue();
        assertThat(manager.runningStreamIds()).isEmpty();
    }

    @Test
    void shouldDestroyAllProcessesOnClose() {
        List<FakeProcess> processes = new ArrayList<>();
        FfmpegProcessManager manager = new FfmpegProcessManager(
                mediaFile -> List.of("ffmpeg"),
                command -> {
                    FakeProcess process = new FakeProcess();
                    processes.add(process);
                    return process;
                }, 2, Duration.ofMillis(10));

        manager.start(mediaFile("first.mp4", "first"));
        manager.start(mediaFile("second.mp4", "second"));
        manager.close();

        assertThat(processes).allMatch(process -> process.destroyed);
        assertThat(manager.runningStreamIds()).isEmpty();
    }

    @Test
    void shouldRejectStartingProcessesAfterClose() {
        List<FakeProcess> processes = new ArrayList<>();
        FfmpegProcessManager manager = new FfmpegProcessManager(
                mediaFile -> List.of("ffmpeg"),
                command -> {
                    FakeProcess process = new FakeProcess();
                    processes.add(process);
                    return process;
                }, 1, Duration.ofMillis(10));

        manager.close();

        assertThatThrownBy(() -> manager.start(mediaFile("first.mp4", "first")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
        assertThat(processes).isEmpty();
    }

    @Test
    void shouldRemoveExitedProcessesBeforeApplyingCapacityLimit() {
        List<FakeProcess> processes = new ArrayList<>();
        FfmpegProcessManager manager = new FfmpegProcessManager(
                mediaFile -> List.of("ffmpeg"),
                command -> {
                    FakeProcess process = new FakeProcess();
                    processes.add(process);
                    return process;
                }, 1, Duration.ofMillis(10));

        manager.start(mediaFile("first.mp4", "first"));
        processes.get(0).alive = false;

        manager.start(mediaFile("second.mp4", "second"));

        assertThat(manager.runningStreamIds()).containsExactly("second");
    }

    @Test
    void shouldBackOffAfterProcessExitAndExposeExitCode() {
        MutableClock clock = new MutableClock();
        List<FakeProcess> processes = new ArrayList<>();
        FfmpegProcessManager manager = new FfmpegProcessManager(
                mediaFile -> List.of("ffmpeg"), command -> {
                    FakeProcess process = new FakeProcess();
                    processes.add(process);
                    return process;
                }, 1, Duration.ofMillis(10), clock);
        MediaFile mediaFile = mediaFile("first.mp4", "first");
        manager.start(mediaFile);
        processes.get(0).alive = false;
        processes.get(0).exitCode = 23;

        assertThat(manager.runningStreamIds()).isEmpty();
        assertThat(manager.failureMessages()).containsEntry(
                "first", "FFmpeg exited with code 23; restart delayed");
        assertThatThrownBy(() -> manager.start(mediaFile))
                .isInstanceOf(FfmpegProcessException.class)
                .hasMessageContaining("backoff");
        assertThat(processes).hasSize(1);

        clock.advance(Duration.ofSeconds(5));
        manager.start(mediaFile);
        assertThat(processes).hasSize(2);
        processes.get(1).alive = false;
        processes.get(1).exitCode = 24;
        assertThat(manager.runningStreamIds()).isEmpty();
        clock.advance(Duration.ofSeconds(5));
        assertThatThrownBy(() -> manager.start(mediaFile))
                .isInstanceOf(FfmpegProcessException.class)
                .hasMessageContaining("backoff");
        clock.advance(Duration.ofSeconds(5));
        manager.start(mediaFile);
        assertThat(processes).hasSize(3);
    }

    @Test
    void shouldBackOffAfterProcessLaunchFailure() {
        MutableClock clock = new MutableClock();
        int[] attempts = {0};
        FfmpegProcessManager manager = new FfmpegProcessManager(
                mediaFile -> List.of("ffmpeg"), command -> {
                    attempts[0]++;
                    throw new IOException("binary is unavailable");
                }, 1, Duration.ofMillis(10), clock);
        MediaFile mediaFile = mediaFile("first.mp4", "first");

        assertThatThrownBy(() -> manager.start(mediaFile))
                .isInstanceOf(FfmpegProcessException.class)
                .hasMessageContaining("Unable to start");
        assertThatThrownBy(() -> manager.start(mediaFile))
                .isInstanceOf(FfmpegProcessException.class)
                .hasMessageContaining("backoff");
        assertThat(attempts[0]).isOne();

        clock.advance(Duration.ofSeconds(5));
        assertThatThrownBy(() -> manager.start(mediaFile))
                .isInstanceOf(FfmpegProcessException.class)
                .hasMessageContaining("Unable to start");
        assertThat(attempts[0]).isEqualTo(2);
    }

    @Test
    void shouldResetFailureCountAfterStableRun() {
        MutableClock clock = new MutableClock();
        List<FakeProcess> processes = new ArrayList<>();
        FfmpegProcessManager manager = new FfmpegProcessManager(
                mediaFile -> List.of("ffmpeg"), command -> {
                    FakeProcess process = new FakeProcess();
                    processes.add(process);
                    return process;
                }, 1, Duration.ofMillis(10), clock);
        MediaFile mediaFile = mediaFile("first.mp4", "first");

        manager.start(mediaFile);
        processes.get(0).alive = false;
        manager.runningStreamIds();
        clock.advance(Duration.ofSeconds(5));
        manager.start(mediaFile);

        clock.advance(Duration.ofMinutes(10));
        processes.get(1).alive = false;
        manager.runningStreamIds();
        clock.advance(Duration.ofSeconds(5));

        manager.start(mediaFile);
        assertThat(processes).hasSize(3);
    }

    @Test
    void shouldShareOneShutdownDeadlineAcrossAllProcesses() {
        List<StubbornProcess> processes = new ArrayList<>();
        FfmpegProcessManager manager = new FfmpegProcessManager(
                mediaFile -> List.of("ffmpeg"), command -> {
                    StubbornProcess process = new StubbornProcess();
                    processes.add(process);
                    return process;
                }, 2, Duration.ofMillis(10));
        manager.start(mediaFile("first.mp4", "first"));
        manager.start(mediaFile("second.mp4", "second"));

        manager.close();

        assertThat(processes).allMatch(process -> process.forciblyDestroyed);
        assertThat(processes).extracting(process -> process.requestedWaitMillis)
                .allMatch(timeout -> timeout <= 10L);
        assertThat(processes.stream().mapToLong(process -> process.requestedWaitMillis).sum())
                .isLessThanOrEqualTo(10L);
    }

    private MediaFile mediaFile(String name, String streamId) {
        return new MediaFile(Path.of("/media", name), name, streamId, 1L, Instant.EPOCH);
    }

    private static class FakeProcess extends Process {
        protected boolean alive = true;
        private boolean destroyed;
        private int exitCode;

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            alive = false;
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return !alive;
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException();
            }
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyed = true;
            alive = false;
        }

        @Override
        public Process destroyForcibly() {
            destroy();
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }

    private static final class StubbornProcess extends FakeProcess {
        private long requestedWaitMillis;
        private boolean forciblyDestroyed;

        @Override
        public void destroy() {
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            requestedWaitMillis = unit.toMillis(timeout);
            java.util.concurrent.locks.LockSupport.parkNanos(unit.toNanos(timeout));
            return false;
        }

        @Override
        public Process destroyForcibly() {
            forciblyDestroyed = true;
            alive = false;
            return this;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.EPOCH;

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
