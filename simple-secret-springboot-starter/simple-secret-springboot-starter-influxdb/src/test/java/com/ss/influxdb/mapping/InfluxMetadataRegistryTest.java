package com.ss.influxdb.mapping;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Exclude;
import org.influxdb.annotation.Measurement;
import org.influxdb.annotation.TimeColumn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfluxMetadataRegistryTest {
    private final InfluxMetadataRegistry registry = new InfluxMetadataRegistry();

    @Test
    void shouldMapMeasurementInheritedFieldsAndGetterColumns() {
        InfluxEntityMetadata metadata = registry.metadata(Telemetry.class);

        assertThat(metadata.getMeasurementName()).isEqualTo("telemetry");
        assertThat(metadata.getDatabaseName()).isEqualTo("entity_db");
        assertThat(metadata.getRetentionPolicy()).isEqualTo("archive");
        assertThat(metadata.getFields()).extracting(InfluxFieldMetadata::getColumnName)
                .containsExactly("device_id", "time", "value");
        assertThat(metadata.getTimeField()).isPresent();
        assertThat(metadata.getTimeField().orElseThrow().getTimeUnit())
                .isEqualTo(TimeUnit.MILLISECONDS);
        assertThat(registry.column(Telemetry::getDeviceId)).isEqualTo("device_id");
        assertThat(registry.column(Telemetry::getValue)).isEqualTo("value");
        assertThat(registry.metadata(Telemetry.class)).isSameAs(metadata);
    }

    @Test
    void shouldUseClassNameAndIgnoreUnannotatedFields() {
        InfluxEntityMetadata metadata = registry.metadata(DefaultMeasurement.class);

        assertThat(metadata.getMeasurementName()).isEqualTo("DefaultMeasurement");
        assertThat(metadata.getDatabaseName()).isNull();
        assertThat(metadata.getRetentionPolicy()).isNull();
        assertThat(metadata.getFields()).extracting(InfluxFieldMetadata::getColumnName)
                .containsExactly("value");
    }

    @Test
    void shouldRejectDuplicateColumnsMultipleTimesAndUnsupportedFields() {
        assertThatThrownBy(() -> registry.metadata(DuplicateColumns.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate", "same");
        assertThatThrownBy(() -> registry.metadata(MultipleTimes.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("time");
        assertThatThrownBy(() -> registry.metadata(UnsupportedField.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported", "values");
        assertThatThrownBy(() -> registry.metadata(ArbitraryNumberField.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported", "value");
    }

    @Test
    void shouldHonorExcludeWhenMeasurementMapsAllFields() {
        InfluxEntityMetadata metadata = registry.metadata(AllFieldsMeasurement.class);

        assertThat(metadata.getFields()).extracting(InfluxFieldMetadata::getColumnName)
                .containsExactly("included");
    }

    static class DeviceBase {
        @Column(name = "device_id", tag = true)
        private String deviceId;

        public String getDeviceId() { return deviceId; }
    }

    @Measurement(name = "telemetry", database = "entity_db", retentionPolicy = "archive")
    static class Telemetry extends DeviceBase {
        @TimeColumn(timeUnit = TimeUnit.MILLISECONDS)
        private long time;
        @Column(name = "value")
        private Double value;
        private String ignored;

        public Double getValue() { return value; }
    }

    static class DefaultMeasurement {
        @Column
        private double value;
        private String ignored;
    }

    static class DuplicateColumns {
        @Column(name = "same") private double first;
        @Column(name = "same") private double second;
    }

    static class MultipleTimes {
        @TimeColumn private long first;
        @TimeColumn private long second;
        @Column private double value;
    }

    static class UnsupportedField {
        @Column private List<String> values;
    }

    static class ArbitraryNumberField {
        @Column private Number value;
    }

    @Measurement(name = "all_fields", allFields = true)
    static class AllFieldsMeasurement {
        private String included;
        @Exclude private String excluded;
    }
}
