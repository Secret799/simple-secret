package com.ss.ics.hikvision;

import com.ss.ics.domain.LoggedDomain;
import com.ss.ics.domain.LoginDomain;
import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.PTZControlDomain;
import com.ss.ics.domain.PlayDomain;
import com.ss.ics.domain.PlaybackTimePeriodDomain;
import com.ss.ics.constants.enums.PtzControlCommandEnums;
import com.ss.ics.service.DeviceLoginService;
import com.ss.ics.service.PlayQueryService;
import com.ss.ics.service.PtzControlService;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/** 海康 SDK 登录服务和后续厂商能力的统一生命周期入口。 */
public final class HikvisionCameraSdkService
        implements DeviceLoginService, PtzControlService, PlayQueryService, AutoCloseable {
    /** basic plugin 使用的厂商产品编码。 */
    public static final String PRODUCT = "Hikvision";

    private final HikvisionSdkRuntime runtime;
    private final HikvisionNativeApi nativeApi;
    private final HikvisionPlaybackCalendarQuery playbackCalendarQuery;
    private final ThreadPoolExecutor ptzExecutor;
    private final Semaphore asyncPtzSlots;
    private final Set<Integer> activeSessions = ConcurrentHashMap.newKeySet();
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
            }
        });
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
            throw exception;
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

    private void cleanupRejectedAsyncHandle(
            Integer handle, RuntimeException primaryFailure) {
        try {
            if (handle != null) {
                logoutHandle(handle);
            }
        } catch (RuntimeException cleanupFailure) {
            suppressOrThrow(primaryFailure, cleanupFailure);
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
                        throw new IllegalStateException(
                                "Hikvision PTZ executor did not terminate");
                    }
                }
            } catch (InterruptedException exception) {
                ptzExecutor.shutdownNow();
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while closing Hikvision PTZ executor", exception);
            }
            for (Integer handle : Set.copyOf(activeSessions)) {
                try {
                    logoutHandle(handle);
                } catch (RuntimeException exception) {
                    if (firstFailure == null) {
                        firstFailure = exception;
                    } else {
                        firstFailure.addSuppressed(exception);
                    }
                }
            }
            boolean runtimeClosed = false;
            try {
                runtime.close();
                runtimeClosed = true;
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
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
}
