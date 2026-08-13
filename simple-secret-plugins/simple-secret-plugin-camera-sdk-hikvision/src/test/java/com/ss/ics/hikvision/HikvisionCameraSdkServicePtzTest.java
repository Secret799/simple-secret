package com.ss.ics.hikvision;

import com.ss.ics.constants.enums.PtzControlCommandEnums;
import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.LoginDomain;
import com.ss.ics.domain.PTZControlDomain;
import com.ss.ics.hikvision.internal.HikvisionNativeApi;
import com.ss.ics.hikvision.internal.model.HikvisionNativeLoginResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class HikvisionCameraSdkServicePtzTest {

    @Test
    void mapsFirstLogicalChannelAndSpeedWithoutOffByOne() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        HikvisionCameraSdkService service = service(nativeApi);
        DeviceDomain device = device().setChannel("1");
        PTZControlDomain control = new PTZControlDomain()
                .setCommand(PtzControlCommandEnums.LEFT)
                .setIsBegin(true)
                .setSpeedLevel(10);

        boolean accepted = service.syncControl(device, control);

        assertThat(accepted).isTrue();
        assertThat(nativeApi.events).containsExactly(
                "login", "ptz:42:33:23:0:7", "logout:42");
        service.close();
    }

    @Test
    void executesDurationCommandAsStartAndStopBeforeLogout() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        HikvisionCameraSdkService service = service(nativeApi);
        PTZControlDomain control = new PTZControlDomain()
                .setCommand(PtzControlCommandEnums.ZOOM_IN)
                .setDuration(Duration.ZERO)
                .setSpeedLevel(1);

        boolean accepted = service.syncControl(device(), control);

        assertThat(accepted).isTrue();
        assertThat(nativeApi.events).containsExactly(
                "login", "ptz:42:33:11:0:1", "ptz:42:33:11:1:1", "logout:42");
        service.close();
    }

    @Test
    void boundsAsyncQueueAndCompletesAcceptedCommandsInOrder() throws InterruptedException {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.blockFirstPtz = true;
        nativeApi.interruptedPtzResult = true;
        HikvisionSdkOptions options = new HikvisionSdkOptions(
                Path.of("sdk"), Duration.ofSeconds(5), 1);
        HikvisionSdkRuntime runtime = HikvisionSdkRuntime.openForTesting(options, nativeApi);
        HikvisionCameraSdkService service = HikvisionCameraSdkService.createForTesting(runtime);
        PTZControlDomain control = new PTZControlDomain()
                .setCommand(PtzControlCommandEnums.RIGHT)
                .setIsBegin(true);

        assertThat(service.asyncControl(device(), control)).isTrue();
        assertThat(nativeApi.firstPtzStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(service.asyncControl(device(), control)).isTrue();
        assertThat(service.asyncControl(device(), control)).isFalse();
        nativeApi.releaseFirstPtz.countDown();

        assertThat(nativeApi.completedCommands.await(2, TimeUnit.SECONDS)).isTrue();
        service.close();
        assertThat(nativeApi.events).containsExactly(
                "login", "ptz:42:33:24:0:4", "login",
                "logout:42", "ptz:42:33:24:0:4", "logout:42");
    }

    @Test
    void logsOutWhenChannelValidationFailsAfterLogin() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        HikvisionCameraSdkService service = service(nativeApi);

        Throwable thrown = catchThrowable(() -> service.syncControl(
                device().setChannel("invalid"),
                new PTZControlDomain()
                        .setCommand(PtzControlCommandEnums.LEFT)
                        .setIsBegin(true)));

        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("channel must be a positive integer");
        assertThat(nativeApi.events).containsExactly("login", "logout:42");
        service.close();
    }

    @Test
    void preservesPtzFailureWhenLogoutAlsoFails() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.ptzResult = false;
        nativeApi.logoutResult = false;
        HikvisionCameraSdkService service = service(nativeApi);

        Throwable thrown = catchThrowable(() -> service.syncControl(
                device(),
                new PTZControlDomain()
                        .setCommand(PtzControlCommandEnums.LEFT)
                        .setIsBegin(true)));

        assertThat(thrown)
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision PTZ control failed (code=0)");
        assertThat(thrown.getSuppressed()).singleElement().satisfies(suppressed ->
                assertThat(suppressed)
                        .isInstanceOf(HikvisionSdkException.class)
                        .hasMessage("Hikvision device logout failed (code=0)"));
        nativeApi.logoutResult = true;
        service.close();
    }

    @Test
    void asyncControlLogsInBeforeReturningSoQueuedTaskStoresNoCredentials() throws Exception {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.blockLogin = true;
        HikvisionCameraSdkService service = service(nativeApi);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> accepted = executor.submit(() -> service.asyncControl(
                    device(),
                    new PTZControlDomain()
                            .setCommand(PtzControlCommandEnums.RIGHT)
                            .setIsBegin(true)));

            assertThat(nativeApi.loginStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(accepted.isDone()).isFalse();
            nativeApi.releaseLogin.countDown();
            assertThat(accepted.get(1, TimeUnit.SECONDS)).isTrue();
            service.close();
            assertThat(nativeApi.events).containsExactly(
                    "login", "ptz:42:33:24:0:4", "logout:42");
        } finally {
            nativeApi.releaseLogin.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void preservesInterruptedDurationWhenStopAlsoFails() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.ptzResults.add(true);
        nativeApi.ptzResults.add(false);
        HikvisionCameraSdkService service = service(nativeApi);

        Thread.currentThread().interrupt();
        Throwable thrown;
        try {
            thrown = catchThrowable(() -> service.syncControl(
                    device(),
                    new PTZControlDomain()
                            .setCommand(PtzControlCommandEnums.RIGHT)
                            .setDuration(Duration.ofSeconds(1))));
        } finally {
            Thread.interrupted();
        }

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Hikvision PTZ duration was interrupted");
        assertThat(thrown.getSuppressed()).singleElement().satisfies(suppressed ->
                assertThat(suppressed)
                        .isInstanceOf(HikvisionSdkException.class)
                        .hasMessage("Hikvision PTZ control failed (code=0)"));
        service.close();
    }

    @Test
    void releasesAsyncCapacityWhenNativeLoginLinkageFails() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.loginLinkageFailure = true;
        HikvisionSdkOptions options = new HikvisionSdkOptions(
                Path.of("sdk"), Duration.ofSeconds(5), 1);
        HikvisionCameraSdkService service = HikvisionCameraSdkService.createForTesting(
                HikvisionSdkRuntime.openForTesting(options, nativeApi));
        PTZControlDomain control = new PTZControlDomain()
                .setCommand(PtzControlCommandEnums.LEFT).setIsBegin(true);

        assertThat(catchThrowable(() -> service.asyncControl(device(), control)))
                .isInstanceOf(UnsatisfiedLinkError.class);
        assertThat(catchThrowable(() -> service.asyncControl(device(), control)))
                .isInstanceOf(UnsatisfiedLinkError.class);
        nativeApi.loginLinkageFailure = false;

        assertThat(service.asyncControl(device(), control)).isTrue();
        service.close();
    }

    @Test
    void acceptedAsyncFailureRemainsObservableWithoutUncaughtException() throws Exception {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.ptzResult = false;
        HikvisionCameraSdkService service = service(nativeApi);
        AtomicReference<Throwable> uncaught = new AtomicReference<>();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> uncaught.set(failure));
        try {
            assertThat(service.asyncControl(device(), new PTZControlDomain()
                    .setCommand(PtzControlCommandEnums.LEFT)
                    .setIsBegin(true))).isTrue();
            service.close();

            assertThat(service.lastAsyncPtzFailure()).hasValueSatisfying(failure ->
                    assertThat(failure)
                            .isInstanceOf(HikvisionSdkException.class)
                            .hasMessage("Hikvision PTZ control failed (code=0)"));
            assertThat(uncaught.get()).isNull();
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
            service.close();
        }
    }

    @Test
    void closeContinuesRuntimeCleanupWhenInterrupted() throws InterruptedException {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.blockFirstPtz = true;
        HikvisionCameraSdkService service = service(nativeApi);
        PTZControlDomain control = new PTZControlDomain()
                .setCommand(PtzControlCommandEnums.LEFT).setIsBegin(true);
        assertThat(service.asyncControl(device(), control)).isTrue();
        assertThat(nativeApi.firstPtzStarted.await(1, TimeUnit.SECONDS)).isTrue();

        Thread.currentThread().interrupt();
        try {
            assertThat(catchThrowable(service::close))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Interrupted while closing Hikvision PTZ executor");
        } finally {
            Thread.interrupted();
            nativeApi.releaseFirstPtz.countDown();
        }

        assertThat(nativeApi.cleanupCalls).isOne();
    }

    private static DeviceDomain device() {
        return new DeviceDomain()
                .setIp("192.0.2.10")
                .setPort("8000")
                .setUsername("operator")
                .setPassword("secret");
    }

    private static HikvisionCameraSdkService service(FakeNativeApi nativeApi) {
        HikvisionSdkRuntime runtime = HikvisionSdkRuntime.openForTesting(
                HikvisionSdkOptions.defaults(Path.of("sdk")), nativeApi);
        return HikvisionCameraSdkService.createForTesting(runtime);
    }

    private static final class FakeNativeApi implements HikvisionNativeApi {
        private final List<String> events = new ArrayList<>();
        private final CountDownLatch firstPtzStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFirstPtz = new CountDownLatch(1);
        private final CountDownLatch completedCommands = new CountDownLatch(2);
        private final CountDownLatch loginStarted = new CountDownLatch(1);
        private final CountDownLatch releaseLogin = new CountDownLatch(1);
        private boolean blockFirstPtz;
        private boolean blockLogin;
        private boolean ptzResult = true;
        private boolean logoutResult = true;
        private boolean loginLinkageFailure;
        private boolean interruptedPtzResult;
        private int cleanupCalls;
        private final Deque<Boolean> ptzResults = new ArrayDeque<>();

        @Override
        public boolean initialize() {
            return true;
        }

        @Override
        public boolean cleanup() {
            cleanupCalls++;
            return true;
        }

        @Override
        public int lastError() {
            return 0;
        }

        @Override
        public HikvisionNativeLoginResult login(LoginDomain login) {
            events.add("login");
            if (loginLinkageFailure) {
                throw new UnsatisfiedLinkError("missing login symbol");
            }
            if (blockLogin) {
                loginStarted.countDown();
                try {
                    releaseLogin.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("login interrupted", exception);
                }
            }
            return new HikvisionNativeLoginResult(42, 33, 71, 8, "serial-01");
        }

        @Override
        public boolean logout(int userId) {
            events.add("logout:" + userId);
            completedCommands.countDown();
            return logoutResult;
        }

        @Override
        public boolean ptzControl(int userId, int channel, int command, int stop, int speed) {
            events.add("ptz:" + userId + ":" + channel + ":" + command + ":" + stop + ":" + speed);
            if (blockFirstPtz) {
                blockFirstPtz = false;
                firstPtzStarted.countDown();
                try {
                    releaseFirstPtz.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return interruptedPtzResult;
                }
            }
            return ptzResults.isEmpty() ? ptzResult : ptzResults.removeFirst();
        }
    }
}
