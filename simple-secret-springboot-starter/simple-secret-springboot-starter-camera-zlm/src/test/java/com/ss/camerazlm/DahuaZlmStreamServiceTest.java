package com.ss.camerazlm;

import com.ss.ics.dahua.DahuaStreamCallback;
import com.ss.ics.dahua.DahuaStreamFrame;
import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.PlayDomain;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 大华 Camera SDK 到 ZLM 转推服务的资源生命周期测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
class DahuaZlmStreamServiceTest {

    @Test
    void forwardsFramesInOrderAndClosesSourceBeforePublisher() throws Exception {
        Fixture fixture = new Fixture(8);
        DahuaZlmStreamSession session = fixture.start("camera-01");
        fixture.publisher.expectPushes(2);

        fixture.source.emit(frame((byte) 0x65));
        fixture.source.emit(frame((byte) 0x41));

        assertThat(fixture.publisher.awaitPushes()).isTrue();
        session.close();
        assertThat(fixture.publisher.frames).containsExactly(
                new byte[]{0, 0, 0, 1, 0x65},
                new byte[]{0, 0, 0, 1, 0x41});
        assertThat(fixture.events).containsSubsequence("source:stop", "publisher:stop");
        assertThat(session.isClosed()).isTrue();
    }

    @Test
    void rejectsDuplicateStreamWithoutStoppingExistingPublisher() {
        Fixture fixture = new Fixture(8);
        DahuaZlmStreamSession first = fixture.start("camera-01");

        assertThatThrownBy(() -> fixture.start("camera-01"))
                .isInstanceOf(CameraZlmException.class)
                .hasMessageContaining("already active");
        assertThat(fixture.publisher.stopCalls).isZero();

        first.close();
        fixture.start("camera-01").close();
    }

    @Test
    void recordsQueueOverflowAndAutomaticallyStopsResources() throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.publisher.blockPush = true;
        DahuaZlmStreamSession session = fixture.start("camera-01");

        fixture.source.emit(frame((byte) 0x65));
        assertThat(fixture.publisher.pushStarted.await(1, TimeUnit.SECONDS)).isTrue();
        fixture.source.emit(frame((byte) 0x41));
        fixture.source.emit(frame((byte) 0x01));
        fixture.publisher.releasePush.countDown();

        assertThat(fixture.source.stopped.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(fixture.publisher.stopped.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(session.failure()).hasValueSatisfying(failure ->
                assertThat(failure.getMessage()).contains("queue is full"));
    }

    @Test
    void rejectsFrameThatExceedsConfiguredByteLimit() throws Exception {
        Fixture fixture = new Fixture(8, 4, 32);
        DahuaZlmStreamSession session = fixture.start("camera-01");

        fixture.source.emit(frame((byte) 0x65));

        assertThat(fixture.source.stopped.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(session.failure()).hasValueSatisfying(failure ->
                assertThat(failure.getMessage()).contains("frame exceeds byte limit"));
    }

    @Test
    void stopsWhenBufferedBytesExceedBudgetBeforeFrameQueueIsFull() throws Exception {
        Fixture fixture = new Fixture(8, 8, 8);
        fixture.publisher.blockPush = true;
        DahuaZlmStreamSession session = fixture.start("camera-01");

        fixture.source.emit(frame((byte) 0x65));
        assertThat(fixture.publisher.pushStarted.await(1, TimeUnit.SECONDS)).isTrue();
        fixture.source.emit(frame((byte) 0x41));
        fixture.publisher.releasePush.countDown();

        assertThat(fixture.source.stopped.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(session.failure()).hasValueSatisfying(failure ->
                assertThat(failure.getMessage()).contains("byte budget"));
    }

    @Test
    void countsPublisherRetainedBytesAgainstBudget() throws Exception {
        Fixture fixture = new Fixture(8, 8, 8);
        fixture.publisher.retainedBytes = 5;
        DahuaZlmStreamSession session = fixture.start("camera-01");

        fixture.source.emit(frame((byte) 0x65));
        assertThat(fixture.publisher.pushFinished.await(1, TimeUnit.SECONDS)).isTrue();
        fixture.source.emit(frame((byte) 0x41));

        assertThat(fixture.source.stopped.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(session.failure()).hasValueSatisfying(failure ->
                assertThat(failure.getMessage()).contains("byte budget"));
    }

    @Test
    void rollsBackReservationAndPublisherWhenSourceStartFails() {
        Fixture fixture = new Fixture(8);
        fixture.source.startFailure = new IllegalStateException("source failed");

        assertThatThrownBy(() -> fixture.start("camera-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("source failed");
        assertThat(fixture.publisher.stopCalls).isOne();

        fixture.source.startFailure = null;
        fixture.start("camera-01").close();
    }

    @Test
    void publisherFailureStopsSourceAndRemainsObservable() throws Exception {
        Fixture fixture = new Fixture(8);
        fixture.publisher.pushFailure = new IllegalStateException("publisher failed");
        DahuaZlmStreamSession session = fixture.start("camera-01");

        fixture.source.emit(frame((byte) 0x65));

        assertThat(fixture.source.stopped.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(fixture.publisher.stopped.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(session.failure()).hasValueSatisfying(failure ->
                assertThat(failure).hasMessage("publisher failed"));
    }

    @Test
    void rollsBackAndThrowsWhenFirstFrameFailsBeforeSourceStartReturns() {
        Fixture fixture = new Fixture(8);
        fixture.publisher.pushFailure = new IllegalStateException("publisher failed during startup");
        fixture.source.emitOnStart = frame((byte) 0x65);
        fixture.source.waitForPushOnStart = fixture.publisher.pushFinished;

        assertThatThrownBy(() -> fixture.start("camera-01"))
                .isInstanceOf(CameraZlmException.class)
                .hasMessageContaining("startup")
                .hasRootCauseMessage("publisher failed during startup");
        assertThat(fixture.publisher.stopCalls).isOne();
        assertThat(fixture.service.activeSessionCount()).isZero();
    }

    @Test
    void nativeLinkageFailureStopsResourcesAndRemainsObservable() throws Exception {
        Fixture fixture = new Fixture(8);
        fixture.publisher.linkageFailure = new UnsatisfiedLinkError("mk_api unavailable");
        DahuaZlmStreamSession session = fixture.start("camera-01");

        fixture.source.emit(frame((byte) 0x65));

        assertThat(fixture.source.stopped.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(fixture.publisher.stopped.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(session.failure()).hasValueSatisfying(failure ->
                assertThat(failure)
                        .isInstanceOf(CameraZlmException.class)
                        .hasCauseInstanceOf(UnsatisfiedLinkError.class));
    }

    @Test
    void nativeSourceStartFailureRollsBackReservationAndPublisher() {
        Fixture fixture = new Fixture(8);
        fixture.source.startLinkageFailure = new UnsatisfiedLinkError("dhnetsdk unavailable");

        assertThatThrownBy(() -> fixture.start("camera-01"))
                .isInstanceOf(CameraZlmException.class)
                .hasCauseInstanceOf(UnsatisfiedLinkError.class);
        assertThat(fixture.publisher.stopCalls).isOne();
        assertThat(fixture.service.activeSessionCount()).isZero();
    }

    @Test
    void nativeSourceStopFailureRemainsObservableAndCanBeRetried() {
        Fixture fixture = new Fixture(8);
        fixture.source.stopLinkageFailures = 1;
        DahuaZlmStreamSession session = fixture.start("camera-01");

        assertThatThrownBy(session::close)
                .isInstanceOf(CameraZlmException.class)
                .hasCauseInstanceOf(UnsatisfiedLinkError.class);
        assertThat(fixture.publisher.stopCalls).isZero();
        assertThat(session.isClosed()).isFalse();

        session.close();
        assertThat(fixture.publisher.stopCalls).isOne();
        assertThat(session.isClosed()).isTrue();
    }

    @Test
    void nativePublisherStopFailureRemainsObservableAndCanBeRetried() {
        Fixture fixture = new Fixture(8);
        fixture.publisher.stopLinkageFailures = 1;
        DahuaZlmStreamSession session = fixture.start("camera-01");

        assertThatThrownBy(session::close)
                .isInstanceOf(CameraZlmException.class)
                .hasCauseInstanceOf(UnsatisfiedLinkError.class);
        assertThat(session.isClosed()).isFalse();

        session.close();
        assertThat(fixture.publisher.stopCalls).isEqualTo(2);
        assertThat(session.isClosed()).isTrue();
    }

    @Test
    void serviceCloseClosesEveryActiveSessionAndRejectsNewStreams() {
        Fixture fixture = new Fixture(8);
        fixture.start("camera-01");

        fixture.service.close();

        assertThat(fixture.events).containsSubsequence("source:stop", "publisher:stop");
        assertThatThrownBy(() -> fixture.start("camera-02"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void retriesSourceStopBeforeStoppingPublisher() {
        Fixture fixture = new Fixture(8);
        fixture.source.stopFailures = 1;
        DahuaZlmStreamSession session = fixture.start("camera-01");

        assertThatThrownBy(session::close)
                .isInstanceOf(CameraZlmException.class)
                .hasMessageContaining("camera stream");
        assertThat(fixture.publisher.stopCalls).isZero();
        assertThat(session.isClosed()).isFalse();

        session.close();
        assertThat(fixture.publisher.stopCalls).isOne();
        assertThat(session.isClosed()).isTrue();
    }

    private static DeviceDomain device() {
        return new DeviceDomain().setIp("192.0.2.10").setPort("37777")
                .setUsername("user").setPassword("secret").setChannel("1");
    }

    private static PlayDomain play() {
        return new PlayDomain().setTakeStreamParam(
                new PlayDomain.TakeStreamParam().setStreamType(0));
    }

    private static DahuaStreamFrame frame(byte nalType) {
        return new DahuaStreamFrame(
                new byte[]{0, 0, 0, 1, nalType}, 1L, 1L, 1, 1);
    }

    /** 单测依赖组合。 */
    private static final class Fixture {
        private final List<String> events = new ArrayList<>();
        private final FakeSource source = new FakeSource(events);
        private final FakePublisher publisher = new FakePublisher(events);
        private final DahuaZlmStreamService service;

        private Fixture(int queueCapacity) {
            service = new DahuaZlmStreamService(
                    source, publisher, queueCapacity, Duration.ofSeconds(1));
        }

        private Fixture(int queueCapacity, int maxFrameBytes, long maxBufferedBytes) {
            service = new DahuaZlmStreamService(
                    source, publisher, queueCapacity, maxFrameBytes,
                    maxBufferedBytes, Duration.ofSeconds(1));
        }

        private DahuaZlmStreamSession start(String stream) {
            return service.start(device(), play(), "live", stream);
        }
    }

    /** 可控的大华取流替身。 */
    private static final class FakeSource implements DahuaStreamSource {
        private final List<String> events;
        private final CountDownLatch stopped = new CountDownLatch(1);
        private DahuaStreamCallback callback;
        private RuntimeException startFailure;
        private LinkageError startLinkageFailure;
        private DahuaStreamFrame emitOnStart;
        private CountDownLatch waitForPushOnStart;
        private int stopFailures;
        private int stopLinkageFailures;

        private FakeSource(List<String> events) {
            this.events = events;
        }

        @Override
        public AutoCloseable start(
                DeviceDomain device, PlayDomain play, DahuaStreamCallback streamCallback) {
            if (startFailure != null) {
                throw startFailure;
            }
            if (startLinkageFailure != null) {
                throw startLinkageFailure;
            }
            callback = streamCallback;
            emitDuringStart(streamCallback);
            return () -> {
                if (stopFailures > 0) {
                    stopFailures--;
                    throw new IllegalStateException("source stop failed");
                }
                if (stopLinkageFailures > 0) {
                    stopLinkageFailures--;
                    throw new UnsatisfiedLinkError("dhnetsdk stop unavailable");
                }
                events.add("source:stop");
                stopped.countDown();
            };
        }

        private void emitDuringStart(DahuaStreamCallback streamCallback) {
            if (emitOnStart == null) {
                return;
            }
            streamCallback.onFrame(emitOnStart);
            try {
                if (waitForPushOnStart != null
                        && !waitForPushOnStart.await(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("publisher was not called during source startup");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for startup frame", exception);
            }
        }

        private void emit(DahuaStreamFrame frame) {
            callback.onFrame(frame);
        }
    }

    /** 可控的 H.264 publisher 替身。 */
    private static final class FakePublisher implements H264StreamPublisher {
        private final List<String> events;
        private final List<byte[]> frames = new ArrayList<>();
        private final CountDownLatch pushStarted = new CountDownLatch(1);
        private final CountDownLatch releasePush = new CountDownLatch(1);
        private final CountDownLatch stopped = new CountDownLatch(1);
        private final CountDownLatch pushFinished = new CountDownLatch(1);
        private volatile CountDownLatch expectedPushes = new CountDownLatch(0);
        private RuntimeException pushFailure;
        private LinkageError linkageFailure;
        private boolean blockPush;
        private int stopCalls;
        private int stopLinkageFailures;
        private int retainedBytes;

        private FakePublisher(List<String> events) {
            this.events = events;
        }

        @Override
        public int push(String app, String stream, byte[] data) throws InterruptedException {
            pushStarted.countDown();
            try {
                if (blockPush) {
                    releasePush.await();
                }
                if (pushFailure != null) {
                    throw pushFailure;
                }
                if (linkageFailure != null) {
                    throw linkageFailure;
                }
                frames.add(data);
                expectedPushes.countDown();
                return retainedBytes;
            } finally {
                pushFinished.countDown();
            }
        }

        @Override
        public void stop(String app, String stream) {
            stopCalls++;
            if (stopLinkageFailures > 0) {
                stopLinkageFailures--;
                throw new UnsatisfiedLinkError("mk_api stop unavailable");
            }
            events.add("publisher:stop");
            stopped.countDown();
        }

        private void expectPushes(int count) {
            expectedPushes = new CountDownLatch(count);
        }

        private boolean awaitPushes() throws InterruptedException {
            return expectedPushes.await(1, TimeUnit.SECONDS);
        }
    }
}
