package com.ss.ics.hikvision;

import com.ss.ics.domain.LoggedDomain;
import com.ss.ics.domain.LoginDomain;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HikvisionCameraSdkServiceLoginTest {

    @Test
    void mapsLoginResultWithoutChangingCredentials() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        HikvisionCameraSdkService service = service(nativeApi);
        LoginDomain login = new LoginDomain()
                .setIp("192.0.2.10")
                .setPort("8000")
                .setUsername(" operator ")
                .setPassword(" secret ");

        LoggedDomain logged = service.login(login);

        assertThat(nativeApi.lastLogin.getUsername()).isEqualTo(" operator ");
        assertThat(nativeApi.lastLogin.getPassword()).isEqualTo(" secret ");
        assertThat(logged.getUserId()).isEqualTo("42");
        assertThat(logged.getChannelNo()).isEqualTo("33");
        assertThat(logged.getDeviceId()).isEqualTo("serial-01");
        assertThat(logged.getDeviceType()).isEqualTo("71");
        assertThat(logged.getDeviceCategory()).isEqualTo("8");

        service.logout(logged.getUserId());
        service.close();

        assertThat(nativeApi.logoutHandles).containsExactly(42);
        assertThat(nativeApi.cleanupCalls).isEqualTo(1);
    }

    @Test
    void rejectsInvalidPortBeforeCallingNativeSdk() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        HikvisionCameraSdkService service = service(nativeApi);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.login(new LoginDomain()
                        .setIp("192.0.2.10")
                        .setPort("70000")
                        .setUsername("operator")
                        .setPassword("secret")))
                .withMessage("port must be between 1 and 65535");
        assertThat(nativeApi.lastLogin).isNull();

        service.close();
    }

    @Test
    void closesRemainingSessionsBeforeSdkCleanup() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        HikvisionCameraSdkService service = service(nativeApi);
        service.login(new LoginDomain()
                .setIp("192.0.2.10")
                .setPort("8000")
                .setUsername("operator")
                .setPassword("secret"));

        service.close();
        service.close();

        assertThat(nativeApi.events).containsExactly("login", "logout:42", "cleanup");
    }

    @Test
    void closeWaitsForLoginAndThenReleasesItsSession() throws Exception {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.blockLogin = true;
        HikvisionCameraSdkService service = service(nativeApi);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<LoggedDomain> login = executor.submit(() -> service.login(new LoginDomain()
                    .setIp("192.0.2.10")
                    .setPort("8000")
                    .setUsername("operator")
                    .setPassword("secret")));
            assertThat(nativeApi.loginStarted.await(1, TimeUnit.SECONDS)).isTrue();

            Future<?> close = executor.submit(service::close);

            assertThat(nativeApi.cleanupStarted.await(100, TimeUnit.MILLISECONDS)).isFalse();
            nativeApi.releaseLogin.countDown();
            assertThat(login.get(1, TimeUnit.SECONDS).getUserId()).isEqualTo("42");
            close.get(1, TimeUnit.SECONDS);
            assertThat(nativeApi.events).containsExactly("login", "logout:42", "cleanup");
        } finally {
            nativeApi.releaseLogin.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void retriesServiceCloseWhenRuntimeCleanupFails() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.cleanupResult = false;
        HikvisionCameraSdkService service = service(nativeApi);

        assertThatThrownBy(service::close)
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision SDK cleanup failed (code=0)");
        nativeApi.cleanupResult = true;
        service.close();

        assertThat(nativeApi.cleanupCalls).isEqualTo(2);
    }

    private static HikvisionCameraSdkService service(FakeNativeApi nativeApi) {
        HikvisionSdkRuntime runtime = HikvisionSdkRuntime.openForTesting(
                HikvisionSdkOptions.defaults(Path.of("sdk")), nativeApi);
        return HikvisionCameraSdkService.createForTesting(runtime);
    }

    private static final class FakeNativeApi implements HikvisionNativeApi {
        private final List<Integer> logoutHandles = new ArrayList<>();
        private final List<String> events = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch loginStarted = new CountDownLatch(1);
        private final CountDownLatch releaseLogin = new CountDownLatch(1);
        private final CountDownLatch cleanupStarted = new CountDownLatch(1);
        private LoginDomain lastLogin;
        private int cleanupCalls;
        private boolean blockLogin;
        private boolean cleanupResult = true;

        @Override
        public boolean initialize() {
            return true;
        }

        @Override
        public boolean cleanup() {
            cleanupStarted.countDown();
            cleanupCalls++;
            events.add("cleanup");
            return cleanupResult;
        }

        @Override
        public int lastError() {
            return 0;
        }

        @Override
        public HikvisionNativeLoginResult login(LoginDomain login) {
            lastLogin = login;
            events.add("login");
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
            logoutHandles.add(userId);
            events.add("logout:" + userId);
            return true;
        }
    }
}
