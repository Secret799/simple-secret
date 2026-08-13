package com.ss.ics.hikvision;

import com.ss.ics.domain.LoggedDomain;
import com.ss.ics.domain.LoginDomain;
import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.PTZControlDomain;
import com.ss.ics.domain.PlayDomain;
import com.ss.ics.domain.PlaybackTimePeriodDomain;
import com.ss.ics.constants.enums.PtzControlCommandEnums;
import com.ss.ics.service.DeviceLoginService;
import com.ss.ics.service.PlayService;
import com.ss.ics.service.PlayQueryService;
import com.ss.ics.service.PtzControlService;
import com.ss.ics.hikvision.internal.HikvisionNativeApi;
import com.ss.ics.hikvision.internal.HikvisionNativeStreamCallback;
import com.ss.ics.hikvision.internal.HikvisionNativeStreamStartException;
import com.ss.ics.hikvision.internal.model.HikvisionNativeLoginResult;
import com.ss.ics.hikvision.internal.model.HikvisionPlaybackRequest;
import com.ss.ics.hikvision.internal.model.HikvisionPreviewRequest;
import com.ss.ics.hikvision.internal.query.HikvisionPlaybackCalendarQuery;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * 海康 SDK 登录、PTZ、录像查询和取流能力的统一生命周期入口。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public final class HikvisionCameraSdkService
        implements DeviceLoginService, PtzControlService, PlayQueryService,
        PlayService<HikvisionStreamSession, HikvisionStreamDataHandler>, AutoCloseable {
    /** basic plugin 使用的厂商产品编码。 */
    public static final String PRODUCT = "Hikvision";

    private final HikvisionSdkRuntime runtime;
    private final HikvisionNativeApi nativeApi;
    private final HikvisionPlaybackCalendarQuery playbackCalendarQuery;
    private final ThreadPoolExecutor ptzExecutor;
    private final Semaphore asyncPtzSlots;
    /** 最近一次已接受异步 PTZ 任务的执行失败。 */
    private final AtomicReference<RuntimeException> lastAsyncPtzFailure =
            new AtomicReference<>();
    private final Set<Integer> activeSessions = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<StreamKey, StreamResource> activeStreams =
            new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private boolean closed;
    private boolean cleanupComplete;

    private HikvisionCameraSdkService(HikvisionSdkRuntime runtime) {
        this(runtime, runtime.options().fileSearchTimeout());
    }

    private HikvisionCameraSdkService(
            HikvisionSdkRuntime runtime, java.time.Duration playbackQueryTimeout) {
        this.runtime = runtime;
        this.nativeApi = runtime.nativeApi();
        this.playbackCalendarQuery = new HikvisionPlaybackCalendarQuery(
                nativeApi, playbackQueryTimeout);
        this.asyncPtzSlots = new Semaphore(
                runtime.options().asyncPtzQueueCapacity() + 1);
        this.ptzExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(runtime.options().asyncPtzQueueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "hikvision-ptz-worker");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * @param options SDK 配置
     * @return 已初始化的海康服务
     */
    public static HikvisionCameraSdkService open(HikvisionSdkOptions options) {
        return new HikvisionCameraSdkService(HikvisionSdkRuntime.open(options));
    }

    static HikvisionCameraSdkService createForTesting(HikvisionSdkRuntime runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        return new HikvisionCameraSdkService(runtime);
    }

    static HikvisionCameraSdkService createForTesting(
            HikvisionSdkRuntime runtime, java.time.Duration playbackQueryTimeout) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        if (playbackQueryTimeout == null || playbackQueryTimeout.isNegative()
                || playbackQueryTimeout.isZero()) {
            throw new IllegalArgumentException("playbackQueryTimeout must be positive");
        }
        return new HikvisionCameraSdkService(runtime, playbackQueryTimeout);
    }

    @Override
    public String product() {
        return PRODUCT;
    }

    @Override
    public LoggedDomain login(LoginDomain login) {
        return withOpen(() -> loginInternal(login));
    }

    private LoggedDomain loginInternal(LoginDomain login) {
        validateLogin(login);
        HikvisionNativeLoginResult result = nativeApi.login(login);
        if (result == null || result.userId() < 0) {
            throw failure("Hikvision device login failed");
        }
        activeSessions.add(result.userId());
        return new LoggedDomain()
                .setUserId(String.valueOf(result.userId()))
                .setChannelNo(String.valueOf(result.startChannel()))
                .setDeviceType(String.valueOf(result.deviceType()))
                .setDeviceCategory(String.valueOf(result.deviceCategory()))
                .setDeviceId(result.serialNumber())
                .setLoginTime(LocalDateTime.now());
    }

    @Override
    public void logout(String userId) {
        withOpen(() -> {
            int handle;
            try {
                handle = Integer.parseInt(userId);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "userId must be a valid integer handle", exception);
            }
            logoutHandle(handle);
        });
    }

    @Override
    public boolean syncControl(DeviceDomain device, PTZControlDomain control) {
        return withOpen(() -> {
            validatePtz(device, control);
            return executeControl(device, control);
        });
    }

    @Override
    public boolean asyncControl(DeviceDomain device, PTZControlDomain control) {
        return withOpen(() -> {
            validatePtz(device, control);
            if (!asyncPtzSlots.tryAcquire()) {
                return false;
            }
            Integer handle = null;
            try {
                LoggedDomain logged = loginInternal(device.toLoginDomain());
                handle = Integer.parseInt(logged.getUserId());
                PtzTask task = new PtzTask(
                        handle,
                        resolveChannel(logged.getChannelNo(), device.getChannel()),
                        resolveCommand(control.getCommand()),
                        control.getSpeed(1, 7),
                        control.getIsBegin(),
                        control.getDuration());
                ptzExecutor.execute(() -> executeAcceptedControl(task));
                return true;
            } catch (RejectedExecutionException exception) {
                cleanupRejectedAsyncHandle(handle, null);
                return false;
            } catch (RuntimeException exception) {
                cleanupRejectedAsyncHandle(handle, exception);
                throw exception;
            } catch (LinkageError error) {
                cleanupRejectedAsyncHandle(handle, error);
                throw error;
            }
        });
    }

    /**
     * 获取最近一次已接受异步 PTZ 任务的执行失败。
     *
     * @return 尚未失败时为空；新失败会替换旧失败
     */
    public Optional<RuntimeException> lastAsyncPtzFailure() {
        return Optional.ofNullable(lastAsyncPtzFailure.get());
    }

    @Override
    public List<PlaybackTimePeriodDomain> playbackRecordExistByMonth(
            DeviceDomain device, PlayDomain request, int year, int month) {
        return withOpen(() -> {
            int streamType = validatePlaybackQuery(device, request, year, month);
            LoggedDomain logged = loginInternal(device.toLoginDomain());
            int userId = Integer.parseInt(logged.getUserId());
            RuntimeException primaryFailure = null;
            try {
                int channel = resolveChannel(logged.getChannelNo(), device.getChannel());
                return playbackCalendarQuery.execute(
                        userId, channel, streamType, YearMonth.of(year, month));
            } catch (RuntimeException exception) {
                primaryFailure = exception;
                throw exception;
            } finally {
                try {
                    logoutHandle(userId);
                } catch (RuntimeException cleanupFailure) {
                    suppressOrThrow(primaryFailure, cleanupFailure);
                }
            }
        });
    }

    /**
     * 启动海康实时预览会话。
     *
     * @param device 设备信息
     * @param request 取流参数
     * @param handler 码流数据处理器
     * @return 可关闭的实时预览会话
     */
    @Override
    public HikvisionStreamSession realPlay(
            DeviceDomain device, PlayDomain request, HikvisionStreamDataHandler handler) {
        return withOpen(() -> startStream(
                device, request, handler, HikvisionStreamSession.Type.REAL_PLAY));
    }

    /**
     * 启动海康按时间历史回放会话。
     *
     * @param device 设备信息
     * @param request 回放和取流参数
     * @param handler 码流数据处理器
     * @return 可关闭的历史回放会话
     */
    @Override
    public HikvisionStreamSession playback(
            DeviceDomain device, PlayDomain request, HikvisionStreamDataHandler handler) {
        return withOpen(() -> startStream(
                device, request, handler, HikvisionStreamSession.Type.PLAYBACK));
    }

    private HikvisionStreamSession startStream(
            DeviceDomain device, PlayDomain request, HikvisionStreamDataHandler handler,
            HikvisionStreamSession.Type type) {
        StreamParameters parameters = validateStream(device, request, handler, type);
        LoggedDomain logged = loginInternal(device.toLoginDomain());
        int loginHandle = Integer.parseInt(logged.getUserId());
        RuntimeException primaryFailure = null;
        try {
            int channel = resolveChannel(logged.getChannelNo(), device.getChannel());
            HikvisionNativeStreamCallback callback = createStreamCallback(handler);
            long streamHandle;
            try {
                streamHandle = startNativeStream(loginHandle, channel, parameters, callback);
            } catch (HikvisionNativeStreamStartException exception) {
                registerStream(exception.streamHandle(), loginHandle, type, callback);
                throw failure(type == HikvisionStreamSession.Type.REAL_PLAY
                        ? "Hikvision real-time preview startup cleanup failed"
                        : "Hikvision playback startup cleanup failed");
            }
            if (streamHandle < 0L) {
                throw failure(type == HikvisionStreamSession.Type.REAL_PLAY
                        ? "Hikvision real-time preview failed" : "Hikvision playback failed");
            }
            registerStream(streamHandle, loginHandle, type, callback);
            return new HikvisionStreamSession(streamHandle, type,
                    () -> closeStream(streamHandle, type));
        } catch (RuntimeException exception) {
            primaryFailure = exception;
            throw exception;
        } catch (LinkageError error) {
            cleanupStreamLoginAfterLinkageError(loginHandle, error);
            throw error;
        } finally {
            if (primaryFailure != null && !ownsLogin(loginHandle)) {
                cleanupStreamLogin(loginHandle, primaryFailure);
            }
        }
    }

    /**
     * 根据会话类型调用对应的原生取流入口。
     *
     * @param loginHandle 临时设备登录句柄
     * @param channel 原生通道号
     * @param parameters 已校验取流参数
     * @param callback 原生码流回调
     * @return 原生播放句柄，失败时为负值
     */
    private long startNativeStream(
            int loginHandle, int channel, StreamParameters parameters,
            HikvisionNativeStreamCallback callback) {
        if (parameters.type() == HikvisionStreamSession.Type.REAL_PLAY) {
            return nativeApi.startRealPlay(loginHandle,
                    new HikvisionPreviewRequest(channel, parameters.streamType(),
                            parameters.protocolType()), callback);
        }
        return nativeApi.startPlayback(loginHandle,
                new HikvisionPlaybackRequest(channel, parameters.streamType(),
                        parameters.beginTime(), parameters.endTime()), callback);
    }

    /**
     * 创建不会向原生边界传播业务异常的码流回调。
     *
     * @param handler 业务码流处理器
     * @return 内部原生回调
     */
    private HikvisionNativeStreamCallback createStreamCallback(HikvisionStreamDataHandler handler) {
        return (streamHandle, dataType, data) -> {
            try {
                handler.onData(new HikvisionStreamData(streamHandle, dataType, data));
            } catch (RuntimeException ignored) {
                // Native callback boundaries must never propagate consumer failures.
            }
        };
    }

    /**
     * 登记活动取流资源，并拒绝厂商 SDK 返回的重复句柄。
     *
     * @param streamHandle 原生播放句柄
     * @param loginHandle 临时设备登录句柄
     * @param type 取流会话类型
     * @param callback 原生回调强引用
     */
    private void registerStream(
            long streamHandle, int loginHandle, HikvisionStreamSession.Type type,
            HikvisionNativeStreamCallback callback) {
        StreamKey key = new StreamKey(type, streamHandle);
        StreamResource resource = new StreamResource(loginHandle, type, callback);
        StreamResource existing = activeStreams.putIfAbsent(key, resource);
        if (existing == null) {
            return;
        }
        existing.addLogin(loginHandle);
        IllegalStateException duplicate = new IllegalStateException(
                "Hikvision SDK returned a duplicate stream handle");
        try {
            closeStreamResource(key);
        } catch (RuntimeException cleanupFailure) {
            duplicate.addSuppressed(cleanupFailure);
        }
        throw duplicate;
    }

    /**
     * 判断活动流是否拥有指定临时登录。
     *
     * @param loginHandle 临时设备登录句柄
     * @return 已由活动流持有时返回 true
     */
    private boolean ownsLogin(int loginHandle) {
        return activeStreams.values().stream()
                .anyMatch(resource -> resource.ownsLogin(loginHandle));
    }

    /**
     * 清理启动失败后未被活动流持有的临时登录。
     *
     * @param loginHandle 临时设备登录句柄
     * @param primaryFailure 启动阶段主异常
     */
    private void cleanupStreamLogin(int loginHandle, RuntimeException primaryFailure) {
        if (!activeSessions.contains(loginHandle)) {
            return;
        }
        try {
            logoutHandle(loginHandle);
        } catch (RuntimeException cleanupFailure) {
            primaryFailure.addSuppressed(cleanupFailure);
        }
    }

    private void cleanupStreamLoginAfterLinkageError(int loginHandle, LinkageError primaryFailure) {
        try {
            logoutHandle(loginHandle);
        } catch (RuntimeException | LinkageError cleanupFailure) {
            primaryFailure.addSuppressed(cleanupFailure);
        }
    }

    /**
     * 在生命周期读锁保护下关闭指定取流会话。
     *
     * @param streamHandle 原生播放句柄
     * @param type 取流会话类型
     */
    private void closeStream(long streamHandle, HikvisionStreamSession.Type type) {
        Lock readLock = lifecycleLock.readLock();
        readLock.lock();
        try {
            closeStreamResource(new StreamKey(type, streamHandle));
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 停止原生取流并注销对应临时设备登录。
     *
     * @param key 取流资源键
     */
    private void closeStreamResource(StreamKey key) {
        StreamResource resource = activeStreams.get(key);
        if (resource == null) {
            return;
        }
        synchronized (resource) {
            if (!resource.streamStopped) {
                stopNativeStream(key.handle(), resource.type);
                resource.streamStopped = true;
            }
            resource.logoutAll(this::logoutHandle);
            activeStreams.remove(key, resource);
        }
    }

    /**
     * 根据会话类型停止原生取流。
     *
     * @param streamHandle 原生播放句柄
     * @param type 取流会话类型
     */
    private void stopNativeStream(long streamHandle, HikvisionStreamSession.Type type) {
        boolean stopped = type == HikvisionStreamSession.Type.REAL_PLAY
                ? nativeApi.stopRealPlay(streamHandle) : nativeApi.stopPlayback(streamHandle);
        if (!stopped) {
            throw failure(type == HikvisionStreamSession.Type.REAL_PLAY
                    ? "Hikvision real-time preview stop failed" : "Hikvision playback stop failed");
        }
    }

    private static int validatePlaybackQuery(
            DeviceDomain device, PlayDomain request, int year, int month) {
        if (device == null) {
            throw new IllegalArgumentException("device must not be null");
        }
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("year must be between 1 and 9999");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }
        if (request == null || request.getTakeStreamParam() == null
                || request.getTakeStreamParam().getStreamType() == null) {
            throw new IllegalArgumentException("takeStreamParam.streamType must not be null");
        }
        int streamType = request.getTakeStreamParam().getStreamType();
        if (streamType < 0 || (streamType > 2 && streamType != 255)) {
            throw new IllegalArgumentException("streamType must be 0, 1, 2, or 255");
        }
        return streamType;
    }

    private boolean executeControl(DeviceDomain device, PTZControlDomain control) {
        LoggedDomain logged = loginInternal(device.toLoginDomain());
        int handle = Integer.parseInt(logged.getUserId());
        RuntimeException primaryFailure = null;
        try {
            int channel = resolveChannel(logged.getChannelNo(), device.getChannel());
            int command = resolveCommand(control.getCommand());
            int speed = control.getSpeed(1, 7);
            return executeControl(
                    handle,
                    channel,
                    command,
                    speed,
                    control.getIsBegin(),
                    control.getDuration());
        } catch (RuntimeException exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            try {
                logoutHandle(handle);
            } catch (RuntimeException cleanupFailure) {
                suppressOrThrow(primaryFailure, cleanupFailure);
            }
        }
    }

    private boolean executeControl(
            int handle,
            int channel,
            int command,
            int speed,
            Boolean isBegin,
            java.time.Duration duration) {
        if (isBegin != null) {
            return executePtz(handle, channel, command, isBegin ? 0 : 1, speed);
        }
        boolean started = executePtz(handle, channel, command, 0, speed);
        RuntimeException primaryFailure = null;
        try {
            sleep(duration);
            return started;
        } catch (RuntimeException exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            try {
                executePtz(handle, channel, command, 1, speed);
            } catch (RuntimeException cleanupFailure) {
                suppressOrThrow(primaryFailure, cleanupFailure);
            }
        }
    }

    private void executeAcceptedControl(PtzTask task) {
        RuntimeException primaryFailure = null;
        try {
            executeControl(
                    task.handle(),
                    task.channel(),
                    task.command(),
                    task.speed(),
                    task.isBegin(),
                    task.duration());
        } catch (RuntimeException exception) {
            primaryFailure = exception;
        } catch (LinkageError error) {
            primaryFailure = new IllegalStateException(
                    "Hikvision native PTZ execution failed", error);
        } finally {
            primaryFailure = logoutAcceptedControl(task.handle(), primaryFailure);
            if (primaryFailure != null) {
                lastAsyncPtzFailure.set(primaryFailure);
            }
            asyncPtzSlots.release();
        }
    }

    private RuntimeException logoutAcceptedControl(
            int handle, RuntimeException primaryFailure) {
        try {
            logoutHandle(handle);
            return primaryFailure;
        } catch (RuntimeException | LinkageError cleanupFailure) {
            return mergeFailure(primaryFailure, asRuntimeFailure(cleanupFailure));
        }
    }

    private void cleanupRejectedAsyncHandle(Integer handle, Throwable primaryFailure) {
        try {
            if (handle != null) {
                logoutHandle(handle);
            }
        } catch (RuntimeException | LinkageError cleanupFailure) {
            if (primaryFailure != null) {
                primaryFailure.addSuppressed(cleanupFailure);
            } else {
                throw cleanupFailure;
            }
        } finally {
            asyncPtzSlots.release();
        }
    }

    @Override
    public void close() {
        Lock writeLock = lifecycleLock.writeLock();
        writeLock.lock();
        try {
            if (cleanupComplete) {
                return;
            }
            closed = true;
            RuntimeException firstFailure = null;
            ptzExecutor.shutdown();
            try {
                if (!ptzExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    ptzExecutor.shutdownNow();
                    if (!ptzExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                        firstFailure = new IllegalStateException(
                                "Hikvision PTZ executor did not terminate");
                    }
                }
            } catch (InterruptedException exception) {
                ptzExecutor.shutdownNow();
                Thread.currentThread().interrupt();
                firstFailure = new IllegalStateException(
                        "Interrupted while closing Hikvision PTZ executor", exception);
            }
            for (StreamKey key : activeStreams.keySet().stream()
                    .sorted(StreamKey.ORDERING).toList()) {
                try {
                    closeStreamResource(key);
                } catch (RuntimeException | LinkageError exception) {
                    firstFailure = mergeFailure(firstFailure, asRuntimeFailure(exception));
                }
            }
            for (Integer handle : Set.copyOf(activeSessions)) {
                if (ownsLogin(handle)) {
                    continue;
                }
                try {
                    logoutHandle(handle);
                } catch (RuntimeException | LinkageError exception) {
                    firstFailure = mergeFailure(firstFailure, asRuntimeFailure(exception));
                }
            }
            boolean runtimeClosed = false;
            if (activeStreams.isEmpty() && activeSessions.isEmpty()) {
                try {
                    runtime.close();
                    runtimeClosed = true;
                } catch (RuntimeException | LinkageError exception) {
                    firstFailure = mergeFailure(firstFailure, asRuntimeFailure(exception));
                }
            }
            cleanupComplete = runtimeClosed;
            if (firstFailure != null) {
                throw firstFailure;
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void logoutHandle(int handle) {
        if (!nativeApi.logout(handle)) {
            throw failure("Hikvision device logout failed");
        }
        activeSessions.remove(handle);
    }

    private HikvisionSdkException failure(String operation) {
        return new HikvisionSdkException(operation, nativeApi.lastError());
    }

    private boolean executePtz(int handle, int channel, int command, int stop, int speed) {
        if (!nativeApi.ptzControl(handle, channel, command, stop, speed)) {
            throw failure("Hikvision PTZ control failed");
        }
        return true;
    }

    private static int resolveChannel(String startChannel, String logicalChannel) {
        int start = parsePositiveInt(startChannel, "startChannel");
        if (logicalChannel == null || logicalChannel.isBlank()) {
            return start;
        }
        int logical = parsePositiveInt(logicalChannel, "channel");
        return Math.addExact(start, logical - 1);
    }

    private static int parsePositiveInt(String value, String field) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a positive integer", exception);
        }
        if (parsed <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return parsed;
    }

    private static int resolveCommand(PtzControlCommandEnums command) {
        return switch (command) {
            case ZOOM_IN -> 11;
            case ZOOM_OUT -> 12;
            case FOCUS_NEAR -> 13;
            case FOCUS_FAR -> 14;
            case IRIS_OPEN -> 15;
            case IRIS_CLOSE -> 16;
            case UP -> 21;
            case DOWN -> 22;
            case LEFT -> 23;
            case RIGHT -> 24;
            case LEFT_UP -> 25;
            case RIGHT_UP -> 26;
            case LEFT_DOWN -> 27;
            case RIGHT_DOWN -> 28;
        };
    }

    private static void validatePtz(DeviceDomain device, PTZControlDomain control) {
        if (device == null) {
            throw new IllegalArgumentException("device must not be null");
        }
        if (control == null) {
            throw new IllegalArgumentException("control must not be null");
        }
        if (control.getCommand() == null) {
            throw new IllegalArgumentException("PTZ command must not be null");
        }
        if (control.getIsBegin() == null
                && (control.getDuration() == null || control.getDuration().isNegative())) {
            throw new IllegalArgumentException("PTZ duration must not be null or negative");
        }
    }

    private static void sleep(java.time.Duration duration) {
        if (duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis(), duration.toNanosPart() % 1_000_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Hikvision PTZ duration was interrupted", exception);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Hikvision SDK service is closed");
        }
    }

    private <T> T withOpen(Supplier<T> action) {
        Lock readLock = lifecycleLock.readLock();
        readLock.lock();
        try {
            ensureOpen();
            return action.get();
        } finally {
            readLock.unlock();
        }
    }

    private void withOpen(Runnable action) {
        withOpen(() -> {
            action.run();
            return null;
        });
    }

    private static void suppressOrThrow(
            RuntimeException primaryFailure, RuntimeException cleanupFailure) {
        if (primaryFailure != null) {
            primaryFailure.addSuppressed(cleanupFailure);
            return;
        }
        throw cleanupFailure;
    }

    private static RuntimeException mergeFailure(
            RuntimeException firstFailure, RuntimeException nextFailure) {
        if (firstFailure == null) {
            return nextFailure;
        }
        firstFailure.addSuppressed(nextFailure);
        return firstFailure;
    }

    private static RuntimeException asRuntimeFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Hikvision native resource cleanup failed", failure);
    }

    /**
     * 校验并规范实时预览或历史回放参数。
     *
     * @param device 设备信息
     * @param request 播放参数
     * @param handler 码流处理器
     * @param type 取流会话类型
     * @return 已校验的内部取流参数
     */
    private static StreamParameters validateStream(
            DeviceDomain device, PlayDomain request, HikvisionStreamDataHandler handler,
            HikvisionStreamSession.Type type) {
        if (device == null) {
            throw new IllegalArgumentException("device must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        if (request == null || request.getTakeStreamParam() == null) {
            throw new IllegalArgumentException("takeStreamParam must not be null");
        }
        int streamType = validateStreamType(request.getTakeStreamParam().getStreamType(), type);
        if (type == HikvisionStreamSession.Type.REAL_PLAY) {
            int protocolType = parseProtocolType(request.getTakeStreamParam().getByProtoType());
            return new StreamParameters(type, streamType, protocolType, null, null);
        }
        PlayDomain.PlaybackParam playback = validatePlayback(request.getPlaybackParam());
        return new StreamParameters(type, streamType, 0,
                playback.getBeginTime(), playback.getEndTime());
    }

    /**
     * 校验厂商码流类型。
     *
     * @param value 码流类型
     * @param type 取流会话类型
     * @return 合法码流类型
     */
    private static int validateStreamType(Integer value, HikvisionStreamSession.Type type) {
        if (value == null) {
            throw new IllegalArgumentException("takeStreamParam.streamType must not be null");
        }
        int maximum = type == HikvisionStreamSession.Type.REAL_PLAY ? 10 : 2;
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException("streamType is outside the Hikvision range");
        }
        return value;
    }

    /**
     * 解析海康应用层取流协议类型。
     *
     * @param value 协议类型字符串
     * @return 协议类型数值
     */
    private static int parseProtocolType(String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("byProtoType must be 0 or 1", exception);
        }
        if (parsed != 0 && parsed != 1) {
            throw new IllegalArgumentException("byProtoType must be 0 or 1");
        }
        return parsed;
    }

    /**
     * 校验按时间回放参数；首版只支持正常一倍速正放。
     *
     * @param playback 历史回放参数
     * @return 已校验回放参数
     */
    private static PlayDomain.PlaybackParam validatePlayback(PlayDomain.PlaybackParam playback) {
        if (playback == null || playback.getBeginTime() == null || playback.getEndTime() == null) {
            throw new IllegalArgumentException("playback beginTime and endTime must not be null");
        }
        if (!playback.getEndTime().isAfter(playback.getBeginTime())) {
            throw new IllegalArgumentException("playback endTime must be after beginTime");
        }
        if (playback.getMultiplier() != null
                && Double.compare(playback.getMultiplier(), 1.0D) != 0) {
            throw new IllegalArgumentException("playback multiplier is not supported");
        }
        return playback;
    }

    private static void validateLogin(LoginDomain login) {
        if (login == null) {
            throw new IllegalArgumentException("login must not be null");
        }
        if (login.getIp() == null || login.getIp().isBlank()) {
            throw new IllegalArgumentException("ip must not be blank");
        }
        if (login.getUsername() == null) {
            throw new IllegalArgumentException("username must not be null");
        }
        if (login.getPassword() == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        int port;
        try {
            port = Integer.parseInt(login.getPort());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("port must be between 1 and 65535", exception);
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
    }

    private record PtzTask(
            int handle,
            int channel,
            int command,
            int speed,
            Boolean isBegin,
            java.time.Duration duration) {
    }

    /**
     * 已校验的内部取流参数。
     *
     * @param type 取流会话类型
     * @param streamType 厂商码流类型
     * @param protocolType 厂商应用层协议类型
     * @param beginTime 历史回放开始时间
     * @param endTime 历史回放结束时间
     */
    private record StreamParameters(
            HikvisionStreamSession.Type type,
            int streamType,
            int protocolType,
            LocalDateTime beginTime,
            LocalDateTime endTime) {
    }

    /**
     * 区分不同 HCNetSDK 播放 API 命名空间中的原生句柄。
     *
     * @param type 取流会话类型
     * @param handle 原生播放句柄
     */
    private record StreamKey(HikvisionStreamSession.Type type, long handle) {
        /** 确保服务关闭时的资源释放顺序稳定。 */
        private static final java.util.Comparator<StreamKey> ORDERING =
                java.util.Comparator.comparing(StreamKey::type)
                        .thenComparingLong(StreamKey::handle);
    }

    private static final class StreamResource {
        /** 临时设备登录句柄。 */
        private final Deque<Integer> loginHandles = new ArrayDeque<>();
        /** 取流会话类型。 */
        private final HikvisionStreamSession.Type type;
        /** 强引用内部回调，防止原生取流期间被回收。 */
        private final HikvisionNativeStreamCallback callback;
        /** 原生流是否已经停止。 */
        private boolean streamStopped;

        /**
         * 创建活动取流资源记录。
         *
         * @param loginHandle 临时设备登录句柄
         * @param type 取流会话类型
         * @param callback 原生回调强引用
         */
        private StreamResource(
                int loginHandle, HikvisionStreamSession.Type type,
                HikvisionNativeStreamCallback callback) {
            loginHandles.addLast(loginHandle);
            this.type = type;
            this.callback = callback;
        }

        private synchronized void addLogin(int loginHandle) {
            loginHandles.addLast(loginHandle);
        }

        private synchronized boolean ownsLogin(int loginHandle) {
            return loginHandles.contains(loginHandle);
        }

        private void logoutAll(java.util.function.IntConsumer logoutAction) {
            while (!loginHandles.isEmpty()) {
                logoutAction.accept(loginHandles.getFirst());
                loginHandles.removeFirst();
            }
        }
    }
}
