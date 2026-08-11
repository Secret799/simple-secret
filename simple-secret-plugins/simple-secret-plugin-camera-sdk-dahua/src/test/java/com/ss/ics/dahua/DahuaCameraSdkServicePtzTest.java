package com.ss.ics.dahua;

import com.ss.ics.constants.enums.PtzControlCommandEnums;
import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.PTZControlDomain;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class DahuaCameraSdkServicePtzTest {

    @Test
    void mapsFirstLogicalChannelAndSpeedWithoutOffByOne() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        DahuaCameraSdkService service = service(nativeApi);

        assertThat(service.syncControl(device().setChannel("1"), new PTZControlDomain()
                .setCommand(PtzControlCommandEnums.LEFT).setIsBegin(true).setSpeedLevel(10)))
                .isTrue();

        assertThat(nativeApi.events).containsExactly(
                "login", "ptz:42:0:2:0:8:0:0", "logout:42");
        service.close();
    }

    @Test
    void mapsDiagonalCommandAndExecutesDurationAsStartThenStop() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        DahuaCameraSdkService service = service(nativeApi);

        assertThat(service.syncControl(device(), new PTZControlDomain()
                .setCommand(PtzControlCommandEnums.RIGHT_UP)
                .setDuration(Duration.ZERO).setSpeedLevel(1))).isTrue();

        assertThat(nativeApi.events).containsExactly(
                "login", "ptz:42:0:33:1:1:0:0", "ptz:42:0:33:1:1:0:1", "logout:42");
        service.close();
    }

    @Test
    void boundsAsyncQueueAndCompletesAcceptedCommands() throws InterruptedException {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        nativeApi.blockFirstPtz = true;
        DahuaSdkOptions options = new DahuaSdkOptions(
                Path.of("sdk"), Duration.ofSeconds(3), Duration.ofSeconds(5), 1, 10_000);
        DahuaCameraSdkService service = DahuaCameraSdkService.createForTesting(
                DahuaSdkRuntime.openForTesting(options, nativeApi));
        PTZControlDomain control = new PTZControlDomain()
                .setCommand(PtzControlCommandEnums.RIGHT).setIsBegin(true);

        assertThat(service.asyncControl(device(), control)).isTrue();
        assertThat(nativeApi.firstPtzStarted.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(service.asyncControl(device(), control)).isTrue();
        assertThat(service.asyncControl(device(), control)).isFalse();
        nativeApi.releaseFirstPtz.countDown();
        assertThat(nativeApi.completedCommands.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        service.close();
    }

    @Test
    void logsOutWhenChannelValidationFailsAfterLogin() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        DahuaCameraSdkService service = service(nativeApi);

        Throwable thrown = catchThrowable(() -> service.syncControl(
                device().setChannel("invalid"), new PTZControlDomain()
                        .setCommand(PtzControlCommandEnums.LEFT).setIsBegin(true)));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("channel must be a positive integer");
        assertThat(nativeApi.events).containsExactly("login", "logout:42");
        service.close();
    }

    @Test
    void preservesPtzFailureWhenLogoutAlsoFails() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        nativeApi.ptzResult = false;
        nativeApi.logoutResult = false;
        DahuaCameraSdkService service = service(nativeApi);

        Throwable thrown = catchThrowable(() -> service.syncControl(device(),
                new PTZControlDomain().setCommand(PtzControlCommandEnums.LEFT).setIsBegin(true)));

        assertThat(thrown).isInstanceOf(DahuaSdkException.class)
                .hasMessage("Dahua PTZ control failed (code=0)");
        assertThat(thrown.getSuppressed()).singleElement().satisfies(suppressed ->
                assertThat(suppressed).isInstanceOf(DahuaSdkException.class)
                        .hasMessage("Dahua device logout failed (code=0)"));
        nativeApi.logoutResult = true;
        service.close();
    }

    @Test
    void rejectsAsyncControlWithoutExceptionAfterClose() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        DahuaCameraSdkService service = service(nativeApi);
        service.close();

        assertThat(service.asyncControl(device(), new PTZControlDomain()
                .setCommand(PtzControlCommandEnums.LEFT).setIsBegin(true))).isFalse();
    }

    private static DeviceDomain device() {
        return new DeviceDomain().setIp("192.0.2.10").setPort("37777")
                .setUsername("operator").setPassword("secret");
    }

    private static DahuaCameraSdkService service(FakeDahuaNativeApi nativeApi) {
        return DahuaCameraSdkService.createForTesting(DahuaSdkRuntime.openForTesting(
                DahuaSdkOptions.defaults(Path.of("sdk")), nativeApi));
    }
}
