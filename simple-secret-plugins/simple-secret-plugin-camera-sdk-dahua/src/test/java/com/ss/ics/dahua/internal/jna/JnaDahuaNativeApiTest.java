package com.ss.ics.dahua.internal.jna;

import com.ss.ics.dahua.DahuaJnaStructures;
import com.ss.ics.dahua.DahuaPoint;
import com.ss.ics.dahua.DahuaSdkException;
import com.ss.ics.dahua.DahuaSdkOptions;
import com.ss.ics.dahua.internal.DahuaNativeApi;
import com.ss.ics.dahua.internal.model.DahuaNativeLoginResult;
import com.ss.ics.dahua.internal.model.DahuaNativeRadiometryRecord;
import com.ss.ics.dahua.internal.model.DahuaNativeRegionTemperature;
import com.ss.ics.dahua.internal.model.DahuaNativeSearchStart;
import com.ss.ics.dahua.internal.model.DahuaNativeStreamFrame;
import com.ss.ics.dahua.internal.model.DahuaNativeTemperatureSummary;
import com.ss.ics.dahua.internal.model.DahuaNativeThermalData;
import com.ss.ics.domain.LoginDomain;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JnaDahuaNativeApiTest {

    @TempDir
    Path tempDirectory;

    @Test
    void copiesLoginResultAndClearsCredentialBuffersAfterCall() throws IOException {
        Files.createFile(tempDirectory.resolve("dhnetsdk.dll"));
        CapturingLibrary library = new CapturingLibrary();
        JnaDahuaNativeApi api = JnaDahuaNativeApi.load(
                DahuaSdkOptions.defaults(tempDirectory), "Windows 11", ignored -> library);

        DahuaNativeLoginResult result = api.login(new LoginDomain()
                .setIp("192.0.2.10")
                .setPort("37777")
                .setUsername("operator")
                .setPassword("secret"));

        assertThat(result.userId()).isEqualTo(42L);
        assertThat(result.serialNumber()).isEqualTo("serial-01");
        assertThat(result.deviceType()).isEqualTo(71);
        assertThat(result.deviceCategory()).isEqualTo(71);
        assertThat(library.usernameAtCall).isEqualTo("operator");
        assertThat(library.passwordAtCall).isEqualTo("secret");
        assertThat(library.login.szUserName).containsOnly((byte) 0);
        assertThat(library.login.szPassword).containsOnly((byte) 0);
        assertThat(library.login.szIP).containsOnly((byte) 0);
    }

    @Test
    void wrapsNativeLoadFailureWithoutAbsolutePath() throws IOException {
        Files.createFile(tempDirectory.resolve("dhnetsdk.dll"));

        assertThatThrownBy(() -> JnaDahuaNativeApi.load(
                DahuaSdkOptions.defaults(tempDirectory), "Windows", ignored -> {
                    throw new UnsatisfiedLinkError(tempDirectory.toString());
                }))
                .isInstanceOf(DahuaSdkException.class)
                .hasMessage("Dahua native library load failed (code=-1)")
                .hasMessageNotContaining(tempDirectory.toString());
    }

    @Test
    void parsesThermalCallbackAndReleasesSubscription() throws IOException {
        Files.createFile(tempDirectory.resolve("dhnetsdk.dll"));
        CapturingLibrary library = new CapturingLibrary();
        JnaDahuaNativeApi api = JnaDahuaNativeApi.load(
                DahuaSdkOptions.defaults(tempDirectory), "Windows", ignored -> library);
        List<DahuaNativeThermalData> received = new ArrayList<>();

        long handle = api.attachRadiometry(42L, 0, received::add);
        DahuaJnaStructures.ThermalData data = new DahuaJnaStructures.ThermalData();
        data.metadata.width = 2;
        data.metadata.height = 1;
        data.metadata.time.year = 2026;
        data.metadata.time.month = 1;
        data.metadata.time.day = 2;
        data.dataBuffer = new Memory(1);
        data.bufferSize = 1;
        data.write();
        library.radiometryCallback.invoke(
                new DahuaJnaStructures.DahuaLong(handle), data, 1, Pointer.NULL);

        assertThat(handle).isEqualTo(77L);
        assertThat(received).singleElement().satisfies(frame -> {
            assertThat(frame.timestamp()).isEqualTo(LocalDateTime.of(2026, 1, 2, 0, 0));
            assertThat(frame.grayscale()).containsExactly((short) 1, (short) 2);
            assertThat(frame.temperatures()).containsExactly(10.5f, 11.5f);
        });
        assertThat(api.fetchRadiometry(42L, 0)).isEqualTo(2);
        assertThat(api.detachRadiometry(handle)).isTrue();
    }

    @Test
    void ignoresOversizedThermalCallbackBeforeParsingOrNotifyingConsumer() throws IOException {
        Files.createFile(tempDirectory.resolve("dhnetsdk.dll"));
        CapturingLibrary library = new CapturingLibrary();
        JnaDahuaNativeApi api = JnaDahuaNativeApi.load(
                DahuaSdkOptions.defaults(tempDirectory), "Windows", ignored -> library);
        List<DahuaNativeThermalData> received = new ArrayList<>();

        api.attachRadiometry(42L, 0, received::add);
        DahuaJnaStructures.ThermalData data = thermalData(2, 2, 16 * 1024 * 1024 + 1);

        library.radiometryCallback.invoke(
                new DahuaJnaStructures.DahuaLong(77L), data, 1, Pointer.NULL);

        assertThat(library.radiometryParseCalls).isZero();
        assertThat(received).isEmpty();
    }

    @Test
    void containsErrorsThrownByPreviewConsumerAtNativeCallbackBoundary() throws IOException {
        Files.createFile(tempDirectory.resolve("dhnetsdk.dll"));
        CapturingLibrary library = new CapturingLibrary();
        JnaDahuaNativeApi api = JnaDahuaNativeApi.load(
                DahuaSdkOptions.defaults(tempDirectory), "Windows", ignored -> library);

        long handle = api.startPreview(42L, 0, 0, frame -> {
            throw new AssertionError("consumer failure");
        });

        assertThatCode(() -> library.previewCallback.invoke(
                new DahuaJnaStructures.DahuaLong(handle), previewInfo(4), Pointer.NULL))
                .doesNotThrowAnyException();
    }

    @Test
    void ignoresOversizedPreviewBufferBeforeNotifyingConsumer() throws IOException {
        Files.createFile(tempDirectory.resolve("dhnetsdk.dll"));
        CapturingLibrary library = new CapturingLibrary();
        JnaDahuaNativeApi api = JnaDahuaNativeApi.load(
                DahuaSdkOptions.defaults(tempDirectory), "Windows", ignored -> library);
        List<DahuaNativeStreamFrame> received = new ArrayList<>();

        long handle = api.startPreview(42L, 0, 0, received::add);
        library.previewCallback.invoke(new DahuaJnaStructures.DahuaLong(handle),
                previewInfo(16 * 1024 * 1024 + 1), Pointer.NULL);

        assertThat(received).isEmpty();
    }

    @Test
    void stopPreviewWaitsForInFlightConsumerBeforeCallingNativeStop() throws Exception {
        Files.createFile(tempDirectory.resolve("dhnetsdk.dll"));
        CapturingLibrary library = new CapturingLibrary();
        JnaDahuaNativeApi api = JnaDahuaNativeApi.load(
                DahuaSdkOptions.defaults(tempDirectory), "Windows", ignored -> library);
        CountDownLatch consumerStarted = new CountDownLatch(1);
        CountDownLatch releaseConsumer = new CountDownLatch(1);
        CountDownLatch stopAttempted = new CountDownLatch(1);

        long handle = api.startPreview(42L, 0, 0, frame -> {
            consumerStarted.countDown();
            await(releaseConsumer);
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> callback = executor.submit(() -> library.previewCallback.invoke(
                    new DahuaJnaStructures.DahuaLong(handle), previewInfo(4), Pointer.NULL));
            assertThat(consumerStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> stopped = executor.submit(() -> {
                stopAttempted.countDown();
                return api.stopPreview(handle);
            });
            assertThat(stopAttempted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(library.previewStopCalled.await(150, TimeUnit.MILLISECONDS)).isFalse();

            releaseConsumer.countDown();

            assertThat(stopped.get(1, TimeUnit.SECONDS)).isTrue();
            callback.get(1, TimeUnit.SECONDS);
            assertThat(library.previewStopCalled.getCount()).isZero();
        } finally {
            releaseConsumer.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void detachRadiometryWaitsForInFlightConsumerBeforeCallingNativeDetach() throws Exception {
        Files.createFile(tempDirectory.resolve("dhnetsdk.dll"));
        CapturingLibrary library = new CapturingLibrary();
        JnaDahuaNativeApi api = JnaDahuaNativeApi.load(
                DahuaSdkOptions.defaults(tempDirectory), "Windows", ignored -> library);
        CountDownLatch consumerStarted = new CountDownLatch(1);
        CountDownLatch releaseConsumer = new CountDownLatch(1);
        CountDownLatch detachAttempted = new CountDownLatch(1);

        long handle = api.attachRadiometry(42L, 0, data -> {
            consumerStarted.countDown();
            await(releaseConsumer);
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> callback = executor.submit(() -> library.radiometryCallback.invoke(
                    new DahuaJnaStructures.DahuaLong(handle), thermalData(2, 1, 1), 1, Pointer.NULL));
            assertThat(consumerStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> detached = executor.submit(() -> {
                detachAttempted.countDown();
                return api.detachRadiometry(handle);
            });
            assertThat(detachAttempted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(library.radiometryDetachCalled.await(150, TimeUnit.MILLISECONDS)).isFalse();

            releaseConsumer.countDown();

            assertThat(detached.get(1, TimeUnit.SECONDS)).isTrue();
            callback.get(1, TimeUnit.SECONDS);
            assertThat(library.radiometryDetachCalled.getCount()).isZero();
        } finally {
            releaseConsumer.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void stopPreviewDoesNotCallNativeStopWhenCallbackDrainTimesOut() throws Exception {
        Files.createFile(tempDirectory.resolve("dhnetsdk.dll"));
        CapturingLibrary library = new CapturingLibrary();
        DahuaSdkOptions options = new DahuaSdkOptions(
                tempDirectory, Duration.ofMillis(50), Duration.ofSeconds(1), 1, 1);
        JnaDahuaNativeApi api = JnaDahuaNativeApi.load(
                options, "Windows", ignored -> library);
        CountDownLatch consumerStarted = new CountDownLatch(1);
        CountDownLatch releaseConsumer = new CountDownLatch(1);
        long handle = api.startPreview(42L, 0, 0, frame -> {
            consumerStarted.countDown();
            await(releaseConsumer);
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> callback = executor.submit(() -> library.previewCallback.invoke(
                    new DahuaJnaStructures.DahuaLong(handle), previewInfo(4), Pointer.NULL));
            assertThat(consumerStarted.await(1, TimeUnit.SECONDS)).isTrue();

            assertThat(api.stopPreview(handle)).isFalse();
            assertThat(library.previewStopCalled.getCount()).isOne();

            releaseConsumer.countDown();
            callback.get(1, TimeUnit.SECONDS);
            assertThat(api.stopPreview(handle)).isTrue();
            assertThat(library.previewStopCalled.getCount()).isZero();
        } finally {
            releaseConsumer.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void duplicatePreviewHandleWaitsForCallbacksFromEveryRegistration() throws Exception {
        Files.createFile(tempDirectory.resolve("dhnetsdk.dll"));
        CapturingLibrary library = new CapturingLibrary();
        JnaDahuaNativeApi api = JnaDahuaNativeApi.load(
                DahuaSdkOptions.defaults(tempDirectory), "Windows", ignored -> library);
        CountDownLatch consumerStarted = new CountDownLatch(1);
        CountDownLatch releaseConsumer = new CountDownLatch(1);
        long firstHandle = api.startPreview(42L, 0, 0, frame -> {
            consumerStarted.countDown();
            await(releaseConsumer);
        });
        long secondHandle = api.startPreview(42L, 0, 0, frame -> { });
        assertThat(secondHandle).isEqualTo(firstHandle);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> callback = executor.submit(() -> library.previewCallbackHistory.get(0)
                    .invoke(new DahuaJnaStructures.DahuaLong(firstHandle),
                            previewInfo(4), Pointer.NULL));
            assertThat(consumerStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> stopped = executor.submit(() -> api.stopPreview(firstHandle));
            assertThat(library.previewStopCalled.await(150, TimeUnit.MILLISECONDS)).isFalse();

            releaseConsumer.countDown();

            assertThat(stopped.get(1, TimeUnit.SECONDS)).isTrue();
            callback.get(1, TimeUnit.SECONDS);
        } finally {
            releaseConsumer.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void mapsTemperatureQueriesAndHistoricalPages() throws IOException {
        Files.createFile(tempDirectory.resolve("dhnetsdk.dll"));
        CapturingLibrary library = new CapturingLibrary();
        JnaDahuaNativeApi api = JnaDahuaNativeApi.load(
                DahuaSdkOptions.defaults(tempDirectory), "Windows", ignored -> library);

        DahuaNativeTemperatureSummary point = api.queryPointTemperature(42L, 0, 1, 2);
        DahuaNativeRegionTemperature region = api.queryRegionTemperature(
                42L, 0, List.of(new DahuaPoint(0, 0),
                        new DahuaPoint(1, 0), new DahuaPoint(0, 1)));
        DahuaNativeSearchStart search = api.startRadiometrySearch(
                42L, 0, 3, 5,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 2, 0, 0));
        List<DahuaNativeRadiometryRecord> page =
                api.findRadiometryPage(42L, search.finderHandle(), 0, 1);

        assertThat(point.average()).isEqualTo(20.5f);
        assertThat(region.maximum()).isEqualTo(30.5);
        assertThat(search).isEqualTo(new DahuaNativeSearchStart(88, 1));
        assertThat(page).singleElement().satisfies(record -> {
            assertThat(record.name()).isEqualTo("rule");
            assertThat(record.coordinates()).containsExactly(new DahuaPoint(1, 2));
        });
        assertThat(api.stopRadiometrySearch(42L, 88)).isTrue();
    }

    private static DahuaJnaStructures.DataCallbackInfo previewInfo(int bufferSize) {
        Memory buffer = new Memory(4);
        buffer.write(0, new byte[]{0, 0, 0, 1}, 0, 4);
        DahuaJnaStructures.DataCallbackInfo info = new DahuaJnaStructures.DataCallbackInfo();
        info.buffer = buffer;
        info.bufferSize = bufferSize;
        info.write();
        return info;
    }

    private static DahuaJnaStructures.ThermalData thermalData(
            int width, int height, int bufferSize) {
        DahuaJnaStructures.ThermalData data = new DahuaJnaStructures.ThermalData();
        data.metadata.width = width;
        data.metadata.height = height;
        data.metadata.time.year = 2026;
        data.metadata.time.month = 1;
        data.metadata.time.day = 2;
        data.dataBuffer = new Memory(1);
        data.bufferSize = bufferSize;
        data.write();
        return data;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static final class CapturingLibrary implements DahuaNetSdkLibrary {
        private DahuaJnaStructures.HighSecurityLoginInput login;
        private String usernameAtCall;
        private String passwordAtCall;
        private DahuaNetSdkLibrary.DataCallback previewCallback;
        private final List<DahuaNetSdkLibrary.DataCallback> previewCallbackHistory =
                new ArrayList<>();
        private DahuaNetSdkLibrary.RadiometryCallback radiometryCallback;
        private int radiometryParseCalls;
        private final CountDownLatch previewStopCalled = new CountDownLatch(1);
        private final CountDownLatch radiometryDetachCalled = new CountDownLatch(1);

        @Override
        public boolean CLIENT_Init(DisconnectCallback callback, Pointer user) {
            return true;
        }

        @Override
        public void CLIENT_SetAutoReconnect(ReconnectCallback callback, Pointer user) {
        }

        @Override
        public void CLIENT_SetConnectTime(int waitTimeMillis, int tryTimes) {
        }

        @Override
        public void CLIENT_SetNetworkParam(DahuaJnaStructures.NetworkParam networkParam) {
        }

        @Override
        public DahuaJnaStructures.DahuaLong CLIENT_LoginWithHighLevelSecurity(
                DahuaJnaStructures.HighSecurityLoginInput input,
                DahuaJnaStructures.HighSecurityLoginOutput output) {
            login = input;
            usernameAtCall = JnaDahuaNativeApi.readUtf8(input.szUserName);
            passwordAtCall = JnaDahuaNativeApi.readUtf8(input.szPassword);
            byte[] serial = "serial-01".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            System.arraycopy(serial, 0, output.deviceInfo.serialNumber, 0, serial.length);
            output.deviceInfo.deviceType = 71;
            output.deviceInfo.channelCount = 4;
            output.write();
            return new DahuaJnaStructures.DahuaLong(42L);
        }

        @Override
        public boolean CLIENT_Logout(DahuaJnaStructures.DahuaLong loginId) {
            return true;
        }

        @Override
        public boolean CLIENT_DHPTZControlEx(DahuaJnaStructures.DahuaLong loginId, int channel,
                                             int command, int param1, int param2, int param3, int stop) {
            return true;
        }

        @Override
        public DahuaJnaStructures.DahuaLong CLIENT_RealPlayByDataType(
                DahuaJnaStructures.DahuaLong loginId,
                DahuaJnaStructures.RealPlayInput input,
                DahuaJnaStructures.RealPlayOutput output,
                int timeoutMillis) {
            previewCallback = input.dataCallback;
            previewCallbackHistory.add(input.dataCallback);
            return new DahuaJnaStructures.DahuaLong(1L);
        }

        @Override
        public boolean CLIENT_StopRealPlayEx(DahuaJnaStructures.DahuaLong previewHandle) {
            previewStopCalled.countDown();
            return true;
        }

        @Override
        public DahuaJnaStructures.DahuaLong CLIENT_RadiometryAttach(
                DahuaJnaStructures.DahuaLong loginId,
                DahuaJnaStructures.RadiometryAttachInput input,
                DahuaJnaStructures.RadiometryAttachOutput output,
                int timeoutMillis) {
            radiometryCallback = input.callback;
            return new DahuaJnaStructures.DahuaLong(77L);
        }

        @Override
        public boolean CLIENT_RadiometryDetach(
                DahuaJnaStructures.DahuaLong subscriptionHandle) {
            radiometryDetachCalled.countDown();
            return true;
        }

        @Override
        public boolean CLIENT_RadiometryFetch(
                DahuaJnaStructures.DahuaLong loginId,
                DahuaJnaStructures.RadiometryFetchInput input,
                DahuaJnaStructures.RadiometryFetchOutput output,
                int timeoutMillis) {
            output.status = 2;
            output.write();
            return true;
        }

        @Override
        public boolean CLIENT_RadiometryDataParse(
                DahuaJnaStructures.ThermalData data,
                short[] grayscale,
                float[] temperatures) {
            radiometryParseCalls++;
            grayscale[0] = 1;
            grayscale[1] = 2;
            temperatures[0] = 10.5f;
            temperatures[1] = 11.5f;
            return true;
        }

        @Override
        public boolean CLIENT_QueryDevInfo(
                DahuaJnaStructures.DahuaLong loginId, int queryType,
                Pointer input, Pointer output, Pointer reserved, int timeoutMillis) {
            if (queryType == 0x0c) {
                DahuaJnaStructures.PointTemperatureOutput result =
                        new DahuaJnaStructures.PointTemperatureOutput(output);
                fillTemperature(result.temperature);
                result.write();
                return true;
            }
            DahuaJnaStructures.ItemTemperatureOutput result =
                    new DahuaJnaStructures.ItemTemperatureOutput(output);
            fillTemperature(result.temperature);
            result.write();
            return true;
        }

        @Override
        public boolean CLIENT_RadiometryGetRandomRegionTemper(
                DahuaJnaStructures.DahuaLong loginId,
                Pointer input, Pointer output, int timeoutMillis) {
            DahuaJnaStructures.RegionTemperatureOutput result =
                    new DahuaJnaStructures.RegionTemperatureOutput(output);
            result.temperature.temperatureUnit = 0;
            result.temperature.average = 2050;
            result.temperature.maximum = 3050;
            result.temperature.minimum = 1050;
            result.temperature.maximumPoint.x = 2;
            result.temperature.maximumPoint.y = 3;
            result.temperature.minimumPoint.x = 4;
            result.temperature.minimumPoint.y = 5;
            result.write();
            return true;
        }

        @Override
        public boolean CLIENT_StartFind(
                DahuaJnaStructures.DahuaLong loginId, int type,
                Pointer input, Pointer output, int timeoutMillis) {
            DahuaJnaStructures.RadiometrySearchOutput result =
                    new DahuaJnaStructures.RadiometrySearchOutput(output);
            result.finderHandle = 88;
            result.totalCount = 1;
            result.write();
            return true;
        }

        @Override
        public boolean CLIENT_DoFind(
                DahuaJnaStructures.DahuaLong loginId, int type,
                Pointer input, Pointer output, int timeoutMillis) {
            DahuaJnaStructures.RadiometryPageOutput result =
                    new DahuaJnaStructures.RadiometryPageOutput(output);
            result.found = 1;
            DahuaJnaStructures.RadiometryRecord record = result.records[0];
            record.time.year = 2026;
            record.time.month = 1;
            record.time.day = 1;
            record.presetId = 1;
            record.ruleId = 2;
            byte[] name = "rule".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            System.arraycopy(name, 0, record.name, 0, name.length);
            record.channel = 0;
            fillTemperature(record.temperature);
            record.coordinateCount = 1;
            record.coordinates[0].x = 1;
            record.coordinates[0].y = 2;
            result.write();
            return true;
        }

        @Override
        public boolean CLIENT_StopFind(
                DahuaJnaStructures.DahuaLong loginId, int type,
                Pointer input, Pointer output, int timeoutMillis) {
            return true;
        }

        private static void fillTemperature(DahuaJnaStructures.TemperatureInfo temperature) {
            temperature.meterType = 1;
            temperature.temperatureUnit = 0;
            temperature.average = 20.5f;
            temperature.maximum = 30.5f;
            temperature.minimum = 10.5f;
            temperature.middle = 19.5f;
            temperature.standardDeviation = 1.5f;
        }

        @Override
        public int CLIENT_GetLastError() {
            return 0;
        }

        @Override
        public void CLIENT_Cleanup() {
        }
    }
}
