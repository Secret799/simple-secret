package com.ss.consumer.ics.dahua;

import com.ss.ics.dahua.DahuaCameraSdkService;
import com.ss.ics.dahua.DahuaThermalSubscription;
import com.ss.ics.dahua.domain.DahuaPoint;
import com.ss.ics.dahua.domain.DahuaRadiometryRecord;
import com.ss.ics.dahua.domain.DahuaRealPlaySession;
import com.ss.ics.dahua.domain.DahuaSdkOptions;
import com.ss.ics.dahua.domain.DahuaTemperatureSummary;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DahuaCameraSdkConsumerTest {

    @Test
    void exposesDahuaCameraSdkPublicTypesWithoutLoadingNativeLibraries() {
        DahuaSdkOptions options = DahuaSdkOptions.defaults(Path.of("/opt/dahua"));
        List<DahuaPoint> region = List.of(
                new DahuaPoint(1, 1), new DahuaPoint(2, 2), new DahuaPoint(3, 1));

        assertThat(options.libraryDirectory()).isEqualTo(Path.of("/opt/dahua"));
        assertThat(DahuaCameraSdkService.PRODUCT).isEqualTo("Dahua");
        assertThat(DahuaRealPlaySession.class).isNotNull();
        assertThat(DahuaThermalSubscription.class).isNotNull();
        assertThat(DahuaTemperatureSummary.class).isNotNull();
        assertThat(DahuaRadiometryRecord.class).isNotNull();
        assertThat(region).hasSize(3);
    }
}
