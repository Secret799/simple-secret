package com.ss.easymedia.h264;

import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.MK_FRAME;
import com.aizuda.zlm4j.structure.MK_INI;
import com.aizuda.zlm4j.structure.MK_MEDIA;
import com.ss.easymedia.h264.parser.H264NalUnit;
import com.ss.easymedia.h264.parser.H264NalUnitReader;
import com.ss.easymedia.support.MemoryTimeCacheManager;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.constants.ZlmMediaServerConstants;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * H.264 裸流推送 ZLM 管理器
 *
 * @author JunPzx
 * @since 2025/11/25 17:21
 */
public class H264NakedFlowPushZlmManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(H264NakedFlowPushZlmManager.class);

    /**
     * 流读取器缓存(用于自动回收资源)
     */
    private final MemoryTimeCacheManager<String, StreamResource> streamReaderCache;
    /**
     * ZLM 配置属性
     */
    private final ZlmMediaProperties zlmMediaProperties;
    /**
     * 默认的流数据没有推送的回收时间(秒)
     */
    private static final Integer DEFAULT_NO_PUSH_RECYCLE_TIME_SECOND = 30;
    /**
     * 默认处理队列大小
     */
    private static final Integer DEFAULT_PROCESS_QUEUE_SIZE = 30 * 5;
    /**
     * 处理队列大小
     */
    private final int processQueueSize;
    /**
     * ZLM 媒体对象
     */
    private final ZLMApi zlmApi;

    /**
     * 构造函数
     *
     * @param zlmMediaProperties      ZLM 配置属性
     * @param noPushRecycleTimeSecond 流数据没有推送的回收时间(秒)
     * @param processQueueSize        有界处理队列大小，必须大于 0
     */
    public H264NakedFlowPushZlmManager(ZlmMediaProperties zlmMediaProperties, int noPushRecycleTimeSecond,
                                       int processQueueSize) {
        this(zlmMediaProperties, noPushRecycleTimeSecond, processQueueSize, ZlmMediaHelper.getZlmApi());
    }

    H264NakedFlowPushZlmManager(ZlmMediaProperties zlmMediaProperties, int noPushRecycleTimeSecond,
                                int processQueueSize, ZLMApi zlmApi) {
        if (processQueueSize <= 0) {
            throw new IllegalArgumentException("processQueueSize must be greater than 0");
        }
        streamReaderCache = new MemoryTimeCacheManager<>(Duration
                .ofSeconds(noPushRecycleTimeSecond));
        streamReaderCache.addOnRemoveListener((key, value) -> value.close());
        this.zlmMediaProperties = Objects.requireNonNull(zlmMediaProperties, "zlmMediaProperties");
        this.processQueueSize = processQueueSize;
        this.zlmApi = Objects.requireNonNull(zlmApi, "zlmApi");
    }

    /**
     * 构造函数
     *
     * @param zlmMediaProperties ZLM 配置属性
     */
    public H264NakedFlowPushZlmManager(ZlmMediaProperties zlmMediaProperties) {
        this(zlmMediaProperties, DEFAULT_NO_PUSH_RECYCLE_TIME_SECOND, DEFAULT_PROCESS_QUEUE_SIZE);
    }

    /**
     * 推送 H.264 裸流数据
     *
     * @param app    应用名
     * @param stream 流名
     * @param data   数据
     * @throws InterruptedException 处理队列满了导致数据无法处理异常
     */
    public void push(String app, String stream, byte[] data) throws InterruptedException {
        String cacheKey = generateKey(app, stream);
        StreamResource streamResource = streamReaderCache.get(cacheKey, true, () -> createStreamResource(app, stream));
        streamResource.writeFragment(data);
    }

    /**
     * 停止指定裸流并释放对应的 ZLM 媒体资源。
     *
     * @param app    应用名
     * @param stream 流名
     */
    public void stopPush(String app, String stream) {
        streamReaderCache.remove(generateKey(app, stream));
    }

    /**
     * 释放全部裸流和缓存清理线程。
     */
    @Override
    public void close() {
        streamReaderCache.clear();
        streamReaderCache.destroy();
    }

    private StreamResource createStreamResource(String app, String stream) {
        MK_INI mkIni = null;
        MK_MEDIA mkMedia = null;
        H264NalUnitReader reader = null;
        StreamResource resource = null;
        try {
            mkIni = zlmApi.mk_ini_create();
            ZlmMediaHelper.Configurator.setConfig(zlmApi, mkIni, zlmMediaProperties);
            mkMedia = zlmApi.mk_media_create2(ZlmMediaServerConstants.DEFAULT_VHOST,
                    app, stream, 0, mkIni);
            if (mkMedia == null) {
                throw new IllegalStateException("创建 ZLM 媒体资源失败");
            }
            zlmApi.mk_media_init_video(mkMedia, 0, 1440, 1080, 30, 2500);
            zlmApi.mk_media_init_audio(mkMedia, 2, 8000, 1, 16);
            zlmApi.mk_media_init_complete(mkMedia);
            reader = new H264NalUnitReader(processQueueSize);
            resource = new StreamResource(reader, mkMedia, zlmApi);
            reader.setNalUnitProcessor(resource::inputNalUnit);
            reader.startReading();
            return resource;
        } catch (RuntimeException | Error e) {
            if (resource != null) {
                resource.close();
            } else {
                if (reader != null) {
                    reader.close();
                }
                if (mkMedia != null) {
                    zlmApi.mk_media_release(mkMedia);
                }
            }
            throw e;
        } finally {
            if (mkIni != null) {
                zlmApi.mk_ini_release(mkIni);
            }
        }
    }


    /**
     * 生成缓存的key
     *
     * @param app    应用名
     * @param stream 流名
     * @return 缓存的key
     */
    private String generateKey(String app, String stream) {
        return app + ":" + stream;
    }

    private static final class StreamResource implements AutoCloseable {

        private final H264NalUnitReader reader;
        private final MK_MEDIA media;
        private final ZLMApi zlmApi;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final Object mediaLock = new Object();

        private StreamResource(H264NalUnitReader reader, MK_MEDIA media, ZLMApi zlmApi) {
            this.reader = Objects.requireNonNull(reader, "reader");
            this.media = Objects.requireNonNull(media, "media");
            this.zlmApi = Objects.requireNonNull(zlmApi, "zlmApi");
        }

        private void writeFragment(byte[] data) throws InterruptedException {
            if (closed.get()) {
                throw new IllegalStateException("H264 stream resource is closed");
            }
            reader.writeFragment(data);
        }

        private void inputNalUnit(H264NalUnit nalUnit) {
            synchronized (mediaLock) {
                if (closed.get()) {
                    return;
                }
                byte[] nalData = nalUnit.getNalData();
                try (Memory memory = new Memory(nalData.length)) {
                    memory.write(0, nalData, 0, nalData.length);
                    MK_FRAME frame = zlmApi.mk_frame_create(0, nalUnit.getFirstFragmentTimestamp(),
                            nalUnit.getFirstFragmentTimestamp(), memory.share(0), nalData.length,
                            null, Pointer.NULL);
                    if (frame == null) {
                        log.error("创建 ZLM H264 frame 失败");
                        return;
                    }
                    try {
                        zlmApi.mk_media_input_frame(media, frame);
                    } finally {
                        zlmApi.mk_frame_unref(frame);
                    }
                }
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                reader.close();
                synchronized (mediaLock) {
                    zlmApi.mk_media_release(media);
                }
            }
        }
    }

}
