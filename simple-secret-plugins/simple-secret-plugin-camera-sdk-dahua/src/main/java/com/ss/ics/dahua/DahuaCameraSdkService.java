package com.ss.ics.dahua;

import com.ss.ics.constants.enums.PtzControlCommandEnums;
import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.LoggedDomain;
import com.ss.ics.domain.LoginDomain;
import com.ss.ics.domain.PTZControlDomain;
import com.ss.ics.service.DeviceLoginService;
import com.ss.ics.service.PlayService;
import com.ss.ics.service.PtzControlService;
import com.ss.ics.dahua.internal.DahuaNativeApi;
import com.ss.ics.dahua.internal.model.DahuaNativeLoginResult;
import com.ss.ics.dahua.internal.model.DahuaNativeRadiometryRecord;
import com.ss.ics.dahua.internal.model.DahuaNativeRegionTemperature;
import com.ss.ics.dahua.internal.model.DahuaNativeSearchStart;
import com.ss.ics.dahua.internal.model.DahuaNativeStreamFrame;
import com.ss.ics.dahua.internal.model.DahuaNativeTemperatureSummary;
import com.ss.ics.dahua.internal.model.DahuaNativeThermalData;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.function.BiFunction;

/** 大华 SDK 登录和 PTZ 能力的统一生命周期入口。 */
public final class DahuaCameraSdkService
        implements DeviceLoginService, PtzControlService,
        PlayService<DahuaRealPlaySession, DahuaStreamCallback>, AutoCloseable {
    /** basic plugin 使用的厂商产品编码。 */
    public static final String PRODUCT = "Dahua";

    private final DahuaSdkRuntime runtime;
    private final DahuaNativeApi nativeApi;
    private final ThreadPoolExecutor ptzExecutor;
    private final Semaphore asyncPtzSlots;
    private final ConcurrentHashMap<Long, AtomicInteger> activeSessions =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PreviewResource> activePreviews =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ThermalResource> activeThermalSubscriptions =
            new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private boolean closed;
    private boolean cleanupComplete;

    private DahuaCameraSdkService(DahuaSdkRuntime runtime) {
        this.runtime = runtime;
        this.nativeApi = runtime.nativeApi();
        this.asyncPtzSlots = new Semaphore(runtime.options().asyncPtzQueueCapacity() + 1);
        this.ptzExecutor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(runtime.options().asyncPtzQueueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "dahua-ptz-worker");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * @param options SDK 配置
     *
     * @return 当前对象
     */
    public static DahuaCameraSdkService open(DahuaSdkOptions options) {
        return new DahuaCameraSdkService(DahuaSdkRuntime.open(options));
    }

    static DahuaCameraSdkService createForTesting(DahuaSdkRuntime runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        return new DahuaCameraSdkService(runtime);
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
        DahuaNativeLoginResult result = nativeApi.login(login);
        if (result == null || result.userId() == 0) {
            throw failure("Dahua device login failed");
        }
        activeSessions.compute(result.userId(), (handle, count) -> {
            if (count == null) {
                return new AtomicInteger(1);
            }
            count.incrementAndGet();
            return count;
        });
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
        withOpen(() -> logoutHandle(parseHandle(userId)));
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
        Lock readLock = lifecycleLock.readLock();
        readLock.lock();
        try {
            if (closed) {
                return false;
            }
            validatePtz(device, control);
            if (!asyncPtzSlots.tryAcquire()) {
                return false;
            }
            Long handle = null;
            try {
                LoggedDomain logged = loginInternal(device.toLoginDomain());
                handle = parseHandle(logged.getUserId());
                PtzParameters parameters = resolveParameters(control);
                PtzTask task = new PtzTask(
                        handle,
                        resolveChannel(logged.getChannelNo(), device.getChannel()),
                        parameters.command(), parameters.param1(), parameters.param2(),
                        parameters.param3(), control.getIsBegin(), control.getDuration());
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
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public DahuaRealPlaySession realPlay(
            DeviceDomain device, com.ss.ics.domain.PlayDomain request,
            DahuaStreamCallback callback) {
        return withOpen(() -> startPreview(device, request, callback));
    }

    private DahuaRealPlaySession startPreview(
            DeviceDomain device, com.ss.ics.domain.PlayDomain request,
            DahuaStreamCallback callback) {
        int streamType = validatePreview(device, request, callback);
        LoggedDomain logged = loginInternal(device.toLoginDomain());
        long loginHandle = parseHandle(logged.getUserId());
        RuntimeException primaryFailure = null;
        try {
            int channel = resolveChannel(logged.getChannelNo(), device.getChannel());
            long previewHandle = nativeApi.startPreview(
                    loginHandle, channel, streamType, frame -> callback.onFrame(
                            new DahuaStreamFrame(frame.data(), frame.pts(), frame.dts(),
                                    frame.frameType(), frame.frameSubType())));
            if (previewHandle == 0) {
                throw failure("Dahua real-time preview failed");
            }
            PreviewResource resource = new PreviewResource(previewHandle, loginHandle);
            PreviewResource existing = activePreviews.putIfAbsent(previewHandle, resource);
            if (existing != null) {
                existing.addLogin(loginHandle);
                IllegalStateException duplicate = new IllegalStateException(
                        "Dahua SDK returned a duplicate preview handle");
                try {
                    closePreviewResource(previewHandle);
                } catch (RuntimeException cleanupFailure) {
                    duplicate.addSuppressed(cleanupFailure);
                }
                throw duplicate;
            }
            return new DahuaRealPlaySession(
                    previewHandle, () -> closePreview(previewHandle));
        } catch (RuntimeException exception) {
            primaryFailure = exception;
            throw exception;
        } catch (LinkageError error) {
            cleanupPreviewLoginAfterLinkageError(loginHandle, error);
            throw error;
        } finally {
            if (primaryFailure != null && !ownsLogin(loginHandle)) {
                try {
                    logoutHandle(loginHandle);
                } catch (RuntimeException cleanupFailure) {
                    primaryFailure.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    private void cleanupPreviewLoginAfterLinkageError(
            long loginHandle, LinkageError primaryFailure) {
        try {
            logoutHandle(loginHandle);
        } catch (RuntimeException | LinkageError cleanupFailure) {
            primaryFailure.addSuppressed(cleanupFailure);
        }
    }

    private boolean ownsLogin(long loginHandle) {
        return activePreviews.values().stream()
                .anyMatch(resource -> resource.ownsLogin(loginHandle));
    }

    private void closePreview(long previewHandle) {
        Lock readLock = lifecycleLock.readLock();
        readLock.lock();
        try {
            closePreviewResource(previewHandle);
        } finally {
            readLock.unlock();
        }
    }

    private void closePreviewResource(long previewHandle) {
        PreviewResource resource = activePreviews.get(previewHandle);
        if (resource == null) {
            return;
        }
        synchronized (resource) {
            if (!resource.previewStopped) {
                if (!nativeApi.stopPreview(previewHandle)) {
                    throw failure("Dahua real-time preview stop failed");
                }
                resource.previewStopped = true;
            }
            resource.logoutAll(this::logoutHandle);
            activePreviews.remove(previewHandle, resource);
        }
    }

    /**
     * 订阅设备热成像数据。
     *
     * @param device 设备信息
     * @param callback 热成像回调
     * @return 可抓取和关闭的订阅
     */
    public DahuaThermalSubscription subscribeThermal(
            DeviceDomain device, DahuaThermalCallback callback) {
        return withOpen(() -> startThermalSubscription(device, callback));
    }

    private DahuaThermalSubscription startThermalSubscription(
            DeviceDomain device, DahuaThermalCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("device must not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        LoggedDomain logged = loginInternal(device.toLoginDomain());
        long loginHandle = parseHandle(logged.getUserId());
        RuntimeException primaryFailure = null;
        try {
            int channel = resolveChannel(logged.getChannelNo(), device.getChannel());
            long subscriptionHandle = nativeApi.attachRadiometry(
                    loginHandle, channel, data -> {
                        try {
                            callback.onData(new DahuaThermalData(
                                    data.timestamp(), data.width(), data.height(),
                                    data.grayscale(), data.temperatures()));
                        } catch (RuntimeException ignored) {
                            // Native callback boundaries must never propagate consumer failures.
                        }
                    });
            if (subscriptionHandle == 0) {
                throw failure("Dahua radiometry attach failed");
            }
            ThermalResource resource =
                    new ThermalResource(subscriptionHandle, loginHandle, channel);
            ThermalResource existing =
                    activeThermalSubscriptions.putIfAbsent(subscriptionHandle, resource);
            if (existing != null) {
                existing.addLogin(loginHandle);
                IllegalStateException duplicate = new IllegalStateException(
                        "Dahua SDK returned a duplicate radiometry subscription handle");
                try {
                    closeThermalResource(subscriptionHandle);
                } catch (RuntimeException cleanupFailure) {
                    duplicate.addSuppressed(cleanupFailure);
                }
                throw duplicate;
            }
            return new DahuaThermalSubscription(
                    subscriptionHandle,
                    () -> fetchThermal(subscriptionHandle),
                    () -> closeThermal(subscriptionHandle));
        } catch (RuntimeException exception) {
            primaryFailure = exception;
            throw exception;
        } catch (LinkageError error) {
            cleanupThermalLoginAfterLinkageError(loginHandle, error);
            throw error;
        } finally {
            if (primaryFailure != null && !ownsThermalLogin(loginHandle)) {
                try {
                    logoutHandle(loginHandle);
                } catch (RuntimeException cleanupFailure) {
                    primaryFailure.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    private boolean ownsThermalLogin(long loginHandle) {
        return activeThermalSubscriptions.values().stream()
                .anyMatch(resource -> resource.ownsLogin(loginHandle));
    }

    private void cleanupThermalLoginAfterLinkageError(
            long loginHandle, LinkageError primaryFailure) {
        try {
            logoutHandle(loginHandle);
        } catch (RuntimeException | LinkageError cleanupFailure) {
            primaryFailure.addSuppressed(cleanupFailure);
        }
    }

    private int fetchThermal(long subscriptionHandle) {
        return withOpen(() -> {
            ThermalResource resource = activeThermalSubscriptions.get(subscriptionHandle);
            if (resource == null) {
                throw new IllegalStateException("Dahua thermal subscription is closed");
            }
            synchronized (resource) {
                if (resource.detached) {
                    throw new IllegalStateException("Dahua thermal subscription is closed");
                }
                return nativeApi.fetchRadiometry(resource.primaryLogin(), resource.channel);
            }
        });
    }

    private void closeThermal(long subscriptionHandle) {
        Lock readLock = lifecycleLock.readLock();
        readLock.lock();
        try {
            closeThermalResource(subscriptionHandle);
        } finally {
            readLock.unlock();
        }
    }

    private void closeThermalResource(long subscriptionHandle) {
        ThermalResource resource = activeThermalSubscriptions.get(subscriptionHandle);
        if (resource == null) {
            return;
        }
        synchronized (resource) {
            if (!resource.detached) {
                if (!nativeApi.detachRadiometry(subscriptionHandle)) {
                    throw failure("Dahua radiometry detach failed");
                }
                resource.detached = true;
            }
            resource.logoutAll(this::logoutHandle);
            activeThermalSubscriptions.remove(subscriptionHandle, resource);
        }
    }

    /**
     * 查询指定 8192 坐标点的温度统计。
     *
     * @param device 摄像机设备信息
     * @param x 像素横坐标
     * @param y 像素纵坐标
     * @return 返回的 {@code DahuaTemperatureSummary} 结果
     */
    public DahuaTemperatureSummary queryPointTemperature(DeviceDomain device, int x, int y) {
        DahuaPoint point = new DahuaPoint(x, y);
        return withOpen(() -> withTemporaryDeviceSession(device, (handle, channel) ->
                map(nativeApi.queryPointTemperature(handle, channel, point.x(), point.y()))));
    }

    /**
     * 查询已配置测温规则的温度统计。
     *
     * @param device 摄像机设备信息
     * @param presetId 预置点编号
     * @param ruleId 测温规则编号
     * @param meterType 厂商 SDK 测温类型
     * @return 返回的 {@code DahuaTemperatureSummary} 结果
     */
    public DahuaTemperatureSummary queryItemTemperature(
            DeviceDomain device, int presetId, int ruleId, int meterType) {
        validateMeterType(meterType);
        if (presetId < 0 || ruleId < 0) {
            throw new IllegalArgumentException("presetId and ruleId must not be negative");
        }
        return withOpen(() -> withTemporaryDeviceSession(device, (handle, channel) ->
                map(nativeApi.queryItemTemperature(
                        handle, channel, presetId, ruleId, meterType))));
    }

    /**
     * 查询 3 到 8 个点组成的任意区域温度。
     *
     * @param device 摄像机设备信息
     * @param points 坐标点集合
     * @return 返回的 {@code DahuaRegionTemperature} 结果
     */
    public DahuaRegionTemperature queryRegionTemperature(
            DeviceDomain device, List<DahuaPoint> points) {
        if (points == null || points.size() < 3 || points.size() > 8
                || points.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("points must contain between 3 and 8 values");
        }
        List<DahuaPoint> copied = List.copyOf(points);
        return withOpen(() -> withTemporaryDeviceSession(device, (handle, channel) -> {
            DahuaNativeRegionTemperature result =
                    nativeApi.queryRegionTemperature(handle, channel, copied);
            if (result == null) {
                throw failure("Dahua region temperature query failed");
            }
            return new DahuaRegionTemperature(
                    result.temperatureUnit(), result.average(), result.maximum(), result.minimum(),
                    result.maximumPoint(), result.minimumPoint());
        }));
    }

    /**
     * 查询历史热成像测温记录。
     *
     * @param device 设备
     * @param meterType 测温类型，0 到 3
     * @param period 保存周期，0、5、10、15 或 30
     * @param begin 开始时间
     * @param end 结束时间
     * @return 不超过配置上限的历史记录
     */
    public List<DahuaRadiometryRecord> searchRadiometry(
            DeviceDomain device, int meterType, int period,
            LocalDateTime begin, LocalDateTime end) {
        validateSearch(meterType, period, begin, end);
        return withOpen(() -> withTemporaryDeviceSession(device, (handle, channel) ->
                executeRadiometrySearch(handle, channel, meterType, period, begin, end)));
    }

    private List<DahuaRadiometryRecord> executeRadiometrySearch(
            long handle, int channel, int meterType, int period,
            LocalDateTime begin, LocalDateTime end) {
        long timeoutNanos = runtime.options().radiometrySearchTimeout().toNanos();
        long startedAt = System.nanoTime();
        DahuaNativeSearchStart search = nativeApi.startRadiometrySearch(
                handle, channel, meterType, period, begin, end);
        if (search == null || search.finderHandle() <= 0) {
            throw failure("Dahua radiometry search start failed");
        }
        RuntimeException primaryFailure = null;
        try {
            if (search.totalCount() < 0) {
                throw failure("Dahua radiometry search start failed");
            }
            if (search.totalCount() > runtime.options().maxRadiometryResults()) {
                throw failure("Dahua radiometry search exceeds result limit");
            }
            List<DahuaRadiometryRecord> results = new ArrayList<>(search.totalCount());
            int offset = 0;
            while (offset < search.totalCount()) {
                ensureWithinDeadline(startedAt, timeoutNanos);
                int count = Math.min(32, search.totalCount() - offset);
                List<DahuaNativeRadiometryRecord> page =
                        nativeApi.findRadiometryPage(handle, search.finderHandle(), offset, count);
                ensureWithinDeadline(startedAt, timeoutNanos);
                if (page == null) {
                    throw failure("Dahua radiometry search page failed");
                }
                if (page.isEmpty()) {
                    break;
                }
                for (DahuaNativeRadiometryRecord record : page) {
                    results.add(map(record));
                    if (results.size() > runtime.options().maxRadiometryResults()) {
                        throw failure("Dahua radiometry search exceeds result limit");
                    }
                }
                offset = Math.addExact(offset, page.size());
            }
            return List.copyOf(results);
        } catch (RuntimeException exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            try {
                if (!nativeApi.stopRadiometrySearch(handle, search.finderHandle())) {
                    throw failure("Dahua radiometry search stop failed");
                }
            } catch (RuntimeException cleanupFailure) {
                suppressOrThrow(primaryFailure, cleanupFailure);
            }
        }
    }

    private <T> T withTemporaryDeviceSession(
            DeviceDomain device, BiFunction<Long, Integer, T> action) {
        if (device == null) {
            throw new IllegalArgumentException("device must not be null");
        }
        LoggedDomain logged = loginInternal(device.toLoginDomain());
        long handle = parseHandle(logged.getUserId());
        RuntimeException primaryFailure = null;
        try {
            return action.apply(handle, resolveChannel(logged.getChannelNo(), device.getChannel()));
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

    private DahuaTemperatureSummary map(DahuaNativeTemperatureSummary result) {
        if (result == null) {
            throw failure("Dahua temperature query failed");
        }
        return new DahuaTemperatureSummary(
                result.meterType(), result.temperatureUnit(), result.average(),
                result.maximum(), result.minimum(), result.middle(), result.standardDeviation());
    }

    private DahuaRadiometryRecord map(DahuaNativeRadiometryRecord result) {
        if (result == null || result.temperature() == null) {
            throw failure("Dahua radiometry search returned invalid data");
        }
        return new DahuaRadiometryRecord(
                result.timestamp(), result.presetId(), result.ruleId(), result.name(),
                result.channel(), map(result.temperature()), result.coordinates());
    }

    private static void ensureWithinDeadline(long startedAt, long timeoutNanos) {
        if (System.nanoTime() - startedAt >= timeoutNanos) {
            throw new DahuaSdkException("Dahua radiometry search timed out", -1);
        }
    }

    private static void validateMeterType(int meterType) {
        if (meterType < 0 || meterType > 3) {
            throw new IllegalArgumentException("meterType must be between 0 and 3");
        }
    }

    private static void validateSearch(
            int meterType, int period, LocalDateTime begin, LocalDateTime end) {
        validateMeterType(meterType);
        if (period != 0 && period != 5 && period != 10 && period != 15 && period != 30) {
            throw new IllegalArgumentException("period must be 0, 5, 10, 15, or 30");
        }
        if (begin == null || end == null || !begin.isBefore(end)) {
            throw new IllegalArgumentException("begin must be before end");
        }
    }

    private boolean executeControl(DeviceDomain device, PTZControlDomain control) {
        LoggedDomain logged = loginInternal(device.toLoginDomain());
        long handle = parseHandle(logged.getUserId());
        RuntimeException primaryFailure = null;
        try {
            PtzParameters parameters = resolveParameters(control);
            return executeControl(handle,
                    resolveChannel(logged.getChannelNo(), device.getChannel()),
                    parameters, control.getIsBegin(), control.getDuration());
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
            long handle, int channel, PtzParameters parameters,
            Boolean isBegin, Duration duration) {
        if (isBegin != null) {
            return executePtz(handle, channel, parameters, isBegin ? 0 : 1);
        }
        boolean started = executePtz(handle, channel, parameters, 0);
        RuntimeException primaryFailure = null;
        try {
            sleep(duration);
            return started;
        } catch (RuntimeException exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            try {
                executePtz(handle, channel, parameters, 1);
            } catch (RuntimeException cleanupFailure) {
                suppressOrThrow(primaryFailure, cleanupFailure);
            }
        }
    }

    private boolean executePtz(
            long handle, int channel, PtzParameters parameters, int stop) {
        if (!nativeApi.ptzControl(handle, channel, parameters.command(),
                parameters.param1(), parameters.param2(), parameters.param3(), stop)) {
            throw failure("Dahua PTZ control failed");
        }
        return true;
    }

    private void executeAcceptedControl(PtzTask task) {
        RuntimeException primaryFailure = null;
        try {
            executeControl(task.handle(), task.channel(), task.parameters(),
                    task.isBegin(), task.duration());
        } catch (RuntimeException exception) {
            primaryFailure = exception;
        } finally {
            try {
                logoutHandle(task.handle());
            } catch (RuntimeException cleanupFailure) {
                suppressOrThrow(primaryFailure, cleanupFailure);
            } finally {
                asyncPtzSlots.release();
            }
        }
    }

    private void cleanupRejectedAsyncHandle(Long handle, Throwable primaryFailure) {
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
                                "Dahua PTZ executor did not terminate");
                    }
                }
            } catch (InterruptedException exception) {
                ptzExecutor.shutdownNow();
                Thread.currentThread().interrupt();
                firstFailure = new IllegalStateException(
                        "Interrupted while closing Dahua PTZ executor", exception);
            }
            for (Long subscriptionHandle :
                    Set.copyOf(activeThermalSubscriptions.keySet())) {
                try {
                    closeThermalResource(subscriptionHandle);
                } catch (RuntimeException | LinkageError exception) {
                    firstFailure = combine(firstFailure, asRuntimeFailure(exception));
                }
            }
            for (Long previewHandle : Set.copyOf(activePreviews.keySet())) {
                try {
                    closePreviewResource(previewHandle);
                } catch (RuntimeException | LinkageError exception) {
                    firstFailure = combine(firstFailure, asRuntimeFailure(exception));
                }
            }
            for (Long handle : Set.copyOf(activeSessions.keySet())) {
                if (ownsLogin(handle) || ownsThermalLogin(handle)) {
                    continue;
                }
                AtomicInteger count = activeSessions.get(handle);
                while (count != null && count.get() > 0) {
                    try {
                        logoutHandle(handle);
                    } catch (RuntimeException | LinkageError exception) {
                        firstFailure = combine(firstFailure, asRuntimeFailure(exception));
                        break;
                    }
                    count = activeSessions.get(handle);
                }
            }
            boolean runtimeClosed = false;
            if (activeThermalSubscriptions.isEmpty()
                    && activePreviews.isEmpty() && activeSessions.isEmpty()) {
                try {
                    runtime.close();
                    runtimeClosed = true;
                } catch (RuntimeException | LinkageError exception) {
                    firstFailure = combine(firstFailure, asRuntimeFailure(exception));
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

    private void logoutHandle(long handle) {
        AtomicInteger count = activeSessions.get(handle);
        if (count == null || count.get() <= 0) {
            return;
        }
        if (!nativeApi.logout(handle)) {
            throw failure("Dahua device logout failed");
        }
        if (count.decrementAndGet() == 0) {
            activeSessions.remove(handle, count);
        }
    }

    private static void validateLogin(LoginDomain login) {
        if (login == null) {
            throw new IllegalArgumentException("login must not be null");
        }
        requireNotBlank(login.getIp(), "ip");
        requireNotBlank(login.getUsername(), "username");
        requireNotBlank(login.getPassword(), "password");
        parsePort(login.getPort());
    }

    private static void validatePtz(DeviceDomain device, PTZControlDomain control) {
        if (device == null) {
            throw new IllegalArgumentException("device must not be null");
        }
        if (control == null || control.getCommand() == null) {
            throw new IllegalArgumentException("control.command must not be null");
        }
        if (control.getIsBegin() == null && control.getDuration() == null) {
            throw new IllegalArgumentException("control duration must not be null");
        }
        if (control.getDuration() != null && control.getDuration().isNegative()) {
            throw new IllegalArgumentException("control duration must not be negative");
        }
    }

    private static int validatePreview(
            DeviceDomain device, com.ss.ics.domain.PlayDomain request,
            DahuaStreamCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("device must not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        if (request == null || request.getTakeStreamParam() == null
                || request.getTakeStreamParam().getStreamType() == null) {
            throw new IllegalArgumentException("takeStreamParam.streamType must not be null");
        }
        int streamType = request.getTakeStreamParam().getStreamType();
        if (streamType != 0 && streamType != 1) {
            throw new IllegalArgumentException("streamType must be 0 or 1");
        }
        return streamType;
    }

    private static PtzParameters resolveParameters(PTZControlDomain control) {
        int speed = control.getSpeed(1, 8);
        int command = switch (control.getCommand()) {
            case UP -> 0;
            case DOWN -> 1;
            case LEFT -> 2;
            case RIGHT -> 3;
            case ZOOM_IN -> 4;
            case ZOOM_OUT -> 5;
            case FOCUS_NEAR -> 6;
            case FOCUS_FAR -> 7;
            case IRIS_OPEN -> 8;
            case IRIS_CLOSE -> 9;
            case LEFT_UP -> 0x20;
            case RIGHT_UP -> 0x21;
            case LEFT_DOWN -> 0x22;
            case RIGHT_DOWN -> 0x23;
        };
        boolean diagonal = switch (control.getCommand()) {
            case LEFT_UP, RIGHT_UP, LEFT_DOWN, RIGHT_DOWN -> true;
            default -> false;
        };
        return diagonal
                ? new PtzParameters(command, speed, speed, 0)
                : new PtzParameters(command, 0, speed, 0);
    }

    private static int resolveChannel(String startChannelValue, String logicalChannelValue) {
        int startChannel = Integer.parseInt(startChannelValue);
        if (logicalChannelValue == null || logicalChannelValue.isBlank()) {
            return startChannel;
        }
        int logicalChannel;
        try {
            logicalChannel = Integer.parseInt(logicalChannelValue);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("channel must be a positive integer", exception);
        }
        if (logicalChannel <= 0) {
            throw new IllegalArgumentException("channel must be a positive integer");
        }
        return Math.addExact(startChannel, logicalChannel - 1);
    }

    private static long parseHandle(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "userId must be a valid long handle", exception);
        }
    }

    private static int parsePort(String value) {
        int port;
        try {
            port = Integer.parseInt(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("port must be between 1 and 65535", exception);
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        return port;
    }

    private static void requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dahua PTZ duration was interrupted", exception);
        }
    }

    private DahuaSdkException failure(String message) {
        return new DahuaSdkException(message, nativeApi.lastError());
    }

    private <T> T withOpen(Supplier<T> action) {
        Lock readLock = lifecycleLock.readLock();
        readLock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("Dahua SDK service is closed");
            }
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

    private static RuntimeException combine(
            RuntimeException first, RuntimeException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    private static RuntimeException asRuntimeFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Dahua native resource cleanup failed", failure);
    }

    private static void suppressOrThrow(
            RuntimeException primaryFailure, RuntimeException cleanupFailure) {
        if (primaryFailure != null) {
            primaryFailure.addSuppressed(cleanupFailure);
            return;
        }
        throw cleanupFailure;
    }

    private record PtzParameters(int command, int param1, int param2, int param3) {
    }

    private record PtzTask(
            long handle, int channel, int command, int param1, int param2, int param3,
            Boolean isBegin, Duration duration) {
        private PtzParameters parameters() {
            return new PtzParameters(command, param1, param2, param3);
        }
    }

    private static final class PreviewResource {
        private final Deque<Long> loginHandles = new ArrayDeque<>();
        private boolean previewStopped;

        private PreviewResource(long previewHandle, long loginHandle) {
            loginHandles.addLast(loginHandle);
        }

        private synchronized void addLogin(long loginHandle) {
            loginHandles.addLast(loginHandle);
        }

        private synchronized boolean ownsLogin(long loginHandle) {
            return loginHandles.contains(loginHandle);
        }

        private void logoutAll(java.util.function.LongConsumer logoutAction) {
            while (!loginHandles.isEmpty()) {
                logoutAction.accept(loginHandles.getFirst());
                loginHandles.removeFirst();
            }
        }
    }

    private static final class ThermalResource {
        private final Deque<Long> loginHandles = new ArrayDeque<>();
        private final int channel;
        private boolean detached;

        private ThermalResource(long subscriptionHandle, long loginHandle, int channel) {
            loginHandles.addLast(loginHandle);
            this.channel = channel;
        }

        private synchronized void addLogin(long loginHandle) {
            loginHandles.addLast(loginHandle);
        }

        private synchronized boolean ownsLogin(long loginHandle) {
            return loginHandles.contains(loginHandle);
        }

        private synchronized long primaryLogin() {
            return loginHandles.getFirst();
        }

        private void logoutAll(java.util.function.LongConsumer logoutAction) {
            while (!loginHandles.isEmpty()) {
                logoutAction.accept(loginHandles.getFirst());
                loginHandles.removeFirst();
            }
        }
    }
}
