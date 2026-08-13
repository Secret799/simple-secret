package com.ss.camerazlm;

import com.ss.easymedia.h264.H264NakedFlowPushZlmManager;
import com.ss.ics.dahua.DahuaCameraSdkService;
import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.PlayDomain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 管理大华实时 H.264 到 ZLM 的独立适配会话。
 *
 * @author junpzx
 * @since 2026-08-13
 */
public final class DahuaZlmStreamService implements AutoCloseable {

    /** ZLM app 和 stream 允许使用的安全字符。 */
    private static final Pattern STREAM_NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    /** 兼容旧构造器时使用的单帧字节上限。 */
    private static final int DEFAULT_MAX_FRAME_BYTES = 4 * 1024 * 1024;
    /** 兼容旧构造器时使用的单路待处理字节预算。 */
    private static final long DEFAULT_MAX_BUFFERED_BYTES = 32L * 1024L * 1024L;
    /** 原生 SDK 允许的最大单帧字节数。 */
    private static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;
    /** 单路流允许的最大待处理字节数。 */
    private static final long MAX_BUFFERED_BYTES = 1024L * 1024L * 1024L;

    /** 大华 SDK 服务。 */
    private final DahuaStreamSource cameraSource;
    /** H.264 到 ZLM 的推流管理器。 */
    private final H264StreamPublisher publisher;
    /** 每路流允许等待消费的帧数。 */
    private final int queueCapacity;
    /** 单帧允许进入适配层的最大字节数。 */
    private final int maxFrameBytes;
    /** 单路排队和推送中的总字节预算。 */
    private final long maxBufferedBytes;
    /** 单路流关闭等待上限。 */
    private final Duration closeTimeout;
    /** 以 app 和 stream 组合键索引的活动会话。 */
    private final ConcurrentHashMap<String, DahuaZlmStreamSession> activeSessions =
            new ConcurrentHashMap<>();
    /** 服务关闭状态。 */
    private final AtomicBoolean closed = new AtomicBoolean(false);
    /** 启动和关闭互斥锁。 */
    private final Object lifecycleLock = new Object();

    /**
     * 创建大华转推服务。
     *
     * @param cameraService 已显式初始化的大华 SDK 服务
     * @param publisher 已显式初始化的 H.264 publisher
     * @param queueCapacity 每路流的有界帧队列容量
     * @param closeTimeout 关闭消费线程的最大等待时长
     */
    public DahuaZlmStreamService(
            DahuaCameraSdkService cameraService, H264NakedFlowPushZlmManager publisher,
            int queueCapacity, Duration closeTimeout) {
        this(Objects.requireNonNull(cameraService, "cameraService")::realPlay,
                publisherAdapter(Objects.requireNonNull(publisher, "publisher")),
                queueCapacity, DEFAULT_MAX_FRAME_BYTES,
                DEFAULT_MAX_BUFFERED_BYTES, closeTimeout);
    }

    /**
     * 创建具有帧数和字节双重边界的大华转推服务。
     *
     * @param cameraService 已显式初始化的大华 SDK 服务
     * @param publisher 当前服务独占的 H.264 publisher
     * @param queueCapacity 每路流的有界帧队列容量
     * @param maxFrameBytes 单帧最大字节数
     * @param maxBufferedBytes 单路排队和推送中的总字节预算
     * @param closeTimeout 关闭消费线程的最大等待时长
     */
    public DahuaZlmStreamService(
            DahuaCameraSdkService cameraService, H264NakedFlowPushZlmManager publisher,
            int queueCapacity, int maxFrameBytes, long maxBufferedBytes,
            Duration closeTimeout) {
        this(Objects.requireNonNull(cameraService, "cameraService")::realPlay,
                publisherAdapter(Objects.requireNonNull(publisher, "publisher")),
                queueCapacity, maxFrameBytes, maxBufferedBytes, closeTimeout);
    }

    DahuaZlmStreamService(
            DahuaStreamSource cameraSource, H264StreamPublisher publisher,
            int queueCapacity, Duration closeTimeout) {
        this(cameraSource, publisher, queueCapacity, DEFAULT_MAX_FRAME_BYTES,
                DEFAULT_MAX_BUFFERED_BYTES, closeTimeout);
    }

    DahuaZlmStreamService(
            DahuaStreamSource cameraSource, H264StreamPublisher publisher,
            int queueCapacity, int maxFrameBytes, long maxBufferedBytes,
            Duration closeTimeout) {
        this.cameraSource = Objects.requireNonNull(cameraSource, "cameraSource");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be greater than 0");
        }
        if (closeTimeout == null || closeTimeout.isZero() || closeTimeout.isNegative()) {
            throw new IllegalArgumentException("closeTimeout must be positive");
        }
        if (maxFrameBytes <= 0 || maxFrameBytes > MAX_FRAME_BYTES) {
            throw new IllegalArgumentException("maxFrameBytes must be between 1 and 16777216");
        }
        if (maxBufferedBytes < maxFrameBytes || maxBufferedBytes > MAX_BUFFERED_BYTES) {
            throw new IllegalArgumentException(
                    "maxBufferedBytes must be between maxFrameBytes and 1073741824");
        }
        this.queueCapacity = queueCapacity;
        this.maxFrameBytes = maxFrameBytes;
        this.maxBufferedBytes = maxBufferedBytes;
        this.closeTimeout = closeTimeout;
    }

    /**
     * 启动一路大华实时预览并转推到 ZLM。
     *
     * @param device 不会被记录的设备连接信息
     * @param play 大华实时取流参数
     * @param app ZLM 应用名
     * @param stream ZLM 流名
     * @return 必须由调用方关闭的转推会话
     */
    public DahuaZlmStreamSession start(
            DeviceDomain device, PlayDomain play, String app, String stream) {
        validateStart(device, play, app, stream);
        String key = key(app, stream);
        synchronized (lifecycleLock) {
            ensureOpen();
            if (activeSessions.containsKey(key)) {
                throw new CameraZlmException("Camera-to-ZLM stream is already active");
            }
            DahuaZlmStreamSession session = new DahuaZlmStreamSession(
                    app, stream, publisher, queueCapacity,
                    maxFrameBytes, maxBufferedBytes, closeTimeout,
                    closedSession -> activeSessions.remove(key, closedSession));
            activeSessions.put(key, session);
            return startSource(session, device, play);
        }
    }

    /**
     * 查询当前活动转推会话数量。
     *
     * @return 活动会话数量
     */
    public int activeSessionCount() {
        return activeSessions.size();
    }

    /** 关闭全部活动会话；关闭不完整的会话仍可再次调用其 close 重试。 */
    @Override
    public void close() {
        synchronized (lifecycleLock) {
            closed.set(true);
        }
        RuntimeException failure = null;
        for (DahuaZlmStreamSession session : List.copyOf(activeSessions.values())) {
            try {
                session.close();
            } catch (RuntimeException exception) {
                failure = combine(failure, exception);
            }
        }
        if (failure != null) {
            throw new CameraZlmException("Failed to close every camera-to-ZLM stream", failure);
        }
    }

    private DahuaZlmStreamSession startSource(
            DahuaZlmStreamSession session, DeviceDomain device, PlayDomain play) {
        AutoCloseable source;
        try {
            source = Objects.requireNonNull(
                    cameraSource.start(device, play, session::accept),
                    "camera source session");
        } catch (RuntimeException exception) {
            session.sourceStartFailed();
            closeAfterStartFailure(session, exception);
            throw exception;
        } catch (LinkageError error) {
            CameraZlmException exception = new CameraZlmException(
                    "Native camera source failed during startup", error);
            session.sourceStartFailed();
            closeAfterStartFailure(session, exception);
            throw exception;
        }
        session.attachSource(source);
        RuntimeException startupFailure = session.failure().orElse(null);
        if (startupFailure != null) {
            CameraZlmException exception = new CameraZlmException(
                    "Camera-to-ZLM stream failed during startup", startupFailure);
            closeAfterStartFailure(session, exception);
            throw exception;
        }
        return session;
    }

    private static void closeAfterStartFailure(
            DahuaZlmStreamSession session, RuntimeException primaryFailure) {
        try {
            session.close();
        } catch (RuntimeException cleanupFailure) {
            if (cleanupFailure != primaryFailure) {
                primaryFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Dahua ZLM stream service is closed");
        }
    }

    private static void validateStart(
            DeviceDomain device, PlayDomain play, String app, String stream) {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(play, "play");
        validateStreamName(app, "app");
        validateStreamName(stream, "stream");
    }

    private static void validateStreamName(String value, String field) {
        if (value == null || !STREAM_NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " must contain 1 to 128 letters, digits, dots, underscores or hyphens");
        }
    }

    private static String key(String app, String stream) {
        return app + '\0' + stream;
    }

    private static RuntimeException combine(RuntimeException first, RuntimeException second) {
        if (first == null) {
            return second;
        }
        first.addSuppressed(second);
        return first;
    }

    private static H264StreamPublisher publisherAdapter(
            H264NakedFlowPushZlmManager publisher) {
        return new H264StreamPublisher() {
            @Override
            public int push(String app, String stream, byte[] data) throws InterruptedException {
                return publisher.pushWithBackpressure(app, stream, data);
            }

            @Override
            public void stop(String app, String stream) {
                publisher.stopPush(app, stream);
            }
        };
    }
}
