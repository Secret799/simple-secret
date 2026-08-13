package com.ss.easymedia.core.handler;

import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.aizuda.zlm4j.structure.MK_TRACK;
import com.aizuda.zlm4j.callback.IMKFrameOutCallBack;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.ss.easymedia.callback.TrackDelegateCallback;
import com.ss.easymedia.config.properties.EmsProperties;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.domain.TrackDomain;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EMS 通用媒体源轨道回调分发测试。
 *
 * @author JunPzx
 */
class EmsCommonStreamChangeHandlerTest {

    /** 被测试处理器日志器。 */
    private static final Logger HANDLER_LOGGER =
            (Logger) LoggerFactory.getLogger(EmsCommonStreamChangeHandler.class);

    /** 测试前的日志级别。 */
    private static Level previousLogLevel;

    @BeforeAll
    static void disableExpectedCallbackErrorLogging() {
        previousLogLevel = HANDLER_LOGGER.getLevel();
        HANDLER_LOGGER.setLevel(Level.OFF);
    }

    @AfterAll
    static void restoreHandlerLogging() {
        HANDLER_LOGGER.setLevel(previousLogLevel);
    }

    @Test
    @DisplayName("媒体源只匹配声明对应 schema 的轨道回调")
    void shouldResolveCallbacksByDeclaredSchema() {
        RecordingCallback rtmp = new RecordingCallback(Set.of("rtmp"));
        RecordingCallback ts = new RecordingCallback(Set.of("ts"));
        RecordingCallback both = new RecordingCallback(Set.of("RTMP", "ts"));
        EmsCommonStreamChangeHandler handler = new EmsCommonStreamChangeHandler(List.of(rtmp, ts, both));

        assertEquals(List.of(rtmp, both), handler.resolveCallbacks("rtmp"));
        assertEquals(List.of(ts, both), handler.resolveCallbacks("TS"));
    }

    @Test
    @DisplayName("默认轨道回调保持监听 TS 协议")
    void shouldKeepTsAsDefaultSchema() {
        TrackDelegateCallback callback = (mediaSource, track, frame) -> {
        };
        EmsCommonStreamChangeHandler handler = new EmsCommonStreamChangeHandler(List.of(callback));

        assertEquals(List.of(callback), handler.resolveCallbacks("ts"));
        assertTrue(handler.resolveCallbacks("rtmp").isEmpty());
    }

    @Test
    @DisplayName("空协议声明不会匹配任何媒体源")
    void shouldIgnoreCallbackWithoutSupportedSchemas() {
        RecordingCallback empty = new RecordingCallback(Set.of());
        RecordingCallback nullSchemas = new RecordingCallback(null);
        RecordingCallback blank = new RecordingCallback(Set.of(" "));
        EmsCommonStreamChangeHandler handler = new EmsCommonStreamChangeHandler(
                List.of(empty, nullSchemas, blank));

        assertTrue(handler.resolveCallbacks("rtmp").isEmpty());
        assertTrue(handler.resolveCallbacks(null).isEmpty());
        assertTrue(handler.resolveCallbacks(" ").isEmpty());
    }

    @Test
    @DisplayName("轨道去重键会隔离不同 schema 的同名媒体源")
    void shouldIncludeSchemaInMediaSourceKey() {
        EmsCommonStreamChangeHandler handler = new EmsCommonStreamChangeHandler(List.of());

        assertNotEquals(handler.key("rtmp", "live", "drone-001"),
                handler.key("ts", "live", "drone-001"));
        assertEquals(handler.key("rtmp", "live", "drone-001"),
                handler.key("RTMP", "live", "drone-001"));
    }

    @Test
    @DisplayName("注册注销和帧事件会隔离单个回调异常")
    void shouldDispatchLifecycleAndFrameEventsSafely() {
        RecordingCallback failing = new RecordingCallback(Set.of("rtmp"), true);
        RecordingCallback healthy = new RecordingCallback(Set.of("rtmp"));
        EmsCommonStreamChangeHandler handler = new EmsCommonStreamChangeHandler(List.of(failing, healthy));
        MediaSourceDomain source = createMediaSource("rtmp");
        List<TrackDelegateCallback> callbacks = handler.resolveCallbacks("rtmp");

        assertDoesNotThrow(() -> handler.notifyRegistered(source, callbacks));
        assertDoesNotThrow(() -> handler.dispatchFrame(source, new TrackDomain(),
                new TrackDelegateCallback.TackDelegateInfo(), callbacks));
        assertDoesNotThrow(() -> handler.notifyDeregistered(source, callbacks));
        assertEquals(1, healthy.registeredCount);
        assertEquals(1, healthy.frameCount);
        assertEquals(1, healthy.deregisteredCount);
    }

    @Test
    @DisplayName("注销事件按原生媒体源身份取回精确注册生命周期")
    void shouldResolveExactRegisteredLifecycleByNativeSourceIdentity() {
        EmsCommonStreamChangeHandler handler = new EmsCommonStreamChangeHandler(List.of());
        Memory oldMemory = new Memory(8);
        Memory newMemory = new Memory(8);
        MK_MEDIA_SOURCE oldRegisterSender = new MK_MEDIA_SOURCE(oldMemory);
        MK_MEDIA_SOURCE oldDeregisterSender = new MK_MEDIA_SOURCE(oldMemory);
        MK_MEDIA_SOURCE newSender = new MK_MEDIA_SOURCE(newMemory);
        MediaSourceDomain oldSource = createMediaSource("rtmp");
        MediaSourceDomain newSource = createMediaSource("rtmp");
        MediaSourceDomain fallback = createMediaSource("rtmp");

        handler.rememberRegisteredLifecycle(oldRegisterSender, oldSource);
        handler.rememberRegisteredLifecycle(newSender, newSource);

        assertEquals(2, handler.registeredLifecycleCount());
        assertSame(oldSource, handler.resolveDeregisteredLifecycle(oldDeregisterSender, fallback));
        assertSame(fallback, handler.resolveDeregisteredLifecycle(oldDeregisterSender, fallback));
        assertSame(newSource, handler.resolveDeregisteredLifecycle(newSender, fallback));
        assertEquals(0, handler.registeredLifecycleCount());
    }

    @Test
    @DisplayName("同指针重复注册复用生命周期且只通知一次")
    void shouldReuseSamePointerRegistrationUntilExactDeregistration() {
        RecordingCallback callback = new RecordingCallback(Set.of("rtmp"));
        List<MK_TRACK> unreferencedTracks = new CopyOnWriteArrayList<>();
        EmsCommonStreamChangeHandler handler = new EmsCommonStreamChangeHandler(
                List.of(callback), new EmsProperties(), unreferencedTracks::add);
        Memory sourceMemory = new Memory(8);
        MK_MEDIA_SOURCE firstSender = new MK_MEDIA_SOURCE(sourceMemory);
        MK_MEDIA_SOURCE duplicateSender = new MK_MEDIA_SOURCE(sourceMemory);
        MK_TRACK firstTrack = new MK_TRACK(new Memory(8));
        IMKFrameOutCallBack firstDelegate = (userData, frame) -> {
        };
        MediaSourceDomain firstSource = createMediaSource("rtmp");
        MediaSourceDomain duplicateSource = createMediaSource("rtmp");
        MediaSourceDomain fallback = createMediaSource("rtmp");

        EmsCommonStreamChangeHandler.LifecycleRegistration firstRegistration =
                handler.prepareRegisteredLifecycle(firstSender, firstSource, List.of(callback));
        handler.installTrackDelegate(firstRegistration.lifecycle(), firstTrack,
                () -> firstDelegate, installedDelegate -> {
                });
        EmsCommonStreamChangeHandler.LifecycleRegistration duplicateRegistration =
                handler.prepareRegisteredLifecycle(duplicateSender, duplicateSource, List.of(callback));

        assertTrue(firstRegistration.created());
        assertTrue(!duplicateRegistration.created());
        assertSame(firstRegistration.lifecycle(), duplicateRegistration.lifecycle());
        assertEquals(1, callback.registeredCount);
        assertEquals(1, handler.retainedTrackDelegateCount(firstSender));
        assertSame(firstTrack, handler.retainedTrack(firstSender, 0));
        assertSame(firstDelegate, handler.retainedTrackDelegate(firstSender, 0));

        handler.resolveDeregisteredLifecycle(duplicateSender, fallback);
        handler.resolveDeregisteredLifecycle(duplicateSender, fallback);

        assertEquals(List.of(firstTrack), unreferencedTracks);
        assertEquals(0, handler.retainedTrackDelegateCount(firstRegistration.lifecycle()));
        assertEquals(0, handler.registeredLifecycleCount());

        EmsCommonStreamChangeHandler.LifecycleRegistration reusedPointerRegistration =
                handler.prepareRegisteredLifecycle(firstSender, createMediaSource("rtmp"), List.of(callback));
        assertTrue(reusedPointerRegistration.created());
        assertNotSame(firstRegistration.lifecycle(), reusedPointerRegistration.lifecycle());
        assertEquals(2, callback.registeredCount);
    }

    @Test
    @DisplayName("注销等待正在进行的注册完成")
    void shouldSerializeRegistrationAndDeregistration() throws InterruptedException {
        CountDownLatch registrationStarted = new CountDownLatch(1);
        CountDownLatch continueRegistration = new CountDownLatch(1);
        TrackDelegateCallback callback = new RecordingCallback(Set.of("rtmp")) {
            @Override
            public void onMediaSourceRegistered(MediaSourceDomain mediaSource) {
                registrationStarted.countDown();
                awaitLatch(continueRegistration);
                super.onMediaSourceRegistered(mediaSource);
            }
        };
        EmsCommonStreamChangeHandler handler = new EmsCommonStreamChangeHandler(List.of(callback));
        Memory sourceMemory = new Memory(8);
        MK_MEDIA_SOURCE registerSender = new MK_MEDIA_SOURCE(sourceMemory);
        MK_MEDIA_SOURCE deregisterSender = new MK_MEDIA_SOURCE(sourceMemory);
        MediaSourceDomain registeredSource = createMediaSource("rtmp");
        AtomicReference<MediaSourceDomain> deregisteredSource = new AtomicReference<>();
        Thread registerThread = new Thread(() -> handler.prepareRegisteredLifecycle(
                registerSender, registeredSource, List.of(callback)), "registration-test");
        Thread deregisterThread = new Thread(() -> deregisteredSource.set(handler.resolveDeregisteredLifecycle(
                deregisterSender, createMediaSource("rtmp"))), "deregistration-test");

        registerThread.start();
        assertTrue(registrationStarted.await(1, TimeUnit.SECONDS));
        deregisterThread.start();
        deregisterThread.join(100L);
        assertTrue(deregisterThread.isAlive());
        continueRegistration.countDown();
        registerThread.join(1_000L);
        deregisterThread.join(1_000L);

        assertFalse(registerThread.isAlive());
        assertFalse(deregisterThread.isAlive());
        assertSame(registeredSource, deregisteredSource.get());
        assertEquals(0, handler.registeredLifecycleCount());
    }

    @Test
    @DisplayName("多个轨道注销时逐一 unref 且单个失败不阻断后续释放")
    void shouldUnrefEveryOwnedTrackWhenOneUnrefFails() {
        List<MK_TRACK> unreferencedTracks = new CopyOnWriteArrayList<>();
        AtomicInteger unrefAttempt = new AtomicInteger();
        EmsCommonStreamChangeHandler handler = new EmsCommonStreamChangeHandler(
                List.of(), new EmsProperties(), track -> {
                    unreferencedTracks.add(track);
                    if (unrefAttempt.incrementAndGet() == 1) {
                        throw new IllegalStateException("first unref failed");
                    }
                });
        MK_MEDIA_SOURCE sender = new MK_MEDIA_SOURCE(new Memory(8));
        MK_TRACK firstTrack = new MK_TRACK(new Memory(8));
        MK_TRACK secondTrack = new MK_TRACK(new Memory(8));
        IMKFrameOutCallBack delegate = (userData, frame) -> {
        };
        EmsCommonStreamChangeHandler.RegisteredLifecycle lifecycle =
                handler.rememberRegisteredLifecycle(sender, createMediaSource("rtmp")).lifecycle();

        handler.installTrackDelegate(lifecycle, firstTrack, () -> delegate, installedDelegate -> {
        });
        handler.installTrackDelegate(lifecycle, secondTrack, () -> delegate, installedDelegate -> {
        });
        handler.resolveDeregisteredLifecycle(sender, createMediaSource("rtmp"));

        assertEquals(List.of(firstTrack, secondTrack), unreferencedTracks);
        assertEquals(0, handler.retainedTrackDelegateCount(lifecycle));
    }

    @Test
    @DisplayName("精确注销只释放一次轨道引用")
    void shouldUnrefOwnedTrackExactlyOnceOnExactDeregistration() {
        AtomicInteger unrefCount = new AtomicInteger();
        EmsCommonStreamChangeHandler handler = new EmsCommonStreamChangeHandler(
                List.of(), new EmsProperties(), track -> unrefCount.incrementAndGet());
        Memory sourceMemory = new Memory(8);
        MK_MEDIA_SOURCE registerSender = new MK_MEDIA_SOURCE(sourceMemory);
        MK_MEDIA_SOURCE deregisterSender = new MK_MEDIA_SOURCE(sourceMemory);
        MK_TRACK track = new MK_TRACK(new Memory(8));
        IMKFrameOutCallBack delegate = (userData, frame) -> {
        };
        MediaSourceDomain fallback = createMediaSource("rtmp");
        EmsCommonStreamChangeHandler.RegisteredLifecycle lifecycle =
                handler.rememberRegisteredLifecycle(registerSender, createMediaSource("rtmp")).lifecycle();

        handler.installTrackDelegate(lifecycle, track, () -> delegate, installedDelegate -> {
        });
        handler.resolveDeregisteredLifecycle(deregisterSender, fallback);
        handler.resolveDeregisteredLifecycle(deregisterSender, fallback);

        assertEquals(1, unrefCount.get());
        assertEquals(0, handler.retainedTrackDelegateCount(lifecycle));
    }

    @Test
    @DisplayName("原生代理安装抛出 Error 时保留所有权到精确注销")
    void shouldRetainAmbiguousNativeInstallationUntilDeregistration() {
        List<MK_TRACK> unreferencedTracks = new CopyOnWriteArrayList<>();
        EmsCommonStreamChangeHandler handler = new EmsCommonStreamChangeHandler(
                List.of(), new EmsProperties(), unreferencedTracks::add);
        MK_MEDIA_SOURCE sender = new MK_MEDIA_SOURCE(new Memory(8));
        MK_TRACK track = new MK_TRACK(new Memory(8));
        IMKFrameOutCallBack delegate = (userData, frame) -> {
        };
        EmsCommonStreamChangeHandler.RegisteredLifecycle lifecycle =
                handler.rememberRegisteredLifecycle(sender, createMediaSource("rtmp")).lifecycle();

        assertThrows(AssertionError.class, () -> handler.installTrackDelegate(
                lifecycle, track, () -> delegate, installedDelegate -> {
                    throw new AssertionError("native install failed");
                }));

        assertEquals(1, handler.retainedTrackDelegateCount(sender));
        handler.resolveDeregisteredLifecycle(sender, createMediaSource("rtmp"));
        assertEquals(List.of(track), unreferencedTracks);
        assertEquals(0, handler.retainedTrackDelegateCount(lifecycle));
    }

    @Test
    @DisplayName("空原生轨道不安装也不 unref")
    void shouldRejectNullNativeTrackBeforeDelegateInstallation() {
        AtomicInteger installCount = new AtomicInteger();
        AtomicInteger unrefCount = new AtomicInteger();
        EmsCommonStreamChangeHandler handler = new EmsCommonStreamChangeHandler(
                List.of(), new EmsProperties(), track -> unrefCount.incrementAndGet());
        MK_MEDIA_SOURCE sender = new MK_MEDIA_SOURCE(new Memory(8));
        EmsCommonStreamChangeHandler.RegisteredLifecycle lifecycle =
                handler.rememberRegisteredLifecycle(sender, createMediaSource("rtmp")).lifecycle();
        IMKFrameOutCallBack delegate = (userData, frame) -> {
        };
        MK_TRACK nullPointerTrack = new MK_TRACK() {
            @Override
            public Pointer getPointer() {
                return Pointer.NULL;
            }
        };

        assertTrue(!handler.installTrackDelegate(
                lifecycle, null, () -> delegate, installedDelegate -> installCount.incrementAndGet()));
        assertTrue(!handler.installTrackDelegate(
                lifecycle, nullPointerTrack, () -> delegate,
                installedDelegate -> installCount.incrementAndGet()));
        handler.resolveDeregisteredLifecycle(sender, createMediaSource("rtmp"));

        assertEquals(0, installCount.get());
        assertEquals(0, unrefCount.get());
    }

    @Test
    @DisplayName("超限和非法原生帧不会读取指针或分发")
    void shouldRejectUnsafeNativeFramesBeforeAllocationReadAndDispatch() {
        RecordingCallback callback = new RecordingCallback(Set.of("rtmp"));
        EmsProperties properties = new EmsProperties();
        properties.setMaxTrackFrameBytes(8);
        EmsCommonStreamChangeHandler handler = new EmsCommonStreamChangeHandler(List.of(callback), properties);
        MediaSourceDomain source = createMediaSource("rtmp");
        TrackDomain track = new TrackDomain();
        track.setCodecIdName("H264");
        CountingPointer pointer = new CountingPointer();

        assertTrue(!handler.acceptNativeFrame(source, track, 0L, 1L, 2L, 0L, 0L,
                pointer, List.of(callback)));
        assertTrue(!handler.acceptNativeFrame(source, track, -1L, 1L, 2L, 0L, 0L,
                pointer, List.of(callback)));
        assertTrue(!handler.acceptNativeFrame(source, track, 9L, 1L, 2L, 0L, 0L,
                pointer, List.of(callback)));
        assertTrue(!handler.acceptNativeFrame(source, track, (long) Integer.MAX_VALUE + 1L,
                1L, 2L, 0L, 0L, pointer, List.of(callback)));
        assertTrue(!handler.acceptNativeFrame(source, track, 1L, 1L, 2L, 0L, 0L,
                null, List.of(callback)));
        assertTrue(!handler.acceptNativeFrame(source, track, 1L, 1L, 2L, 0L, 0L,
                Pointer.NULL, List.of(callback)));

        assertEquals(0, pointer.readCount.get());
        assertEquals(0, callback.frameCount);
    }

    @Test
    @DisplayName("合法原生帧只读取一次并同步分发")
    void shouldReadAndDispatchValidatedNativeFrame() {
        RecordingCallback callback = new RecordingCallback(Set.of("rtmp"));
        EmsCommonStreamChangeHandler handler = new EmsCommonStreamChangeHandler(List.of(callback));
        MediaSourceDomain source = createMediaSource("rtmp");
        TrackDomain track = new TrackDomain();
        track.setCodecIdName("H264");
        CountingPointer pointer = new CountingPointer();

        assertTrue(handler.acceptNativeFrame(source, track, 4L, 1L, 2L, 0L, 0L,
                pointer, List.of(callback)));

        assertEquals(1, pointer.readCount.get());
        assertEquals(1, callback.frameCount);
    }

    /**
     * 创建指定协议的媒体源。
     *
     * @param schema 媒体源协议
     * @return 媒体源
     */
    private MediaSourceDomain createMediaSource(String schema) {
        MediaSourceDomain source = new MediaSourceDomain();
        source.setSchema(schema);
        source.setApp("live");
        source.setStream("drone-001");
        return source;
    }

    /**
     * 等待并发测试闩锁，超时或中断时使测试线程失败。
     *
     * @param latch 待等待闩锁
     */
    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("latch timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("latch interrupted", exception);
        }
    }

    /** 可记录媒体源生命周期和帧事件的测试回调。 */
    private static class RecordingCallback implements TrackDelegateCallback {

        /** 支持的媒体源协议。 */
        private final Set<String> schemas;

        /** 是否固定抛出异常。 */
        private final boolean failing;

        /** 注册事件次数。 */
        private int registeredCount;

        /** 帧事件次数。 */
        private int frameCount;

        /** 注销事件次数。 */
        private int deregisteredCount;

        /**
         * 创建正常测试回调。
         *
         * @param schemas 支持的媒体源协议
         */
        private RecordingCallback(Set<String> schemas) {
            this(schemas, false);
        }

        /**
         * 创建测试回调。
         *
         * @param schemas 支持的媒体源协议
         * @param failing 是否固定抛出异常
         */
        private RecordingCallback(Set<String> schemas, boolean failing) {
            this.schemas = schemas;
            this.failing = failing;
        }

        @Override
        public Set<String> supportedSchemas() {
            return schemas;
        }

        @Override
        public void onMediaSourceRegistered(MediaSourceDomain mediaSource) {
            throwIfRequired();
            registeredCount++;
        }

        @Override
        public void onMediaSourceDeregistered(MediaSourceDomain mediaSource) {
            throwIfRequired();
            deregisteredCount++;
        }

        @Override
        public void callback(MediaSourceDomain mediaSourceDomain, TrackDomain trackDomain,
                             TackDelegateInfo tackDelegateInfo) {
            throwIfRequired();
            frameCount++;
        }

        /** 在异常测试回调中抛出固定异常。 */
        private void throwIfRequired() {
            if (failing) {
                throw new IllegalStateException("expected callback failure");
            }
        }
    }

    /**
     * 记录原生内存读取次数的测试指针。
     *
     * @author junpzx
     * @since 2026-08-13
     */
    private static final class CountingPointer extends Pointer {

        /** 指针读取次数。 */
        private final AtomicInteger readCount = new AtomicInteger();

        /** 创建非空测试指针。 */
        private CountingPointer() {
            super(1L);
        }

        @Override
        public void read(long offset, byte[] buffer, int index, int length) {
            readCount.incrementAndGet();
        }
    }
}
