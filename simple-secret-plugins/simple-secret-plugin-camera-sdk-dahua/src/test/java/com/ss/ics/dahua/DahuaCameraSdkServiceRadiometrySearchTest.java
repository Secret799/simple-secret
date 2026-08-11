package com.ss.ics.dahua;

import com.ss.ics.domain.DeviceDomain;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DahuaCameraSdkServiceRadiometrySearchTest {

    @Test
    void pagesResultsAndAlwaysStopsFinderBeforeLogout() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        DahuaCameraSdkService service = service(nativeApi, 10);

        List<DahuaRadiometryRecord> results = service.searchRadiometry(
                device(), 3, 5,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 2, 0, 0));

        assertThat(results).hasSize(3);
        assertThat(nativeApi.events).containsExactly(
                "login", "thermal:search:start:42:0",
                "thermal:search:page:88:0:3", "thermal:search:stop:88", "logout:42");
        service.close();
    }

    @Test
    void enforcesConfiguredResultLimitAndStillStopsFinder() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        nativeApi.searchTotalCount = 3;
        DahuaCameraSdkService service = service(nativeApi, 2);

        assertThatThrownBy(() -> service.searchRadiometry(
                device(), 3, 5,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 2, 0, 0)))
                .isInstanceOf(DahuaSdkException.class)
                .hasMessage("Dahua radiometry search exceeds result limit (code=0)");
        assertThat(nativeApi.events).containsExactly(
                "login", "thermal:search:start:42:0", "thermal:search:stop:88", "logout:42");
        service.close();
    }

    @Test
    void stopsValidFinderWhenSdkReturnsNegativeTotalCount() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        nativeApi.searchTotalCount = -1;
        DahuaCameraSdkService service = service(nativeApi, 10);

        try {
            assertThatThrownBy(() -> service.searchRadiometry(
                    device(), 3, 5,
                    LocalDateTime.of(2026, 1, 1, 0, 0),
                    LocalDateTime.of(2026, 1, 2, 0, 0)))
                    .isInstanceOf(DahuaSdkException.class)
                    .hasMessage("Dahua radiometry search start failed (code=0)");
            assertThat(nativeApi.events).containsExactly(
                    "login", "thermal:search:start:42:0",
                    "thermal:search:stop:88", "logout:42");
        } finally {
            service.close();
        }
    }

    private static DeviceDomain device() {
        return new DeviceDomain().setIp("192.0.2.10").setPort("37777")
                .setUsername("operator").setPassword("secret");
    }

    private static DahuaCameraSdkService service(FakeDahuaNativeApi nativeApi, int maxResults) {
        DahuaSdkOptions options = new DahuaSdkOptions(
                Path.of("sdk"), Duration.ofSeconds(3), Duration.ofSeconds(5), 256, maxResults);
        return DahuaCameraSdkService.createForTesting(
                DahuaSdkRuntime.openForTesting(options, nativeApi));
    }
}
