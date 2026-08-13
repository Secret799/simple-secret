package com.ss.ics.hikvision.internal.jna;

import com.ss.ics.hikvision.HikvisionJnaStructures;
import com.ss.ics.hikvision.HikvisionSdkException;
import com.ss.ics.hikvision.HikvisionSdkOptions;
import com.ss.ics.hikvision.internal.HikvisionNativeApi;
import com.ss.ics.hikvision.internal.HikvisionNativeStreamCallback;
import com.ss.ics.hikvision.internal.HikvisionNativeStreamStartException;
import com.ss.ics.hikvision.internal.model.HikvisionFileSearchCondition;
import com.ss.ics.hikvision.internal.model.HikvisionFileSearchResult;
import com.ss.ics.hikvision.internal.model.HikvisionNativeLoginResult;
import com.ss.ics.hikvision.internal.model.HikvisionPlaybackRequest;
import com.ss.ics.hikvision.internal.model.HikvisionPreviewRequest;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.ss.ics.domain.LoginDomain;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * JNA 实现的海康原生 SDK 适配器。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public final class JnaHikvisionNativeApi implements HikvisionNativeApi {
    private static final int SDK_PATH_BUFFER_SIZE = 256;
    private static final int LOCAL_SDK_PATH_BUFFER_SIZE = 384;
    private static final int PLAYBACK_START_COMMAND = 1;
    private static final int MAX_NATIVE_BUFFER_BYTES = 16 * 1024 * 1024;

    private final HikvisionNativeLibraryPaths paths;
    private final HikvisionNativeLibrary library;
    private final ConcurrentHashMap<StreamCallbackKey, CopyOnWriteArrayList<HikvisionNativeDataCallback>>
            streamCallbacks =
            new ConcurrentHashMap<>();

    private JnaHikvisionNativeApi(
            HikvisionNativeLibraryPaths paths, HikvisionNativeLibrary library) {
        this.paths = paths;
        this.library = library;
    }

    /**
     * 校验并加载海康原生 SDK。
     *
     * @param options SDK 配置
     * @return 已加载但尚未初始化的原生适配器
     */
    public static JnaHikvisionNativeApi load(HikvisionSdkOptions options) {
        return load(options, System.getProperty("os.name"),
                path -> Native.load(path.toString(), HikvisionNativeLibrary.class));
    }

    static JnaHikvisionNativeApi load(
            HikvisionSdkOptions options,
            String osName,
            Function<Path, HikvisionNativeLibrary> loader) {
        if (loader == null) {
            throw new IllegalArgumentException("loader must not be null");
        }
        HikvisionNativeLibraryPaths paths = HikvisionNativeLibraryPaths.resolve(options, osName);
        try {
            return new JnaHikvisionNativeApi(paths, loader.apply(paths.networkLibrary()));
        } catch (LinkageError | RuntimeException exception) {
            throw new HikvisionSdkException(
                    "Hikvision native library load failed", -1, exception);
        }
    }

    @Override
    public boolean initialize() {
        if (paths.linux()) {
            if (!library.NET_DVR_SetSDKInitCfg(3, nativePath(paths.cryptoLibrary()))) {
                return false;
            }
            if (!library.NET_DVR_SetSDKInitCfg(4, nativePath(paths.sslLibrary()))) {
                return false;
            }
            if (!library.NET_DVR_SetSDKInitCfg(
                    2, nativeComponentPath(paths.componentDirectory()))) {
                return false;
            }
        }
        return library.NET_DVR_Init();
    }

    @Override
    public boolean cleanup() {
        return library.NET_DVR_Cleanup();
    }

    @Override
    public int lastError() {
        return library.NET_DVR_GetLastError();
    }

    @Override
    public HikvisionNativeLoginResult login(LoginDomain login) {
        if (login == null) {
            throw new IllegalArgumentException("login must not be null");
        }
        HikvisionJnaStructures.UserLoginInfo loginInfo = new HikvisionJnaStructures.UserLoginInfo();
        copyUtf8("ip", login.getIp(), loginInfo.sDeviceAddress);
        copyUtf8("username", login.getUsername(), loginInfo.sUserName);
        copyUtf8("password", login.getPassword(), loginInfo.sPassword);
        loginInfo.wPort = (short) parsePort(login.getPort());
        loginInfo.bUseAsynLogin = false;
        loginInfo.byLoginMode = 0;
        loginInfo.write();

        HikvisionJnaStructures.DeviceInfoV40 deviceInfo = new HikvisionJnaStructures.DeviceInfoV40();
        deviceInfo.write();
        try {
            int userId = library.NET_DVR_Login_V40(loginInfo, deviceInfo);
            deviceInfo.read();
            HikvisionJnaStructures.DeviceInfoV30 v30 = deviceInfo.deviceInfoV30;
            int digitalStart = Byte.toUnsignedInt(v30.startDigitalChannel);
            int startChannel = digitalStart > 0
                    ? digitalStart : Byte.toUnsignedInt(v30.startAnalogChannel);
            int dvrType = Byte.toUnsignedInt(v30.dvrType);
            int deviceType = Short.toUnsignedInt(v30.deviceType);
            int deviceCategory = dvrType == 0 ? deviceType : dvrType;
            return new HikvisionNativeLoginResult(
                    userId,
                    startChannel,
                    deviceType,
                    deviceCategory,
                    readUtf8(v30.serialNumber));
        } finally {
            Arrays.fill(loginInfo.sDeviceAddress, (byte) 0);
            Arrays.fill(loginInfo.sUserName, (byte) 0);
            Arrays.fill(loginInfo.sPassword, (byte) 0);
            loginInfo.write();
        }
    }

    @Override
    public boolean logout(int userId) {
        return library.NET_DVR_Logout(userId);
    }

    @Override
    public boolean ptzControl(int userId, int channel, int command, int stop, int speed) {
        return library.NET_DVR_PTZControlWithSpeed_Other(
                userId, channel, command, stop, speed);
    }

    @Override
    public long findFiles(int userId, HikvisionFileSearchCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("condition must not be null");
        }
        HikvisionJnaStructures.FileSearchCondition nativeCondition =
                new HikvisionJnaStructures.FileSearchCondition();
        nativeCondition.struStreamID.dwSize = nativeCondition.struStreamID.size();
        nativeCondition.struStreamID.dwChannel = condition.channel();
        nativeCondition.struStartTime = toSearchCondition(condition.startTime());
        nativeCondition.struStopTime = toSearchCondition(condition.stopTime());
        nativeCondition.byQuickSearch = 1;
        nativeCondition.byStreamType = (byte) condition.streamType();
        nativeCondition.dwFileType = 0xff;
        nativeCondition.byIsLocked = (byte) 0xff;
        nativeCondition.dwTimeout = condition.nativeTimeoutMillis();
        nativeCondition.write();
        return library.NET_DVR_FindFile_V50(userId, nativeCondition);
    }

    @Override
    public HikvisionFileSearchResult findNextFile(long findHandle) {
        int nativeHandle = Math.toIntExact(findHandle);
        HikvisionJnaStructures.FileSearchData data = new HikvisionJnaStructures.FileSearchData();
        data.write();
        int status = library.NET_DVR_FindNextFile_V50(nativeHandle, data);
        if (status != HikvisionFileSearchResult.SUCCESS) {
            return new HikvisionFileSearchResult(status, null, null);
        }
        data.read();
        return new HikvisionFileSearchResult(
                status, toLocalDateTime(data.struStartTime), toLocalDateTime(data.struStopTime));
    }

    @Override
    public boolean closeFind(long findHandle) {
        return library.NET_DVR_FindClose_V30(Math.toIntExact(findHandle));
    }

    @Override
    public long startRealPlay(
            int userId, HikvisionPreviewRequest request,
            HikvisionNativeStreamCallback callback) {
        requireStreamArguments(request, callback);
        HikvisionJnaStructures.PreviewInfo previewInfo = new HikvisionJnaStructures.PreviewInfo();
        previewInfo.lChannel = request.channel();
        previewInfo.dwStreamType = request.streamType();
        previewInfo.dwLinkMode = 0;
        previewInfo.bBlocked = 0;
        previewInfo.byProtoType = (byte) request.protocolType();
        previewInfo.write();
        HikvisionNativeDataCallback nativeCallback = nativeCallback(callback);
        int streamHandle = library.NET_DVR_RealPlay_V40(
                userId, previewInfo, nativeCallback, Pointer.NULL);
        if (streamHandle >= 0) {
            retainCallback(StreamType.REAL_PLAY, streamHandle, nativeCallback);
        }
        return streamHandle;
    }

    @Override
    public boolean stopRealPlay(long streamHandle) {
        int nativeHandle = Math.toIntExact(streamHandle);
        boolean stopped = library.NET_DVR_StopRealPlay(nativeHandle);
        if (stopped) {
            streamCallbacks.remove(new StreamCallbackKey(StreamType.REAL_PLAY, nativeHandle));
        }
        return stopped;
    }

    @Override
    public long startPlayback(
            int userId, HikvisionPlaybackRequest request,
            HikvisionNativeStreamCallback callback) {
        requireStreamArguments(request, callback);
        HikvisionJnaStructures.PlaybackParameters parameters = toPlaybackParameters(request);
        int streamHandle = library.NET_DVR_PlayBackByTime_V50(userId, parameters);
        if (streamHandle < 0) {
            return streamHandle;
        }
        HikvisionNativeDataCallback nativeCallback = nativeCallback(callback);
        try {
            if (!library.NET_DVR_SetPlayDataCallBack_V40(
                    streamHandle, nativeCallback, Pointer.NULL)) {
                requirePlaybackCleanup(streamHandle, null);
                return -1L;
            }
            IntByReference outputLength = new IntByReference();
            boolean started = library.NET_DVR_PlayBackControl_V40(
                    streamHandle, PLAYBACK_START_COMMAND,
                    Pointer.NULL, 0, Pointer.NULL, outputLength);
            if (!started) {
                requirePlaybackCleanup(streamHandle, nativeCallback);
                return -1L;
            }
        } catch (LinkageError error) {
            cleanupPlaybackAfterLinkageError(streamHandle, nativeCallback, error);
            throw error;
        }
        retainCallback(StreamType.PLAYBACK, streamHandle, nativeCallback);
        return streamHandle;
    }

    @Override
    public boolean stopPlayback(long streamHandle) {
        int nativeHandle = Math.toIntExact(streamHandle);
        boolean stopped = library.NET_DVR_StopPlayBack(nativeHandle);
        if (stopped) {
            streamCallbacks.remove(new StreamCallbackKey(StreamType.PLAYBACK, nativeHandle));
        }
        return stopped;
    }

    /** 原生回调所属的 HCNetSDK 播放 API 类型。 */
    private enum StreamType {
        /** 实时预览。 */
        REAL_PLAY,
        /** 历史回放。 */
        PLAYBACK
    }

    /**
     * 原生回调强引用键。
     *
     * @param type HCNetSDK 播放 API 类型
     * @param handle 原生播放句柄
     */
    private record StreamCallbackKey(StreamType type, int handle) {
    }

    /**
     * 保留原生回调强引用，同类型重复句柄期间不覆盖已有回调。
     *
     * @param type HCNetSDK 播放 API 类型
     * @param streamHandle 原生播放句柄
     * @param callback 原生回调
     */
    private void retainCallback(
            StreamType type, int streamHandle, HikvisionNativeDataCallback callback) {
        streamCallbacks.computeIfAbsent(
                new StreamCallbackKey(type, streamHandle), ignored -> new CopyOnWriteArrayList<>())
                .add(callback);
    }

    /**
     * 清理半启动回放；停止失败时保留回调并把句柄交还服务层重试。
     *
     * @param streamHandle 原生播放句柄
     * @param callback 已成功注册到 HCNetSDK 的回调，未注册时为 null
     */
    private void requirePlaybackCleanup(
            int streamHandle, HikvisionNativeDataCallback callback) {
        if (library.NET_DVR_StopPlayBack(streamHandle)) {
            return;
        }
        if (callback != null) {
            retainCallback(StreamType.PLAYBACK, streamHandle, callback);
        }
        throw new HikvisionNativeStreamStartException(streamHandle);
    }

    private void cleanupPlaybackAfterLinkageError(
            int streamHandle, HikvisionNativeDataCallback callback, LinkageError primaryFailure) {
        try {
            requirePlaybackCleanup(streamHandle, callback);
        } catch (HikvisionNativeStreamStartException cleanupFailure) {
            cleanupFailure.addSuppressed(primaryFailure);
            throw cleanupFailure;
        }
    }

    private static HikvisionNativeDataCallback nativeCallback(
            HikvisionNativeStreamCallback callback) {
        return (streamHandle, dataType, buffer, bufferSize, userData) -> {
            try {
                if (buffer == null || bufferSize <= 0 || bufferSize > MAX_NATIVE_BUFFER_BYTES) {
                    return;
                }
                callback.onData(streamHandle, dataType, buffer.getByteArray(0, bufferSize));
            } catch (Throwable ignored) {
                // JNA callback boundaries must never propagate Java failures into native code.
            }
        };
    }

    /**
     * 校验原生取流入口的公共参数。
     *
     * @param request 原生取流请求
     * @param callback 码流回调
     */
    private static void requireStreamArguments(Object request, HikvisionNativeStreamCallback callback) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
    }

    /**
     * 将内部回放请求转换为 HCNetSDK V50 结构。
     *
     * @param request 内部回放请求
     * @return 已写入 native 内存的回放参数
     */
    private static HikvisionJnaStructures.PlaybackParameters toPlaybackParameters(
            HikvisionPlaybackRequest request) {
        HikvisionJnaStructures.PlaybackParameters parameters =
                new HikvisionJnaStructures.PlaybackParameters();
        parameters.dwSize = parameters.size();
        parameters.struIDInfo.dwSize = parameters.struIDInfo.size();
        parameters.struIDInfo.dwChannel = request.channel();
        parameters.struBeginTime = toPlaybackTime(request.beginTime());
        parameters.struEndTime = toPlaybackTime(request.endTime());
        parameters.byStreamType = (byte) request.streamType();
        parameters.byOptimalStreamType = 1;
        parameters.write();
        return parameters;
    }

    /**
     * 将设备本地时间转换为 HCNetSDK V50 时间结构。
     *
     * @param value 设备本地日期时间
     * @return 原生回放时间结构
     */
    private static HikvisionJnaStructures.PlaybackTime toPlaybackTime(LocalDateTime value) {
        if (value == null) {
            throw new IllegalArgumentException("playback time must not be null");
        }
        HikvisionJnaStructures.PlaybackTime result = new HikvisionJnaStructures.PlaybackTime();
        result.wYear = (short) value.getYear();
        result.byMonth = (byte) value.getMonthValue();
        result.byDay = (byte) value.getDayOfMonth();
        result.byHour = (byte) value.getHour();
        result.byMinute = (byte) value.getMinute();
        result.bySecond = (byte) value.getSecond();
        result.wMillisecond = (short) (value.getNano() / 1_000_000);
        return result;
    }

    private static Memory nativePath(Path path) {
        byte[] bytes = path.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= SDK_PATH_BUFFER_SIZE) {
            throw new IllegalArgumentException("Hikvision native library path is too long");
        }
        Memory memory = new Memory(SDK_PATH_BUFFER_SIZE);
        memory.clear();
        memory.write(0, bytes, 0, bytes.length);
        return memory;
    }

    private static Memory nativeComponentPath(Path path) {
        byte[] bytes = path.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= SDK_PATH_BUFFER_SIZE) {
            throw new IllegalArgumentException("Hikvision native library path is too long");
        }
        Memory memory = new Memory(LOCAL_SDK_PATH_BUFFER_SIZE);
        memory.clear();
        memory.write(0, bytes, 0, bytes.length);
        return memory;
    }

    private static void copyUtf8(String field, String value, byte[] target) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= target.length) {
            throw new IllegalArgumentException(field + " exceeds Hikvision SDK byte limit");
        }
        System.arraycopy(bytes, 0, target, 0, bytes.length);
    }

    private static String readUtf8(byte[] source) {
        int length = 0;
        while (length < source.length && source[length] != 0) {
            length++;
        }
        return new String(source, 0, length, StandardCharsets.UTF_8);
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

    private static HikvisionJnaStructures.TimeSearchCondition toSearchCondition(
            LocalDateTime value) {
        if (value == null) {
            throw new IllegalArgumentException("search time must not be null");
        }
        HikvisionJnaStructures.TimeSearchCondition result =
                new HikvisionJnaStructures.TimeSearchCondition();
        result.wYear = (short) value.getYear();
        result.byMonth = (byte) value.getMonthValue();
        result.byDay = (byte) value.getDayOfMonth();
        result.byHour = (byte) value.getHour();
        result.byMinute = (byte) value.getMinute();
        result.bySecond = (byte) value.getSecond();
        result.wMillisecond = (short) (value.getNano() / 1_000_000);
        return result;
    }

    private static LocalDateTime toLocalDateTime(HikvisionJnaStructures.TimeSearch value) {
        try {
            return LocalDateTime.of(
                    Short.toUnsignedInt(value.wYear),
                    Byte.toUnsignedInt(value.byMonth),
                    Byte.toUnsignedInt(value.byDay),
                    Byte.toUnsignedInt(value.byHour),
                    Byte.toUnsignedInt(value.byMinute),
                    Byte.toUnsignedInt(value.bySecond));
        } catch (DateTimeException exception) {
            throw new HikvisionSdkException(
                    "Hikvision playback calendar returned invalid time",
                    HikvisionFileSearchResult.FILE_EXCEPTION);
        }
    }
}
