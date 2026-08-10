package com.ss.influxdb.mapping;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.influxdb.annotation.TimeColumn;
import org.influxdb.dto.Point;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfluxPointMapperTest {
    private final InfluxPointMapper mapper = new InfluxPointMapper(new InfluxMetadataRegistry());

    @Test
    void shouldMapTimeTagsFieldsAndSkipNulls() {
        Telemetry telemetry = new Telemetry();
        telemetry.time = 1234L;
        telemetry.deviceId = "device-1";
        telemetry.value = 12.5D;
        telemetry.note = null;

        Point point = mapper.toPoint(telemetry);
        String protocol = point.lineProtocol(TimeUnit.MILLISECONDS);

        assertThat(protocol).startsWith("telemetry,device_id=device-1 ");
        assertThat(protocol).contains("value=12.5");
        assertThat(protocol).doesNotContain("note=");
        assertThat(protocol).endsWith(" 1234");
    }

    @Test
    void shouldRejectEntityWithoutAnyNonNullField() {
        Telemetry telemetry = new Telemetry();
        telemetry.deviceId = "device-1";

        assertThatThrownBy(() -> mapper.toPoint(telemetry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
    }

    @Test
    void shouldRejectNonFiniteFieldsAndInvalidNumericTimes() {
        assertThatThrownBy(() -> mapper.toPoint(new NumericTelemetry(1D, Double.NaN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
        assertThatThrownBy(() -> mapper.toPoint(new NumericTelemetry(Double.POSITIVE_INFINITY, 1D)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("time");
        assertThatThrownBy(() -> mapper.toPoint(new NumericTelemetry(1.5D, 1D)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("time");
    }

    @Measurement(name = "telemetry")
    static class Telemetry {
        @TimeColumn(timeUnit = TimeUnit.MILLISECONDS) long time;
        @Column(name = "device_id", tag = true) String deviceId;
        @Column Double value;
        @Column String note;
    }

    @Measurement(name = "numeric_telemetry")
    static class NumericTelemetry {
        @TimeColumn(timeUnit = TimeUnit.MILLISECONDS) Double time;
        @Column Double value;

        NumericTelemetry(Double time, Double value) {
            this.time = time;
            this.value = value;
        }
    }
}
