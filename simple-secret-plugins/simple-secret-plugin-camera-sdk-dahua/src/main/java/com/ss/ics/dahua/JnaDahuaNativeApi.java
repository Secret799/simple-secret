package com.ss.ics.dahua;

import com.ss.ics.domain.LoginDomain;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** JNA 实现的大华原生 SDK 适配器。 */
final class JnaDahuaNativeApi implements DahuaNativeApi {
    private static final int MAX_NATIVE_BUFFER_BYTES = 16 * 1024 * 1024;
    private static final int MAX_THERMAL_DIMENSION = 8_192;
    private static final int MAX_THERMAL_PIXELS = 16_777_216;

    private final DahuaSdkOptions options;
    private final DahuaNativeLibrary library;
    private final ConcurrentHashMap<Long, DahuaCallbackGroup> previewCallbacks =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, DahuaCallbackGroup>
            radiometryCallbacks = new ConcurrentHashMap<>();
    private final DahuaNativeLibrary.DisconnectCallback disconnectCallback =
            (loginId, ip, port, user) -> { };
    private final DahuaNativeLibrary.ReconnectCallback reconnectCallback =
            (loginId, ip, port, user) -> { };

    private JnaDahuaNativeApi(DahuaSdkOptions options, DahuaNativeLibrary library) {
        this.options = options;
        this.library = library;
    }

    static JnaDahuaNativeApi load(DahuaSdkOptions options) {
        return load(options, System.getProperty("os.name"),
                path -> Native.load(path.toString(), DahuaNativeLibrary.class));
    }

    static JnaDahuaNativeApi load(
            DahuaSdkOptions options,
            String osName,
            Function<Path, DahuaNativeLibrary> loader) {
        if (loader == null) {
            throw new IllegalArgumentException("loader must not be null");
        }
        DahuaNativeLibraryPaths paths = DahuaNativeLibraryPaths.resolve(options, osName);
        try {
            return new JnaDahuaNativeApi(options, loader.apply(paths.netSdkLibrary()));
        } catch (LinkageError | RuntimeException exception) {
            throw new DahuaSdkException("Dahua native library load failed", -1);
        }
    }

    @Override
    public boolean initialize() {
        if (!library.CLIENT_Init(disconnectCallback, Pointer.NULL)) {
            return false;
        }
        library.CLIENT_SetAutoReconnect(reconnectCallback, Pointer.NULL);
        int timeout = Math.toIntExact(options.operationTimeout().toMillis());
        library.CLIENT_SetConnectTime(timeout, 1);
        DahuaJnaStructures.NetworkParam networkParam = new DahuaJnaStructures.NetworkParam();
        networkParam.connectTime = timeout;
        networkParam.getConnectionInfoTime = timeout;
        networkParam.getDeviceInfoTime = timeout;
        networkParam.write();
        library.CLIENT_SetNetworkParam(networkParam);
        return true;
    }

    @Override
    public boolean cleanup() {
        library.CLIENT_Cleanup();
        return true;
    }

    @Override
    public int lastError() {
        return library.CLIENT_GetLastError();
    }

    @Override
    public DahuaNativeLoginResult login(LoginDomain login) {
        if (login == null) {
            throw new IllegalArgumentException("login must not be null");
        }
        DahuaJnaStructures.HighSecurityLoginInput input =
                new DahuaJnaStructures.HighSecurityLoginInput();
        copyUtf8("ip", login.getIp(), input.szIP);
        copyUtf8("username", login.getUsername(), input.szUserName);
        copyUtf8("password", login.getPassword(), input.szPassword);
        input.port = parsePort(login.getPort());
        input.write();
        DahuaJnaStructures.HighSecurityLoginOutput output =
                new DahuaJnaStructures.HighSecurityLoginOutput();
        output.write();
        try {
            DahuaJnaStructures.DahuaLong handle =
                    library.CLIENT_LoginWithHighLevelSecurity(input, output);
            output.read();
            return new DahuaNativeLoginResult(
                    handle.longValue(),
                    0,
                    output.deviceInfo.deviceType,
                    output.deviceInfo.deviceType,
                    readUtf8(output.deviceInfo.serialNumber));
        } finally {
            Arrays.fill(input.szIP, (byte) 0);
            Arrays.fill(input.szUserName, (byte) 0);
            Arrays.fill(input.szPassword, (byte) 0);
            input.write();
        }
    }

    @Override
    public boolean logout(long userId) {
        return library.CLIENT_Logout(new DahuaJnaStructures.DahuaLong(userId));
    }

    @Override
    public boolean ptzControl(
            long userId, int channel, int command,
            int param1, int param2, int param3, int stop) {
        return library.CLIENT_DHPTZControlEx(
                new DahuaJnaStructures.DahuaLong(userId), channel, command,
                param1, param2, param3, stop);
    }

    @Override
    public long startPreview(
            long userId, int channel, int streamType, DahuaNativeStreamCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        DahuaCallbackGate gate = new DahuaCallbackGate();
        DahuaNativeLibrary.DataCallback nativeCallback = (handle, info, user) -> {
            try {
                if (!gate.enter()) {
                    return 1;
                }
                try {
                    if (info == null) {
                        return 1;
                    }
                    info.read();
                    if (!isValidNativeBuffer(info.buffer, info.bufferSize)) {
                        return 1;
                    }
                    byte[] header = info.buffer.getByteArray(0, Math.min(info.bufferSize, 4));
                    if (!isAnnexB(header)) {
                        return 1;
                    }
                    byte[] data = info.buffer.getByteArray(0, info.bufferSize);
                    callback.onFrame(new DahuaNativeStreamFrame(
                            data,
                            Integer.toUnsignedLong(info.time.pts),
                            Integer.toUnsignedLong(info.time.dts),
                            info.frameType,
                            info.frameSubType));
                } finally {
                    gate.exit();
                }
            } catch (Throwable ignored) {
                // Native callback boundaries must never propagate Java exceptions.
            }
            return 1;
        };
        gate.retain(nativeCallback);
        DahuaJnaStructures.RealPlayInput input = new DahuaJnaStructures.RealPlayInput();
        input.channel = channel;
        input.realPlayType = streamType == 0 ? 0 : 3;
        input.dataType = 4;
        input.audioType = 1;
        input.dataCallback = nativeCallback;
        input.write();
        DahuaJnaStructures.RealPlayOutput output = new DahuaJnaStructures.RealPlayOutput();
        output.write();
        long handle = library.CLIENT_RealPlayByDataType(
                new DahuaJnaStructures.DahuaLong(userId), input, output,
                Math.toIntExact(options.operationTimeout().toMillis())).longValue();
        if (handle != 0) {
            previewCallbacks.compute(handle, (ignored, group) -> {
                DahuaCallbackGroup registration =
                        group == null ? new DahuaCallbackGroup() : group;
                registration.add(gate);
                return registration;
            });
        } else {
            gate.release();
        }
        return handle;
    }

    @Override
    public boolean stopPreview(long previewHandle) {
        DahuaCallbackGroup group = previewCallbacks.get(previewHandle);
        if (group != null && !group.disableAndAwait(options.operationTimeout())) {
            return false;
        }
        boolean stopped = library.CLIENT_StopRealPlayEx(
                new DahuaJnaStructures.DahuaLong(previewHandle));
        if (stopped && group != null && previewCallbacks.remove(previewHandle, group)) {
            group.release();
        }
        return stopped;
    }

    private static boolean isAnnexB(byte[] header) {
        return header.length >= 3
                && header[0] == 0 && header[1] == 0
                && (header[2] == 1 || header.length >= 4 && header[2] == 0 && header[3] == 1);
    }

    private static boolean isValidNativeBuffer(Pointer buffer, int bufferSize) {
        return buffer != null
                && Pointer.nativeValue(buffer) != 0
                && bufferSize > 0
                && bufferSize <= MAX_NATIVE_BUFFER_BYTES;
    }

    @Override
    public long attachRadiometry(
            long userId, int channel, DahuaNativeThermalCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        DahuaCallbackGate gate = new DahuaCallbackGate();
        DahuaNativeLibrary.RadiometryCallback nativeCallback =
                (subscriptionHandle, data, bufferLength, user) -> {
                    try {
                        if (!gate.enter()) {
                            return;
                        }
                        try {
                            if (data == null) {
                                return;
                            }
                            data.read();
                            int width = data.metadata.width;
                            int height = data.metadata.height;
                            if (!isValidThermalData(data, bufferLength, width, height)) {
                                return;
                            }
                            int pixels = width * height;
                            short[] grayscale = new short[pixels];
                            float[] temperatures = new float[pixels];
                            if (!library.CLIENT_RadiometryDataParse(
                                    data, grayscale, temperatures)) {
                                return;
                            }
                            callback.onData(new DahuaNativeThermalData(
                                    toLocalDateTime(data.metadata.time), width, height,
                                    grayscale, temperatures));
                        } finally {
                            gate.exit();
                        }
                    } catch (Throwable ignored) {
                        // Native callback boundaries must never propagate Java exceptions.
                    }
                };
        gate.retain(nativeCallback);
        DahuaJnaStructures.RadiometryAttachInput input =
                new DahuaJnaStructures.RadiometryAttachInput();
        input.channel = channel;
        input.callback = nativeCallback;
        input.write();
        DahuaJnaStructures.RadiometryAttachOutput output =
                new DahuaJnaStructures.RadiometryAttachOutput();
        output.write();
        long handle = library.CLIENT_RadiometryAttach(
                new DahuaJnaStructures.DahuaLong(userId), input, output,
                timeoutMillis()).longValue();
        if (handle != 0) {
            radiometryCallbacks.compute(handle, (ignored, group) -> {
                DahuaCallbackGroup registration =
                        group == null ? new DahuaCallbackGroup() : group;
                registration.add(gate);
                return registration;
            });
        } else {
            gate.release();
        }
        return handle;
    }

    @Override
    public boolean detachRadiometry(long subscriptionHandle) {
        DahuaCallbackGroup group = radiometryCallbacks.get(subscriptionHandle);
        if (group != null && !group.disableAndAwait(options.operationTimeout())) {
            return false;
        }
        boolean detached = library.CLIENT_RadiometryDetach(
                new DahuaJnaStructures.DahuaLong(subscriptionHandle));
        if (detached && group != null && radiometryCallbacks.remove(subscriptionHandle, group)) {
            group.release();
        }
        return detached;
    }

    private static boolean isValidThermalData(
            DahuaJnaStructures.ThermalData data, int bufferLength, int width, int height) {
        return width > 0
                && width <= MAX_THERMAL_DIMENSION
                && height > 0
                && height <= MAX_THERMAL_DIMENSION
                && (long) width * height <= MAX_THERMAL_PIXELS
                && isValidNativeBuffer(data.dataBuffer, data.bufferSize)
                && bufferLength > 0
                && bufferLength <= MAX_NATIVE_BUFFER_BYTES;
    }

    @Override
    public int fetchRadiometry(long userId, int channel) {
        DahuaJnaStructures.RadiometryFetchInput input =
                new DahuaJnaStructures.RadiometryFetchInput();
        input.channel = channel;
        input.write();
        DahuaJnaStructures.RadiometryFetchOutput output =
                new DahuaJnaStructures.RadiometryFetchOutput();
        output.write();
        if (!library.CLIENT_RadiometryFetch(
                new DahuaJnaStructures.DahuaLong(userId), input, output, timeoutMillis())) {
            throw new DahuaSdkException("Dahua radiometry fetch failed", lastError());
        }
        output.read();
        return output.status;
    }

    @Override
    public DahuaNativeTemperatureSummary queryPointTemperature(
            long userId, int channel, int x, int y) {
        DahuaJnaStructures.PointTemperatureInput input =
                new DahuaJnaStructures.PointTemperatureInput();
        input.channel = channel;
        input.coordinate.x = (short) x;
        input.coordinate.y = (short) y;
        input.write();
        DahuaJnaStructures.PointTemperatureOutput output =
                new DahuaJnaStructures.PointTemperatureOutput();
        output.write();
        if (!library.CLIENT_QueryDevInfo(
                new DahuaJnaStructures.DahuaLong(userId), 0x0c,
                input.getPointer(), output.getPointer(), Pointer.NULL, timeoutMillis())) {
            return null;
        }
        output.read();
        return map(output.temperature);
    }

    @Override
    public DahuaNativeTemperatureSummary queryItemTemperature(
            long userId, int channel, int presetId, int ruleId, int meterType) {
        DahuaJnaStructures.ItemTemperatureInput input =
                new DahuaJnaStructures.ItemTemperatureInput();
        input.condition.channel = channel;
        input.condition.presetId = presetId;
        input.condition.ruleId = ruleId;
        input.condition.meterType = meterType;
        input.write();
        DahuaJnaStructures.ItemTemperatureOutput output =
                new DahuaJnaStructures.ItemTemperatureOutput();
        output.write();
        if (!library.CLIENT_QueryDevInfo(
                new DahuaJnaStructures.DahuaLong(userId), 0x0d,
                input.getPointer(), output.getPointer(), Pointer.NULL, timeoutMillis())) {
            return null;
        }
        output.read();
        return map(output.temperature);
    }

    @Override
    public DahuaNativeRegionTemperature queryRegionTemperature(
            long userId, int channel, List<DahuaPoint> points) {
        DahuaJnaStructures.RegionTemperatureInput input =
                new DahuaJnaStructures.RegionTemperatureInput();
        input.channel = channel;
        input.pointCount = points.size();
        for (int index = 0; index < points.size(); index++) {
            input.polygon[index].x = (short) points.get(index).x();
            input.polygon[index].y = (short) points.get(index).y();
        }
        input.write();
        DahuaJnaStructures.RegionTemperatureOutput output =
                new DahuaJnaStructures.RegionTemperatureOutput();
        output.write();
        if (!library.CLIENT_RadiometryGetRandomRegionTemper(
                new DahuaJnaStructures.DahuaLong(userId),
                input.getPointer(), output.getPointer(), timeoutMillis())) {
            return null;
        }
        output.read();
        DahuaJnaStructures.RegionTemperatureInfo info = output.temperature;
        return new DahuaNativeRegionTemperature(
                info.temperatureUnit,
                info.average / 100.0,
                info.maximum / 100.0,
                info.minimum / 100.0,
                map(info.maximumPoint),
                map(info.minimumPoint));
    }

    @Override
    public DahuaNativeSearchStart startRadiometrySearch(
            long userId, int channel, int meterType, int period,
            LocalDateTime begin, LocalDateTime end) {
        DahuaJnaStructures.RadiometrySearchInput input =
                new DahuaJnaStructures.RadiometrySearchInput();
        input.startTime = toNetTime(begin);
        input.endTime = toNetTime(end);
        input.meterType = meterType;
        input.channel = channel;
        input.period = period;
        input.write();
        DahuaJnaStructures.RadiometrySearchOutput output =
                new DahuaJnaStructures.RadiometrySearchOutput();
        output.write();
        if (!library.CLIENT_StartFind(
                new DahuaJnaStructures.DahuaLong(userId), 0,
                input.getPointer(), output.getPointer(), timeoutMillis())) {
            return null;
        }
        output.read();
        return new DahuaNativeSearchStart(output.finderHandle, output.totalCount);
    }

    @Override
    public List<DahuaNativeRadiometryRecord> findRadiometryPage(
            long userId, int finderHandle, int offset, int count) {
        if (count < 1 || count > 32) {
            throw new IllegalArgumentException("count must be between 1 and 32");
        }
        DahuaJnaStructures.RadiometryPageInput input =
                new DahuaJnaStructures.RadiometryPageInput();
        input.finderHandle = finderHandle;
        input.offset = offset;
        input.count = count;
        input.write();
        DahuaJnaStructures.RadiometryPageOutput output =
                new DahuaJnaStructures.RadiometryPageOutput();
        output.write();
        if (!library.CLIENT_DoFind(
                new DahuaJnaStructures.DahuaLong(userId), 0,
                input.getPointer(), output.getPointer(), timeoutMillis())) {
            return null;
        }
        output.read();
        int found = Math.min(Math.max(output.found, 0), 32);
        List<DahuaNativeRadiometryRecord> results = new ArrayList<>(found);
        for (int index = 0; index < found; index++) {
            DahuaJnaStructures.RadiometryRecord record = output.records[index];
            int coordinateCount = Math.min(Math.max(record.coordinateCount, 0), 8);
            List<DahuaPoint> coordinates = new ArrayList<>(coordinateCount);
            for (int coordinateIndex = 0; coordinateIndex < coordinateCount; coordinateIndex++) {
                coordinates.add(map(record.coordinates[coordinateIndex]));
            }
            results.add(new DahuaNativeRadiometryRecord(
                    toLocalDateTime(record.time), record.presetId, record.ruleId,
                    readUtf8(record.name), record.channel, map(record.temperature), coordinates));
        }
        return List.copyOf(results);
    }

    @Override
    public boolean stopRadiometrySearch(long userId, int finderHandle) {
        DahuaJnaStructures.RadiometryStopInput input =
                new DahuaJnaStructures.RadiometryStopInput();
        input.finderHandle = finderHandle;
        input.write();
        DahuaJnaStructures.RadiometryStopOutput output =
                new DahuaJnaStructures.RadiometryStopOutput();
        output.write();
        return library.CLIENT_StopFind(
                new DahuaJnaStructures.DahuaLong(userId), 0,
                input.getPointer(), output.getPointer(), timeoutMillis());
    }

    private int timeoutMillis() {
        return Math.toIntExact(options.operationTimeout().toMillis());
    }

    private static DahuaNativeTemperatureSummary map(
            DahuaJnaStructures.TemperatureInfo info) {
        return new DahuaNativeTemperatureSummary(
                info.meterType, info.temperatureUnit, info.average, info.maximum,
                info.minimum, info.middle, info.standardDeviation);
    }

    private static DahuaPoint map(DahuaJnaStructures.Point point) {
        return new DahuaPoint(Short.toUnsignedInt(point.x), Short.toUnsignedInt(point.y));
    }

    private static DahuaJnaStructures.NetTime toNetTime(LocalDateTime value) {
        DahuaJnaStructures.NetTime result = new DahuaJnaStructures.NetTime();
        result.year = value.getYear();
        result.month = value.getMonthValue();
        result.day = value.getDayOfMonth();
        result.hour = value.getHour();
        result.minute = value.getMinute();
        result.second = value.getSecond();
        return result;
    }

    private static LocalDateTime toLocalDateTime(DahuaJnaStructures.NetTime value) {
        try {
            return LocalDateTime.of(
                    value.year, value.month, value.day,
                    value.hour, value.minute, value.second);
        } catch (DateTimeException exception) {
            throw new DahuaSdkException("Dahua SDK returned invalid time", -1);
        }
    }

    private static void copyUtf8(String field, String value, byte[] target) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        try {
            if (bytes.length >= target.length) {
                throw new IllegalArgumentException(field + " exceeds Dahua SDK byte limit");
            }
            System.arraycopy(bytes, 0, target, 0, bytes.length);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    static String readUtf8(byte[] source) {
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
}
