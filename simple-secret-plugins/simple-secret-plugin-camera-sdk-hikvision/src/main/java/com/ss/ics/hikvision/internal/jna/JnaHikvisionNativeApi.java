package com.ss.ics.hikvision.internal.jna;

import com.ss.ics.hikvision.HikvisionJnaStructures;
import com.ss.ics.hikvision.HikvisionSdkException;
import com.ss.ics.hikvision.HikvisionSdkOptions;
import com.ss.ics.hikvision.internal.HikvisionNativeApi;
import com.ss.ics.hikvision.internal.model.HikvisionFileSearchCondition;
import com.ss.ics.hikvision.internal.model.HikvisionFileSearchResult;
import com.ss.ics.hikvision.internal.model.HikvisionNativeLoginResult;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.ss.ics.domain.LoginDomain;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Arrays;
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

    private final HikvisionNativeLibraryPaths paths;
    private final HikvisionNativeLibrary library;

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
                    "Hikvision native library load failed", -1);
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
