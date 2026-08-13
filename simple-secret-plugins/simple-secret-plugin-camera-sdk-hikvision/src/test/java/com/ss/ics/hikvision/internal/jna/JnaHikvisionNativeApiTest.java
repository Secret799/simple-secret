package com.ss.ics.hikvision.internal.jna;

import com.ss.ics.domain.LoginDomain;
import com.ss.ics.hikvision.HikvisionJnaStructures;
import com.ss.ics.hikvision.HikvisionSdkException;
import com.ss.ics.hikvision.HikvisionSdkOptions;
import com.ss.ics.hikvision.internal.HikvisionNativeApi;
import com.ss.ics.hikvision.internal.HikvisionNativeStreamStartException;
import com.ss.ics.hikvision.internal.model.HikvisionFileSearchCondition;
import com.ss.ics.hikvision.internal.model.HikvisionFileSearchResult;
import com.ss.ics.hikvision.internal.model.HikvisionNativeLoginResult;
import com.ss.ics.hikvision.internal.model.HikvisionPlaybackRequest;
import com.ss.ics.hikvision.internal.model.HikvisionPreviewRequest;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JnaHikvisionNativeApiTest {

    @TempDir
    Path tempDirectory;

    @Test
    void configuresLinuxLibrariesBeforeInitialization() throws IOException {
        Path networkLibrary = Files.createFile(tempDirectory.resolve("libhcnetsdk.so"));
        Files.createFile(tempDirectory.resolve("libcrypto.so.1.1"));
        Files.createFile(tempDirectory.resolve("libssl.so.1.1"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        List<Path> loadedPaths = new ArrayList<>();

        HikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory),
                "Linux",
                path -> {
                    loadedPaths.add(path);
                    return library;
                });
        assertThat(nativeApi.initialize()).isTrue();
        assertThat(nativeApi.cleanup()).isTrue();

        assertThat(loadedPaths).containsExactly(networkLibrary);
        assertThat(library.initConfigTypes).containsExactly(3, 4, 2);
        assertThat(library.initConfigBufferSizes).containsExactly(256L, 256L, 384L);
        assertThat(library.initializeCalls).isEqualTo(1);
        assertThat(library.cleanupCalls).isEqualTo(1);
    }

    @Test
    void mapsSynchronousLoginStructuresAndPreservesCredentials() throws IOException {
        Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        JnaHikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows 11", path -> library);
        LoginDomain login = new LoginDomain()
                .setIp("192.0.2.10")
                .setPort("8000")
                .setUsername(" operator ")
                .setPassword(" secret ");

        HikvisionNativeLoginResult result = nativeApi.login(login);

        assertThat(library.capturedDeviceAddress).isEqualTo("192.0.2.10");
        assertThat(library.capturedUsername).isEqualTo(" operator ");
        assertThat(library.capturedPassword).isEqualTo(" secret ");
        assertThat(Short.toUnsignedInt(library.lastLogin.wPort)).isEqualTo(8000);
        assertThat(library.lastLogin.bUseAsynLogin).isFalse();
        assertThat(library.lastLogin.sDeviceAddress).containsOnly((byte) 0);
        assertThat(library.lastLogin.sUserName).containsOnly((byte) 0);
        assertThat(library.lastLogin.sPassword).containsOnly((byte) 0);
        assertThat(result).isEqualTo(new HikvisionNativeLoginResult(42, 33, 71, 8, "serial-01"));
    }

    @Test
    void rejectsOversizedCredentialsBeforeCallingNativeLibrary() throws IOException {
        Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        JnaHikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows", path -> library);
        String secret = "x".repeat(64);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> nativeApi.login(new LoginDomain()
                        .setIp("192.0.2.10")
                        .setPort("8000")
                        .setUsername("operator")
                        .setPassword(secret)))
                .withMessage("password exceeds Hikvision SDK byte limit")
                .withMessageNotContaining(secret);
        assertThat(library.lastLogin).isNull();
    }

    @Test
    void delegatesPtzControlToNonPreviewNativeCall() throws IOException {
        Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        JnaHikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows", path -> library);

        boolean accepted = nativeApi.ptzControl(42, 33, 23, 0, 7);

        assertThat(accepted).isTrue();
        assertThat(library.lastPtz).isEqualTo("42:33:23:0:7");
    }

    @Test
    void mapsFileSearchConditionsResultsAndHandleLifecycle() throws IOException {
        Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        JnaHikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows", path -> library);
        HikvisionFileSearchCondition condition = new HikvisionFileSearchCondition(
                33,
                1,
                LocalDateTime.of(2026, 2, 1, 0, 0),
                LocalDateTime.of(2026, 2, 28, 23, 59, 59),
                5_000);

        long handle = nativeApi.findFiles(42, condition);
        HikvisionFileSearchResult result = nativeApi.findNextFile(handle);
        boolean closed = nativeApi.closeFind(handle);

        assertThat(handle).isEqualTo(81);
        assertThat(library.lastFileCondition.struStreamID.dwSize)
                .isEqualTo(library.lastFileCondition.struStreamID.size());
        assertThat(library.lastFileCondition.struStreamID.dwChannel).isEqualTo(33);
        assertThat(library.lastFileCondition.byQuickSearch).isEqualTo((byte) 1);
        assertThat(library.lastFileCondition.byStreamType).isEqualTo((byte) 1);
        assertThat(library.lastFileCondition.dwFileType).isEqualTo(0xff);
        assertThat(library.lastFileCondition.byIsLocked).isEqualTo((byte) 0xff);
        assertThat(library.lastFileCondition.dwTimeout).isEqualTo(5_000);
        assertThat(library.lastFileCondition.size()).isEqualTo(416);
        assertThat(toDateTime(library.lastFileCondition.struStartTime))
                .isEqualTo(condition.startTime());
        assertThat(toDateTime(library.lastFileCondition.struStopTime))
                .isEqualTo(condition.stopTime());
        assertThat(result).isEqualTo(new HikvisionFileSearchResult(
                HikvisionFileSearchResult.SUCCESS,
                LocalDateTime.of(2026, 2, 3, 23, 30),
                LocalDateTime.of(2026, 2, 4, 0, 15)));
        assertThat(new HikvisionJnaStructures.FileSearchData().size()).isEqualTo(444);
        assertThat(closed).isTrue();
        assertThat(library.closedFindHandle).isEqualTo(81);
    }

    @Test
    void mapsRealPlayParametersAndCopiesCallbackData() throws IOException {
        Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        JnaHikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows", path -> library);
        List<byte[]> dataBlocks = new ArrayList<>();

        long handle = nativeApi.startRealPlay(
                42, new HikvisionPreviewRequest(34, 1, 1),
                (streamHandle, dataType, data) -> dataBlocks.add(data));
        Memory memory = new Memory(3);
        memory.write(0, new byte[]{1, 2, 3}, 0, 3);
        library.realDataCallback.invoke(101, 2, memory, 3, Pointer.NULL);
        memory.setByte(0, (byte) 9);

        assertThat(handle).isEqualTo(101L);
        assertThat(library.lastPreviewInfo.lChannel).isEqualTo(34);
        assertThat(library.lastPreviewInfo.dwStreamType).isEqualTo(1);
        assertThat(library.lastPreviewInfo.dwLinkMode).isZero();
        assertThat(library.lastPreviewInfo.bBlocked).isZero();
        assertThat(library.lastPreviewInfo.byProtoType).isEqualTo((byte) 1);
        assertThat(dataBlocks).singleElement()
                .satisfies(data -> assertThat(data).containsExactly(1, 2, 3));
        assertThat(nativeApi.stopRealPlay(handle)).isTrue();
        assertThat(library.stoppedRealPlayHandle).isEqualTo(101);
    }

    @Test
    void isolatesUncheckedFailuresAtNativeCallbackBoundary() throws IOException {
        Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        JnaHikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows", path -> library);
        nativeApi.startRealPlay(42, new HikvisionPreviewRequest(33, 0, 0),
                (streamHandle, dataType, data) -> {
                    throw new IllegalStateException("consumer failure");
                });
        Memory memory = new Memory(1);
        memory.setByte(0, (byte) 1);

        assertThatCode(() -> library.realDataCallback.invoke(
                101, 1, memory, 1, Pointer.NULL)).doesNotThrowAnyException();
    }

    @Test
    void rejectsOversizedNativeCallbackBufferWithoutReadingIt() throws IOException {
        Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        JnaHikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows", path -> library);
        List<byte[]> dataBlocks = new ArrayList<>();
        nativeApi.startRealPlay(42, new HikvisionPreviewRequest(33, 0, 0),
                (streamHandle, dataType, data) -> dataBlocks.add(data));
        Memory memory = new Memory(1);

        assertThatCode(() -> library.realDataCallback.invoke(
                101, 1, memory, 16 * 1024 * 1024 + 1, Pointer.NULL))
                .doesNotThrowAnyException();
        assertThat(dataBlocks).isEmpty();
    }

    @Test
    void isolatesErrorsAtNativeCallbackBoundary() throws IOException {
        Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        JnaHikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows", path -> library);
        nativeApi.startRealPlay(42, new HikvisionPreviewRequest(33, 0, 0),
                (streamHandle, dataType, data) -> {
                    throw new AssertionError("consumer failure");
                });
        Memory memory = new Memory(1);

        assertThatCode(() -> library.realDataCallback.invoke(
                101, 1, memory, 1, Pointer.NULL)).doesNotThrowAnyException();
    }

    @Test
    void startsPlaybackOnlyAfterCallbackAndPlayControlSucceed() throws IOException {
        Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        JnaHikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows", path -> library);
        LocalDateTime begin = LocalDateTime.of(2026, 8, 12, 10, 0);
        LocalDateTime end = begin.plusMinutes(30);

        long handle = nativeApi.startPlayback(
                42, new HikvisionPlaybackRequest(33, 0, begin, end),
                (streamHandle, dataType, data) -> { });

        assertThat(handle).isEqualTo(202L);
        assertThat(library.lastPlaybackParameters.dwSize)
                .isEqualTo(library.lastPlaybackParameters.size());
        assertThat(library.lastPlaybackParameters.struIDInfo.dwSize)
                .isEqualTo(library.lastPlaybackParameters.struIDInfo.size());
        assertThat(library.lastPlaybackParameters.struIDInfo.dwChannel).isEqualTo(33);
        assertThat(library.lastPlaybackParameters.byStreamType).isZero();
        assertThat(toDateTime(library.lastPlaybackParameters.struBeginTime)).isEqualTo(begin);
        assertThat(toDateTime(library.lastPlaybackParameters.struEndTime)).isEqualTo(end);
        assertThat(library.playbackEvents)
                .containsExactly("create:42", "callback:202", "control:202:1");
        assertThat(nativeApi.stopPlayback(handle)).isTrue();
        assertThat(library.playbackEvents).endsWith("stop:202");
    }

    @Test
    void stopsPlaybackWhenCallbackRegistrationFails() throws IOException {
        Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        library.playbackCallbackResult = false;
        JnaHikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows", path -> library);
        LocalDateTime begin = LocalDateTime.of(2026, 8, 12, 10, 0);

        long handle = nativeApi.startPlayback(
                42, new HikvisionPlaybackRequest(33, 0, begin, begin.plusMinutes(1)),
                (streamHandle, dataType, data) -> { });

        assertThat(handle).isEqualTo(-1L);
        assertThat(library.playbackEvents)
                .containsExactly("create:42", "callback:202", "stop:202");
    }

    @Test
    void stopsPlaybackWhenPlayControlFails() throws IOException {
        Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        library.playbackControlResult = false;
        JnaHikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows", path -> library);
        LocalDateTime begin = LocalDateTime.of(2026, 8, 12, 10, 0);

        long handle = nativeApi.startPlayback(
                42, new HikvisionPlaybackRequest(33, 0, begin, begin.plusMinutes(1)),
                (streamHandle, dataType, data) -> { });

        assertThat(handle).isEqualTo(-1L);
        assertThat(library.playbackEvents).containsExactly(
                "create:42", "callback:202", "control:202:1", "stop:202");
    }

    @Test
    void preservesPlaybackHandleWhenCallbackLinkageFailureCannotStop() throws Exception {
        Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        library.playbackCallbackLinkageFailure = true;
        library.stopPlaybackResult = false;
        JnaHikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows", path -> library);
        LocalDateTime begin = LocalDateTime.of(2026, 8, 12, 10, 0);

        assertThatThrownBy(() -> nativeApi.startPlayback(
                42, new HikvisionPlaybackRequest(33, 0, begin, begin.plusMinutes(1)),
                (streamHandle, dataType, data) -> { }))
                .isInstanceOfSatisfying(HikvisionNativeStreamStartException.class,
                        exception -> assertThat(exception.streamHandle()).isEqualTo(202L));

        assertThat(streamCallbacks(nativeApi)).hasSize(1);
        assertThat(library.playbackEvents).containsExactly(
                "create:42", "callback:202", "stop:202");
    }

    @Test
    void retainsCallbackAndHandleWhenFailedPlaybackCannotStop() throws Exception {
        Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        library.playbackControlResult = false;
        library.stopPlaybackResult = false;
        JnaHikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows", path -> library);
        LocalDateTime begin = LocalDateTime.of(2026, 8, 12, 10, 0);

        assertThatThrownBy(() -> nativeApi.startPlayback(
                42, new HikvisionPlaybackRequest(33, 0, begin, begin.plusMinutes(1)),
                (streamHandle, dataType, data) -> { }))
                .isInstanceOfSatisfying(HikvisionNativeStreamStartException.class,
                        exception -> assertThat(exception.streamHandle()).isEqualTo(202L));

        assertThat(streamCallbacks(nativeApi)).hasSize(1);
        assertThat(library.playbackEvents).containsExactly(
                "create:42", "callback:202", "control:202:1", "stop:202");
    }

    @Test
    void keepsCallbacksIndependentWhenStreamTypesShareNumericHandle() throws Exception {
        Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));
        FakeNativeLibrary library = new FakeNativeLibrary();
        library.playbackHandle = library.realPlayHandle;
        JnaHikvisionNativeApi nativeApi = JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory), "Windows", path -> library);
        LocalDateTime begin = LocalDateTime.of(2026, 8, 12, 10, 0);

        nativeApi.startRealPlay(42, new HikvisionPreviewRequest(33, 0, 0),
                (streamHandle, dataType, data) -> { });
        nativeApi.startPlayback(43,
                new HikvisionPlaybackRequest(33, 0, begin, begin.plusMinutes(1)),
                (streamHandle, dataType, data) -> { });

        assertThat(streamCallbacks(nativeApi)).hasSize(2);
        nativeApi.stopRealPlay(library.realPlayHandle);
        assertThat(streamCallbacks(nativeApi)).hasSize(1);
        nativeApi.stopPlayback(library.playbackHandle);
        assertThat(streamCallbacks(nativeApi)).isEmpty();
    }

    @Test
    void doesNotExposeNativeLibraryPathWhenLoadingFails() throws IOException {
        Path networkLibrary = Files.createFile(tempDirectory.resolve("HCNetSDK.dll"));

        assertThatThrownBy(() -> JnaHikvisionNativeApi.load(
                HikvisionSdkOptions.defaults(tempDirectory),
                "Windows",
                path -> {
                    throw new UnsatisfiedLinkError("cannot load " + path);
                }))
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision native library load failed (code=-1)")
                .hasMessageNotContaining(networkLibrary.toString())
                .hasCauseInstanceOf(UnsatisfiedLinkError.class);
    }

    private static String readCString(byte[] value) {
        int length = 0;
        while (length < value.length && value[length] != 0) {
            length++;
        }
        return new String(value, 0, length, StandardCharsets.UTF_8);
    }

    private static LocalDateTime toDateTime(HikvisionJnaStructures.TimeSearchCondition value) {
        return LocalDateTime.of(
                Short.toUnsignedInt(value.wYear),
                Byte.toUnsignedInt(value.byMonth),
                Byte.toUnsignedInt(value.byDay),
                Byte.toUnsignedInt(value.byHour),
                Byte.toUnsignedInt(value.byMinute),
                Byte.toUnsignedInt(value.bySecond));
    }

    private static LocalDateTime toDateTime(HikvisionJnaStructures.PlaybackTime value) {
        return LocalDateTime.of(
                Short.toUnsignedInt(value.wYear), Byte.toUnsignedInt(value.byMonth),
                Byte.toUnsignedInt(value.byDay), Byte.toUnsignedInt(value.byHour),
                Byte.toUnsignedInt(value.byMinute), Byte.toUnsignedInt(value.bySecond),
                Short.toUnsignedInt(value.wMillisecond) * 1_000_000);
    }

    private static Map<?, ?> streamCallbacks(JnaHikvisionNativeApi nativeApi) throws Exception {
        java.lang.reflect.Field field = JnaHikvisionNativeApi.class
                .getDeclaredField("streamCallbacks");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(nativeApi);
    }

    private static final class FakeNativeLibrary implements HikvisionNativeLibrary {
        private final List<Integer> initConfigTypes = new ArrayList<>();
        private final List<Long> initConfigBufferSizes = new ArrayList<>();
        private int initializeCalls;
        private int cleanupCalls;
        private HikvisionJnaStructures.UserLoginInfo lastLogin;
        private String capturedDeviceAddress;
        private String capturedUsername;
        private String capturedPassword;
        private String lastPtz;
        private HikvisionJnaStructures.FileSearchCondition lastFileCondition;
        private int closedFindHandle;
        private HikvisionJnaStructures.PreviewInfo lastPreviewInfo;
        private HikvisionNativeDataCallback realDataCallback;
        private int stoppedRealPlayHandle;
        private HikvisionJnaStructures.PlaybackParameters lastPlaybackParameters;
        private final List<String> playbackEvents = new ArrayList<>();
        private boolean playbackCallbackResult = true;
        private boolean playbackControlResult = true;
        private boolean playbackCallbackLinkageFailure;
        private boolean stopPlaybackResult = true;
        private int realPlayHandle = 101;
        private int playbackHandle = 202;

        @Override
        public boolean NET_DVR_SetSDKInitCfg(int type, Pointer buffer) {
            initConfigTypes.add(type);
            initConfigBufferSizes.add(((Memory) buffer).size());
            return buffer != null;
        }

        @Override
        public boolean NET_DVR_Init() {
            initializeCalls++;
            return true;
        }

        @Override
        public boolean NET_DVR_Cleanup() {
            cleanupCalls++;
            return true;
        }

        @Override
        public int NET_DVR_GetLastError() {
            return 0;
        }

        @Override
        public int NET_DVR_Login_V40(
                HikvisionJnaStructures.UserLoginInfo login,
                HikvisionJnaStructures.DeviceInfoV40 deviceInfo) {
            lastLogin = login;
            capturedDeviceAddress = readCString(login.sDeviceAddress);
            capturedUsername = readCString(login.sUserName);
            capturedPassword = readCString(login.sPassword);
            HikvisionJnaStructures.DeviceInfoV30 v30 = deviceInfo.deviceInfoV30;
            byte[] serial = "serial-01".getBytes(StandardCharsets.UTF_8);
            System.arraycopy(serial, 0, v30.serialNumber, 0, serial.length);
            v30.startAnalogChannel = 1;
            v30.startDigitalChannel = 33;
            v30.deviceType = 71;
            v30.dvrType = 8;
            deviceInfo.write();
            return 42;
        }

        @Override
        public boolean NET_DVR_Logout(int userId) {
            return true;
        }

        @Override
        public boolean NET_DVR_PTZControlWithSpeed_Other(
                int userId, int channel, int command, int stop, int speed) {
            lastPtz = userId + ":" + channel + ":" + command + ":" + stop + ":" + speed;
            return true;
        }

        @Override
        public int NET_DVR_FindFile_V50(
                int userId, HikvisionJnaStructures.FileSearchCondition condition) {
            condition.read();
            lastFileCondition = condition;
            return 81;
        }

        @Override
        public int NET_DVR_FindNextFile_V50(
                int findHandle, HikvisionJnaStructures.FileSearchData data) {
            data.struStartTime = time(2026, 2, 3, 23, 30, 0);
            data.struStopTime = time(2026, 2, 4, 0, 15, 0);
            data.write();
            return HikvisionFileSearchResult.SUCCESS;
        }

        @Override
        public boolean NET_DVR_FindClose_V30(int findHandle) {
            closedFindHandle = findHandle;
            return true;
        }

        @Override
        public int NET_DVR_RealPlay_V40(
                int userId, HikvisionJnaStructures.PreviewInfo previewInfo,
                HikvisionNativeDataCallback callback, Pointer userData) {
            previewInfo.read();
            lastPreviewInfo = previewInfo;
            realDataCallback = callback;
            return realPlayHandle;
        }

        @Override
        public boolean NET_DVR_StopRealPlay(int streamHandle) {
            stoppedRealPlayHandle = streamHandle;
            return true;
        }

        @Override
        public int NET_DVR_PlayBackByTime_V50(
                int userId, HikvisionJnaStructures.PlaybackParameters parameters) {
            parameters.read();
            lastPlaybackParameters = parameters;
            playbackEvents.add("create:" + userId);
            return playbackHandle;
        }

        @Override
        public boolean NET_DVR_SetPlayDataCallBack_V40(
                int streamHandle, HikvisionNativeDataCallback callback, Pointer userData) {
            playbackEvents.add("callback:" + streamHandle);
            if (playbackCallbackLinkageFailure) {
                throw new UnsatisfiedLinkError("missing playback callback symbol");
            }
            return playbackCallbackResult;
        }

        @Override
        public boolean NET_DVR_PlayBackControl_V40(
                int streamHandle, int command, Pointer input, int inputLength,
                Pointer output, com.sun.jna.ptr.IntByReference outputLength) {
            playbackEvents.add("control:" + streamHandle + ":" + command);
            return playbackControlResult;
        }

        @Override
        public boolean NET_DVR_StopPlayBack(int streamHandle) {
            playbackEvents.add("stop:" + streamHandle);
            return stopPlaybackResult;
        }

        private static HikvisionJnaStructures.TimeSearch time(
                int year, int month, int day, int hour, int minute, int second) {
            HikvisionJnaStructures.TimeSearch value = new HikvisionJnaStructures.TimeSearch();
            value.wYear = (short) year;
            value.byMonth = (byte) month;
            value.byDay = (byte) day;
            value.byHour = (byte) hour;
            value.byMinute = (byte) minute;
            value.bySecond = (byte) second;
            return value;
        }
    }
}
