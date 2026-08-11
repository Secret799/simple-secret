package com.ss.ics.hikvision;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HikvisionSdkRuntimeTest {

    @Test
    void initializesAndCleansUpExactlyOnce() {
        FakeNativeApi nativeApi = new FakeNativeApi(true, true, 0);

        HikvisionSdkRuntime runtime = HikvisionSdkRuntime.openForTesting(
                HikvisionSdkOptions.defaults(Path.of("sdk")), nativeApi);
        runtime.close();
        runtime.close();

        assertThat(nativeApi.initializeCalls).isEqualTo(1);
        assertThat(nativeApi.cleanupCalls).isEqualTo(1);
    }

    @Test
    void reportsInitializationFailureWithoutLibraryPathOrCredentials() {
        FakeNativeApi nativeApi = new FakeNativeApi(false, true, 17);

        assertThatThrownBy(() -> HikvisionSdkRuntime.openForTesting(
                HikvisionSdkOptions.defaults(Path.of("secret-sdk-path")), nativeApi))
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision SDK initialization failed (code=17)")
                .hasMessageNotContaining("secret-sdk-path");
        assertThat(nativeApi.cleanupCalls).isZero();
    }

    @Test
    void rejectsConcurrentRuntimeAndAllowsOpeningAfterClose() {
        FakeNativeApi firstApi = new FakeNativeApi(true, true, 0);
        FakeNativeApi secondApi = new FakeNativeApi(true, true, 0);
        HikvisionSdkOptions options = HikvisionSdkOptions.defaults(Path.of("sdk"));
        HikvisionSdkRuntime first = HikvisionSdkRuntime.openForTesting(options, firstApi);

        assertThatThrownBy(() -> HikvisionSdkRuntime.openForTesting(options, secondApi))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only one Hikvision SDK runtime may be open per process");
        assertThat(secondApi.initializeCalls).isZero();

        first.close();
        HikvisionSdkRuntime reopened = HikvisionSdkRuntime.openForTesting(options, secondApi);
        reopened.close();
        assertThat(secondApi.initializeCalls).isEqualTo(1);
    }

    @Test
    void cleanupFailureKeepsProcessGateAndCanBeRetried() {
        FakeNativeApi firstApi = new FakeNativeApi(true, false, 17);
        FakeNativeApi secondApi = new FakeNativeApi(true, true, 0);
        HikvisionSdkOptions options = HikvisionSdkOptions.defaults(Path.of("sdk"));
        HikvisionSdkRuntime first = HikvisionSdkRuntime.openForTesting(options, firstApi);

        assertThatThrownBy(first::close)
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision SDK cleanup failed (code=17)");
        assertThatThrownBy(() -> HikvisionSdkRuntime.openForTesting(options, secondApi))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only one Hikvision SDK runtime may be open per process");

        firstApi.cleanupResult = true;
        first.close();
        HikvisionSdkRuntime reopened = HikvisionSdkRuntime.openForTesting(options, secondApi);
        reopened.close();
        assertThat(firstApi.cleanupCalls).isEqualTo(2);
    }

    private static final class FakeNativeApi implements HikvisionNativeApi {
        private final boolean initializeResult;
        private boolean cleanupResult;
        private final int errorCode;
        private int initializeCalls;
        private int cleanupCalls;

        private FakeNativeApi(boolean initializeResult, boolean cleanupResult, int errorCode) {
            this.initializeResult = initializeResult;
            this.cleanupResult = cleanupResult;
            this.errorCode = errorCode;
        }

        @Override
        public boolean initialize() {
            initializeCalls++;
            return initializeResult;
        }

        @Override
        public boolean cleanup() {
            cleanupCalls++;
            return cleanupResult;
        }

        @Override
        public int lastError() {
            return errorCode;
        }
    }
}
