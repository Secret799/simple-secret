package com.ss.ics.dahua;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DahuaSdkRuntimeTest {

    @Test
    void initializesAndCleansUpExactlyOnce() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();

        DahuaSdkRuntime runtime = DahuaSdkRuntime.openForTesting(
                DahuaSdkOptions.defaults(Path.of("sdk")), nativeApi);
        runtime.close();
        runtime.close();

        assertThat(nativeApi.initializeCalls).isEqualTo(1);
        assertThat(nativeApi.cleanupCalls).isEqualTo(1);
    }

    @Test
    void reportsInitializationFailureWithoutLibraryPath() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        nativeApi.initializeResult = false;
        nativeApi.errorCode = 17;

        assertThatThrownBy(() -> DahuaSdkRuntime.openForTesting(
                DahuaSdkOptions.defaults(Path.of("secret-sdk-path")), nativeApi))
                .isInstanceOf(DahuaSdkException.class)
                .hasMessage("Dahua SDK initialization failed (code=17)")
                .hasMessageNotContaining("secret-sdk-path");
    }

    @Test
    void rejectsConcurrentRuntimeAndAllowsOpeningAfterClose() {
        DahuaSdkOptions options = DahuaSdkOptions.defaults(Path.of("sdk"));
        DahuaSdkRuntime first = DahuaSdkRuntime.openForTesting(options, new FakeDahuaNativeApi());

        assertThatThrownBy(() -> DahuaSdkRuntime.openForTesting(options, new FakeDahuaNativeApi()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only one Dahua SDK runtime may be open per process");

        first.close();
        DahuaSdkRuntime reopened = DahuaSdkRuntime.openForTesting(options, new FakeDahuaNativeApi());
        reopened.close();
    }

    @Test
    void cleanupFailureKeepsProcessGateAndCanBeRetried() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        nativeApi.cleanupResult = false;
        DahuaSdkOptions options = DahuaSdkOptions.defaults(Path.of("sdk"));
        DahuaSdkRuntime runtime = DahuaSdkRuntime.openForTesting(options, nativeApi);

        assertThatThrownBy(runtime::close)
                .isInstanceOf(DahuaSdkException.class)
                .hasMessage("Dahua SDK cleanup failed (code=0)");
        assertThatThrownBy(() -> DahuaSdkRuntime.openForTesting(options, new FakeDahuaNativeApi()))
                .isInstanceOf(IllegalStateException.class);

        nativeApi.cleanupResult = true;
        runtime.close();
        DahuaSdkRuntime reopened = DahuaSdkRuntime.openForTesting(options, new FakeDahuaNativeApi());
        reopened.close();
        assertThat(nativeApi.cleanupCalls).isEqualTo(2);
    }
}
