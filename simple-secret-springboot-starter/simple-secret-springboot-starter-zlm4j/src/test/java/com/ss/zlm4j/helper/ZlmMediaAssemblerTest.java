package com.ss.zlm4j.helper;

import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.aizuda.zlm4j.structure.MK_TRACK;
import com.ss.zlm4j.callback.MKStreamChangeCallBack;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.context.ZlmCallbackHandlerContext;
import com.ss.zlm4j.context.ZlmMediaContext;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.event.StreamRegisteredEvent;
import com.ss.zlm4j.support.SpringUtils;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * ZLMediaKit 媒体源组装与复制轨道引用释放测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
class ZlmMediaAssemblerTest {

    @Test
    void releasesEveryCopiedTrackExactlyOnce() {
        MK_TRACK firstTrack = track();
        MK_TRACK secondTrack = track();
        FakeZlmApi fake = new FakeZlmApi(List.of(firstTrack, secondTrack));

        MediaSourceDomain source = ZlmMediaHelper.Assembler.getMediaSource(
                fake.api(), mediaSource(), true);

        assertThat(source.getTracks()).hasSize(2);
        assertThat(fake.unreferencedTracks()).containsExactly(firstTrack, secondTrack);
    }

    @Test
    void skipsNullTracksWithoutUnref() {
        MK_TRACK nullPointerTrack = new MK_TRACK() {
            @Override
            public Pointer getPointer() {
                return Pointer.NULL;
            }
        };
        MK_TRACK validTrack = track();
        FakeZlmApi fake = new FakeZlmApi(java.util.Arrays.asList(null, nullPointerTrack, validTrack));

        MediaSourceDomain source = ZlmMediaHelper.Assembler.getMediaSource(
                fake.api(), mediaSource(), true);

        assertThat(source.getTracks()).hasSize(1);
        assertThat(fake.unreferencedTracks()).containsExactly(validTrack);
    }

    @Test
    void releasesMalformedTrackAndContinuesWithRemainingTracks() {
        MK_TRACK runtimeFailureTrack = track();
        MK_TRACK errorFailureTrack = track();
        MK_TRACK validTrack = track();
        FakeZlmApi fake = new FakeZlmApi(List.of(runtimeFailureTrack, errorFailureTrack, validTrack));
        fake.failMetadataFor(runtimeFailureTrack, errorFailureTrack);

        MediaSourceDomain source = ZlmMediaHelper.Assembler.getMediaSource(
                fake.api(), mediaSource(), true);

        assertThat(source.getTracks()).hasSize(1);
        assertThat(fake.unreferencedTracks()).containsExactly(runtimeFailureTrack, errorFailureTrack, validTrack);
    }

    @Test
    void isolatesUnrefFailureAndContinuesWithRemainingTracks() {
        MK_TRACK firstTrack = track();
        MK_TRACK secondTrack = track();
        MK_TRACK thirdTrack = track();
        FakeZlmApi fake = new FakeZlmApi(List.of(firstTrack, secondTrack, thirdTrack));
        fake.failFirstTwoUnrefs();

        assertThatCode(() -> ZlmMediaHelper.Assembler.getMediaSource(fake.api(), mediaSource(), true))
                .doesNotThrowAnyException();
        assertThat(fake.unreferencedTracks()).containsExactly(firstTrack, secondTrack, thirdTrack);
    }

    @Test
    void streamChangeCallbackPublishesTracksAfterReleasingCopiedReferences() {
        MK_TRACK track = track();
        FakeZlmApi fake = new FakeZlmApi(List.of(track));
        List<StreamRegisteredEvent> events = new ArrayList<>();

        try (GenericApplicationContext context = applicationContext(fake.api(), events)) {
            new MKStreamChangeCallBack(null).invoke(1, mediaSource());
        }

        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.getMediaSource().getTracks()).hasSize(1));
        assertThat(fake.unreferencedTracks()).containsExactly(track);
    }

    /**
     * 创建包含原生 API 和事件监听器的测试上下文。
     *
     * @param api 受控原生 API
     * @param events 已发布注册事件
     * @return 已刷新的测试上下文
     */
    private GenericApplicationContext applicationContext(ZLMApi api, List<StreamRegisteredEvent> events) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(SpringUtils.class);
        context.registerBean(ZlmMediaContext.class, () -> new TestZlmMediaContext(api));
        context.addApplicationListener(event -> {
            if (event instanceof StreamRegisteredEvent registeredEvent) {
                events.add(registeredEvent);
            }
        });
        context.refresh();
        return context;
    }

    /** @return 测试媒体源句柄 */
    private static MK_MEDIA_SOURCE mediaSource() {
        return new MK_MEDIA_SOURCE(new Memory(8));
    }

    /** @return 测试轨道句柄 */
    private static MK_TRACK track() {
        return new MK_TRACK(new Memory(8));
    }

    /**
     * 提供受控原生 API 的测试媒体上下文。
     *
     * @author junpzx
     * @since 2026-08-13
     */
    private static final class TestZlmMediaContext extends ZlmMediaContext {

        /** 受控原生 API。 */
        private final ZLMApi api;

        /**
         * 创建测试媒体上下文。
         *
         * @param api 受控原生 API
         */
        private TestZlmMediaContext(ZLMApi api) {
            super(new ZlmMediaProperties(), new ZlmCallbackHandlerContext());
            this.api = api;
        }

        /** @return 受控原生 API */
        @Override
        public ZLMApi getZlmApi() {
            return api;
        }

        /** 禁止测试 Spring 上下文启动真实原生媒体服务。 */
        @Override
        public void initMediaServer() {
            // 外层事件路径只需要受控 API，不需要启动原生监听器。
        }
    }

    /**
     * 可注入轨道和失败点的原生 API 替身。
     *
     * @author junpzx
     * @since 2026-08-13
     */
    private static final class FakeZlmApi {

        /** 按原生索引返回的轨道列表。 */
        private final List<MK_TRACK> tracks;

        /** 已尝试释放的轨道列表。 */
        private final List<MK_TRACK> unreferencedTracks = new ArrayList<>();

        /** JDK 动态代理生成的原生 API。 */
        private final ZLMApi api;

        /** 元数据读取抛出普通异常的轨道。 */
        private MK_TRACK runtimeFailureTrack;

        /** 元数据读取抛出严重错误的轨道。 */
        private MK_TRACK errorFailureTrack;

        /** 是否让前两次轨道释放失败。 */
        private boolean failFirstTwoUnrefs;

        /** 轨道释放尝试次数。 */
        private final AtomicInteger unrefAttempts = new AtomicInteger();

        /**
         * 创建原生 API 替身。
         *
         * @param tracks 按原生索引返回的轨道
         */
        private FakeZlmApi(List<MK_TRACK> tracks) {
            this.tracks = tracks;
            this.api = (ZLMApi) Proxy.newProxyInstance(ZLMApi.class.getClassLoader(),
                    new Class<?>[]{ZLMApi.class}, (proxy, method, args) -> invoke(method.getName(), args));
        }

        /** @return 原生 API 替身 */
        private ZLMApi api() {
            return api;
        }

        /** @return 已尝试释放的轨道快照 */
        private List<MK_TRACK> unreferencedTracks() {
            return List.copyOf(unreferencedTracks);
        }

        /**
         * 设置两种元数据读取失败的轨道。
         *
         * @param runtimeTrack 抛出普通异常的轨道
         * @param errorTrack 抛出严重错误的轨道
         */
        private void failMetadataFor(MK_TRACK runtimeTrack, MK_TRACK errorTrack) {
            runtimeFailureTrack = runtimeTrack;
            errorFailureTrack = errorTrack;
        }

        /** 让前两次轨道释放分别抛出普通异常和严重错误。 */
        private void failFirstTwoUnrefs() {
            failFirstTwoUnrefs = true;
        }

        /**
         * 响应受控原生 API 调用。
         *
         * @param methodName 原生方法名
         * @param arguments 原生方法参数
         * @return 原生方法返回值
         */
        private Object invoke(String methodName, Object[] arguments) {
            return switch (methodName) {
                case "mk_media_source_get_app" -> "live";
                case "mk_media_source_get_stream" -> "dock-01";
                case "mk_media_source_get_schema" -> "rtmp";
                case "mk_media_source_get_track_count" -> tracks.size();
                case "mk_media_source_get_track" -> tracks.get((int) arguments[1]);
                case "mk_media_source_get_origin_url" -> originUrl();
                case "mk_track_codec_name" -> "H264";
                case "mk_track_codec_id", "mk_track_bit_rate", "mk_track_video_width",
                        "mk_track_video_height", "mk_track_video_fps" -> metadataValue(methodName, arguments);
                case "mk_track_is_video" -> 1;
                case "mk_track_unref" -> unref((MK_TRACK) arguments[0]);
                default -> defaultValue(methodName);
            };
        }

        /**
         * 返回轨道元数据或注入读取失败。
         *
         * @param methodName 原生方法名
         * @param arguments 原生方法参数
         * @return 固定元数据
         */
        private int metadataValue(String methodName, Object[] arguments) {
            if (arguments[0] == runtimeFailureTrack) {
                throw new IllegalStateException("metadata failed");
            }
            if (arguments[0] == errorFailureTrack) {
                throw new AssertionError("metadata error");
            }
            return "mk_track_codec_id".equals(methodName) ? 7 : 1;
        }

        /**
         * 记录并执行一次轨道释放尝试。
         *
         * @param track 待释放轨道
         * @return void 原生方法的空返回值
         */
        private Object unref(MK_TRACK track) {
            unreferencedTracks.add(track);
            int attempt = unrefAttempts.incrementAndGet();
            if (failFirstTwoUnrefs && attempt == 1) {
                throw new IllegalStateException("unref failed");
            }
            if (failFirstTwoUnrefs && attempt == 2) {
                throw new AssertionError("unref error");
            }
            return null;
        }

        /** @return 包含测试 URL 的原生内存 */
        private Pointer originUrl() {
            Memory originUrl = new Memory(16);
            originUrl.setString(0, "rtmp://origin");
            return originUrl;
        }

        /**
         * 返回未显式模拟方法的稳定值。
         *
         * @param methodName 原生方法名
         * @return 时间戳使用 long，其余信息字段使用 int
         */
        private Object defaultValue(String methodName) {
            if ("mk_media_source_get_create_stamp".equals(methodName)) {
                return 1L;
            }
            return 1;
        }
    }
}
