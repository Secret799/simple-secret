package com.ss.easymedia.core.handler;

import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.ss.easymedia.callback.TrackDelegateCallback;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.domain.TrackDomain;
import com.sun.jna.Memory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
}
