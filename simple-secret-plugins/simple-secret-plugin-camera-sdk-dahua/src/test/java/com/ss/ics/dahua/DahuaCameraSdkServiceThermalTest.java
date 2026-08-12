package com.ss.ics.dahua;

import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.dahua.internal.model.DahuaNativeThermalData;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DahuaCameraSdkServiceThermalTest {

    @Test
    void subscribesFetchesAndClosesBeforeLogout() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        DahuaCameraSdkService service = service(nativeApi);
        List<DahuaThermalData> received = new ArrayList<>();

        DahuaThermalSubscription subscription = service.subscribeThermal(
                device().setChannel("1"), received::add);
        short[] grayscale = new short[]{1, 2};
        float[] temperatures = new float[]{10.5f, 11.5f};
        nativeApi.thermalCallback.onData(new DahuaNativeThermalData(
                LocalDateTime.of(2026, 1, 1, 0, 0), 2, 1, grayscale, temperatures));
        grayscale[0] = 9;
        temperatures[0] = 99.0f;

        assertThat(subscription.fetch()).isEqualTo(2);
        assertThat(received).singleElement().satisfies(data -> {
            assertThat(data.grayscale()).containsExactly((short) 1, (short) 2);
            assertThat(data.temperatures()).containsExactly(10.5f, 11.5f);
        });
        subscription.close();
        service.close();

        assertThat(nativeApi.events).containsExactly(
                "login", "thermal:attach:42:0", "thermal:fetch:42:0",
                "thermal:detach:77", "logout:42", "cleanup");
    }

    @Test
    void mapsPointItemAndRegionQueriesWithTemporaryLogins() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        DahuaCameraSdkService service = service(nativeApi);

        DahuaTemperatureSummary point = service.queryPointTemperature(device(), 100, 200);
        DahuaTemperatureSummary item = service.queryItemTemperature(device(), 1, 2, 3);
        DahuaRegionTemperature region = service.queryRegionTemperature(
                device(), List.of(new DahuaPoint(0, 0), new DahuaPoint(8192, 8192),
                        new DahuaPoint(0, 8192)));

        assertThat(point.average()).isEqualTo(20.5f);
        assertThat(item.meterType()).isEqualTo(1);
        assertThat(region.maximum()).isEqualTo(30.5);
        assertThat(nativeApi.events).containsExactly(
                "login", "thermal:point:42:0:100:200", "logout:42",
                "login", "thermal:item:42:0:1:2:3", "logout:42",
                "login", "thermal:region:42:0:3", "logout:42");
        service.close();
    }

    @Test
    void duplicateSubscriptionHandleInvalidatesNativeResourceAndBothLogins() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        DahuaCameraSdkService service = service(nativeApi);
        DahuaThermalSubscription first = service.subscribeThermal(device(), ignored -> { });

        try {
            assertThatThrownBy(() -> service.subscribeThermal(device(), ignored -> { }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Dahua SDK returned a duplicate radiometry subscription handle");
            assertThat(nativeApi.events).containsExactly(
                    "login", "thermal:attach:42:0",
                    "login", "thermal:attach:42:0",
                    "thermal:detach:77", "logout:42", "logout:42");
        } finally {
            first.close();
            service.close();
        }
        assertThat(nativeApi.events).endsWith("cleanup");
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
