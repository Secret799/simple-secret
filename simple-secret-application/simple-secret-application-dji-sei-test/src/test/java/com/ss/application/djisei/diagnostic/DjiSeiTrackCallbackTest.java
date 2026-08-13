package com.ss.application.djisei.diagnostic;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ss.application.djisei.config.DjiSeiProperties;
import com.ss.application.djisei.parser.H26xSeiParser;
import com.ss.easymedia.callback.TrackDelegateCallback.TackDelegateInfo;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.domain.TrackDomain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.fail;

/**
 * DJI RTMP SEI 轨道诊断回调测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
class DjiSeiTrackCallbackTest {

    /** 被测类日志记录器。 */
    private Logger logger;

    /** 内存日志收集器。 */
    private ListAppender<ILoggingEvent> appender;

    /** 可控测试时钟。 */
    private MutableClock clock;

    /** 被测回调。 */
    private DjiSeiTrackCallback callback;

    /** 合法 RTMP 媒体源。 */
    private MediaSourceDomain source;

    /** 合法 H.264 视频轨道。 */
    private TrackDomain videoTrack;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(DjiSeiTrackCallback.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        clock = new MutableClock();
        DjiSeiProperties properties = new DjiSeiProperties();
        callback = new DjiSeiTrackCallback(new H26xSeiParser(), properties, clock);
        source = source("rtmp", "live");
        videoTrack = videoTrack("H264");
        callback.onMediaSourceRegistered(source);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void shouldSupportOnlyRtmpSchema() {
        assertThat(callback.supportedSchemas()).containsExactly("rtmp");
    }

    @Test
    void shouldLogSeiAndDeregisterSummaryForAllowedRtmpVideo() {
        callback.onMediaSourceRegistered(source);
        callback.callback(source, videoTrack, frame(frameWithH264UserData()));
        callback.onMediaSourceDeregistered(source);

        assertThat(messages()).anyMatch(message -> message.contains("SEI detected")
                && message.contains("stream=dock-01")
                && message.contains("payloadType=5")
                && message.contains("payloadBytes=19"));
        assertThat(messages()).anyMatch(message -> message.contains("stream summary")
                && message.contains("videoFrames=1")
                && message.contains("seiNalUnits=1")
                && message.contains("seiMessages=1")
                && message.contains("malformedMessages=0"));
    }

    @Test
    void shouldIgnoreWrongSchemaAppAudioAndUnsupportedCodec() {
        callback.callback(source("ts", "live"), videoTrack, frame(frameWithH264UserData()));
        callback.callback(source("rtmp", "other"), videoTrack, frame(frameWithH264UserData()));
        callback.callback(source, audioTrack(), frame(frameWithH264UserData()));
        callback.callback(source, videoTrack("vp9"), frame(frameWithH264UserData()));

        assertThat(messages()).noneMatch(message -> message.contains("SEI detected"));
    }

    @Test
    void shouldGuardNullInputsAndNullFrameData() {
        assertThatCode(() -> {
            callback.callback(null, videoTrack, frame(frameWithH264UserData()));
            callback.callback(source, null, frame(frameWithH264UserData()));
            callback.callback(source, videoTrack, null);
            callback.callback(source, videoTrack, frame(null));
        }).doesNotThrowAnyException();

        assertThat(messages()).noneMatch(message -> message.contains("SEI detected"));
    }

    @Test
    void shouldDistinguishNoSeiFromMalformedSeiInPeriodicSummary() {
        callback.onMediaSourceRegistered(source);
        callback.callback(source, videoTrack, frame(regularH264Frame()));
        callback.callback(source, videoTrack, frame(malformedH264SeiFrame()));
        clock.advance(Duration.ofSeconds(31));
        callback.callback(source, videoTrack, frame(regularH264Frame()));

        assertThat(messages()).anyMatch(message -> message.contains("periodic summary")
                && message.contains("videoFrames=3")
                && message.contains("seiMessages=0")
                && message.contains("malformedMessages=1"));
    }

    @Test
    void shouldLogOneWarningForEachMalformedFrame() {
        byte[] frame = concat(malformedH264SeiFrame(), malformedH264SeiFrame());

        callback.callback(source, videoTrack, frame(frame));
        callback.onMediaSourceDeregistered(source);

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .hasSize(1)
                .first()
                .extracting(ILoggingEvent::getFormattedMessage)
                .asString()
                .contains("issueCount=2");
        assertThat(messages()).anyMatch(message -> message.contains("stream summary")
                && message.contains("malformedMessages=2"));
    }

    @Test
    void shouldLogEveryParsedH265Message() {
        byte[] h265Frame = concat(
                annexB(bytes(78, 1, 4, 1, 'A', 0x80)),
                annexB(bytes(80, 1, 5, 1, 'B', 0x80)));

        callback.callback(source, videoTrack("hevc"), frame(h265Frame));

        assertThat(messages())
                .filteredOn(message -> message.contains("SEI detected"))
                .hasSize(2)
                .allMatch(message -> message.contains("codec=H265"));
    }

    @Test
    void shouldCapPerFrameMessageLogsWhileKeepingParsedSummaryCount() {
        DjiSeiProperties properties = new DjiSeiProperties();
        properties.setMaxSeiMessages(4);
        properties.setMaxMessageLogs(2);
        callback = new DjiSeiTrackCallback(new H26xSeiParser(), properties, clock);
        callback.onMediaSourceRegistered(source);
        byte[] messages = concat(bytes(1, 0), bytes(2, 0), bytes(3, 0), bytes(4, 0), bytes(0x80));

        callback.callback(source, videoTrack, frame(annexB(concat(bytes(0x06), messages))));
        callback.onMediaSourceDeregistered(source);

        assertThat(messages()).filteredOn(message -> message.contains("SEI detected")).hasSize(2);
        assertThat(streamSummaries()).singleElement().asString().contains("seiMessages=4");
    }

    @Test
    void shouldIncludeAdmittedFrameInFinalSummaryWithoutResurrectingClosedLifecycle() throws Exception {
        CountDownLatch parseStarted = new CountDownLatch(1);
        CountDownLatch allowParseToFinish = new CountDownLatch(1);
        AtomicInteger parseCount = new AtomicInteger();
        DjiSeiTrackCallback.FrameParser parser = blockingParser(
                parseStarted, allowParseToFinish, parseCount);
        callback = new DjiSeiTrackCallback(parser, new DjiSeiProperties(), clock);
        callback.onMediaSourceRegistered(source);
        AtomicReference<Thread> deregisterThread = new AtomicReference<>();
        ThreadPoolExecutor frameExecutor = singleThreadExecutor("dji-frame-test", new AtomicReference<>());
        ThreadPoolExecutor deregisterExecutor = singleThreadExecutor("dji-deregister-test", deregisterThread);

        Future<?> frameFuture = frameExecutor.submit(
                () -> callback.callback(source, videoTrack, frame(regularH264Frame())));
        Future<?> deregisterFuture = null;
        try {
            assertThat(parseStarted.await(5, TimeUnit.SECONDS)).isTrue();
            deregisterFuture = deregisterExecutor.submit(() -> callback.onMediaSourceDeregistered(source));
            awaitWaitingThread(deregisterThread, deregisterFuture);

            callback.callback(source, videoTrack, frame(regularH264Frame()));
            assertThat(parseCount).hasValue(1);
        } finally {
            allowParseToFinish.countDown();
            frameFuture.get(5, TimeUnit.SECONDS);
            if (deregisterFuture != null) {
                deregisterFuture.get(5, TimeUnit.SECONDS);
            }
            shutdown(frameExecutor);
            shutdown(deregisterExecutor);
        }

        callback.callback(source, videoTrack, frame(regularH264Frame()));
        assertThat(parseCount).hasValue(1);
        assertThat(streamSummaries()).singleElement().asString().contains("videoFrames=1");
    }

    @Test
    void shouldIgnoreStaleDeregisterAfterNewLifecycleRegistration() {
        MediaSourceDomain oldSource = source;
        MediaSourceDomain newSource = source("rtmp", "live");
        callback.onMediaSourceRegistered(newSource);
        appender.list.clear();

        callback.callback(newSource, videoTrack, frame(regularH264Frame()));
        callback.onMediaSourceDeregistered(oldSource);
        assertThat(streamSummaries()).isEmpty();

        callback.callback(newSource, videoTrack, frame(regularH264Frame()));
        callback.onMediaSourceDeregistered(newSource);
        assertThat(streamSummaries()).singleElement().asString().contains("videoFrames=2");
    }

    @Test
    void shouldReleaseLifecycleAdmissionWhenParserFails() {
        DjiSeiTrackCallback.FrameParser failingParser = (data, codec, maxFrameBytes, maxPayloadBytes) -> {
            throw new IllegalStateException("expected parser failure");
        };
        callback = new DjiSeiTrackCallback(failingParser, new DjiSeiProperties(), clock);
        callback.onMediaSourceRegistered(source);

        assertThatCode(() -> callback.callback(source, videoTrack, frame(regularH264Frame())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("expected parser failure");
        assertThatCode(() -> callback.onMediaSourceDeregistered(source)).doesNotThrowAnyException();
        assertThat(streamSummaries()).singleElement().asString().contains("videoFrames=0");
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<String> streamSummaries() {
        return messages().stream().filter(message -> message.contains("stream summary")).toList();
    }

    private DjiSeiTrackCallback.FrameParser blockingParser(
            CountDownLatch started, CountDownLatch finish, AtomicInteger parseCount) {
        H26xSeiParser delegate = new H26xSeiParser();
        return (data, codec, maxFrameBytes, maxPayloadBytes) -> {
            if (parseCount.incrementAndGet() == 1) {
                started.countDown();
                awaitLatch(finish);
            }
            return delegate.parse(data, codec, maxFrameBytes, maxPayloadBytes);
        };
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the parser test latch", exception);
        }
    }

    private ThreadPoolExecutor singleThreadExecutor(String threadName, AtomicReference<Thread> threadReference) {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), runnable -> {
            Thread thread = new Thread(runnable, threadName);
            threadReference.set(thread);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    private void awaitWaitingThread(AtomicReference<Thread> threadReference, Future<?> future) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        List<Thread.State> waitingStates = List.of(Thread.State.WAITING, Thread.State.TIMED_WAITING);
        while (System.nanoTime() < deadline) {
            Thread thread = threadReference.get();
            if (thread != null && waitingStates.contains(thread.getState())) {
                return;
            }
            if (future.isDone()) {
                fail("Deregistration completed before the admitted frame");
            }
            Thread.onSpinWait();
        }
        fail("Deregistration did not wait for the admitted frame");
    }

    private void shutdown(ThreadPoolExecutor executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    private MediaSourceDomain source(String schema, String app) {
        MediaSourceDomain mediaSource = new MediaSourceDomain();
        mediaSource.setSchema(schema);
        mediaSource.setApp(app);
        mediaSource.setStream("dock-01");
        return mediaSource;
    }

    private TrackDomain videoTrack(String codec) {
        TrackDomain track = new TrackDomain();
        track.setIsVideo(1);
        track.setCodecIdName(codec);
        return track;
    }

    private TrackDomain audioTrack() {
        TrackDomain track = videoTrack("H264");
        track.setIsVideo(0);
        return track;
    }

    private TackDelegateInfo frame(byte[] data) {
        return new TackDelegateInfo().setData(data).setPts(12L).setDts(10L);
    }

    private byte[] frameWithH264UserData() {
        byte[] uuid = bytes(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
        return annexB(concat(bytes(6, 5, 19), uuid, bytes('D', 'J', 'I', 0x80)));
    }

    private byte[] regularH264Frame() {
        return annexB(bytes(0x65, 1, 2, 3));
    }

    private byte[] malformedH264SeiFrame() {
        return annexB(bytes(6, 5, 10, 1, 2));
    }

    private byte[] annexB(byte[] nalUnit) {
        return concat(bytes(0, 0, 0, 1), nalUnit);
    }

    private byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }

    private byte[] concat(byte[]... arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }
        byte[] result = new byte[length];
        int position = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, position, array.length);
            position += array.length;
        }
        return result;
    }

    /**
     * 可手动推进的 UTC 测试时钟。
     *
     * @author junpzx
     * @since 2026-08-13
     */
    private static final class MutableClock extends Clock {

        /** 当前测试时间。 */
        private Instant instant = Instant.EPOCH;

        /**
         * 推进测试时间。
         *
         * @param duration 推进时长
         */
        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        /** @return UTC 时区 */
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /**
         * 返回同一 UTC 测试时钟。
         *
         * @param zone 目标时区
         * @return 当前测试时钟
         */
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        /** @return 当前测试时间 */
        @Override
        public Instant instant() {
            return instant;
        }
    }
}
