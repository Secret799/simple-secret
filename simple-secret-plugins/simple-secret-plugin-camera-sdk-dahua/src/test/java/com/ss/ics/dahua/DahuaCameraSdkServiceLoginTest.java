package com.ss.ics.dahua;

import com.ss.ics.domain.LoggedDomain;
import com.ss.ics.domain.LoginDomain;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DahuaCameraSdkServiceLoginTest {

    @Test
    void mapsLoginResultWithoutChangingCredentials() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        DahuaCameraSdkService service = service(nativeApi);
        LoginDomain login = new LoginDomain()
                .setIp("192.0.2.10")
                .setPort("37777")
                .setUsername(" operator ")
                .setPassword(" secret ");

        LoggedDomain logged = service.login(login);

        assertThat(nativeApi.lastLogin.getUsername()).isEqualTo(" operator ");
        assertThat(nativeApi.lastLogin.getPassword()).isEqualTo(" secret ");
        assertThat(logged.getUserId()).isEqualTo("42");
        assertThat(logged.getChannelNo()).isEqualTo("0");
        assertThat(logged.getDeviceId()).isEqualTo("serial-01");
        assertThat(logged.getDeviceType()).isEqualTo("71");
        assertThat(logged.getDeviceCategory()).isEqualTo("4");

        service.logout(logged.getUserId());
        service.close();
        assertThat(nativeApi.events).containsExactly("login", "logout:42", "cleanup");
    }

    @Test
    void rejectsInvalidPortBeforeCallingNativeSdk() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        DahuaCameraSdkService service = service(nativeApi);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.login(login().setPort("70000")))
                .withMessage("port must be between 1 and 65535");
        assertThat(nativeApi.lastLogin).isNull();
        service.close();
    }

    @Test
    void closesRemainingSessionsBeforeSdkCleanup() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        DahuaCameraSdkService service = service(nativeApi);
        service.login(login());

        service.close();
        service.close();

        assertThat(nativeApi.events).containsExactly("login", "logout:42", "cleanup");
    }

    private static LoginDomain login() {
        return new LoginDomain().setIp("192.0.2.10").setPort("37777")
                .setUsername("operator").setPassword("secret");
    }

    private static DahuaCameraSdkService service(FakeDahuaNativeApi nativeApi) {
        return DahuaCameraSdkService.createForTesting(DahuaSdkRuntime.openForTesting(
                DahuaSdkOptions.defaults(Path.of("sdk")), nativeApi));
    }
}
