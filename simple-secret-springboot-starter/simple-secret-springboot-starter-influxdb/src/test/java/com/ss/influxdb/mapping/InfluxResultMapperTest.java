package com.ss.influxdb.mapping;

import com.ss.influxdb.exception.InfluxOperationException;
import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.influxdb.annotation.TimeColumn;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfluxResultMapperTest {
    private final InfluxResultMapper mapper = new InfluxResultMapper(new InfluxMetadataRegistry());

    @Test
    void shouldMapRowsGroupTagsNumbersAndIsoTime() {
        QueryResult result = result(series(
                List.of("time", "value", "unknown"),
                List.of(List.of("2026-08-09T12:30:00Z", 12.5D, "ignored")),
                Map.of("device_id", "device-1")));

        List<Telemetry> records = mapper.map(result, Telemetry.class);

        assertThat(records).hasSize(1);
        Telemetry record = records.get(0);
        assertThat(record.time).isEqualTo(LocalDateTime.of(2026, 8, 9, 12, 30));
        assertThat(record.deviceId).isEqualTo("device-1");
        assertThat(record.value).isEqualTo(12.5D);
    }

    @Test
    void shouldRejectServerErrorsAndDeclaredColumnConversionFailures() {
        QueryResult error = new QueryResult();
        error.setError("authorization failed");
        assertThatThrownBy(() -> mapper.map(error, Telemetry.class))
                .isInstanceOf(InfluxOperationException.class)
                .hasMessageContaining("query result")
                .hasMessageNotContaining("authorization failed");

        QueryResult invalid = result(series(
                List.of("value"), List.of(List.of("not-a-number")), Map.of()));
        assertThatThrownBy(() -> mapper.map(invalid, Telemetry.class))
                .isInstanceOf(InfluxOperationException.class)
                .hasMessageContaining("value");
    }

    @Test
    void shouldMapPrimitiveAndCharacterGroupTags() {
        QueryResult result = result(series(
                List.of("value"),
                List.of(List.of(12.5D)),
                Map.of("shard", "7", "grade", "A")));

        List<PrimitiveTagTelemetry> records = mapper.map(result, PrimitiveTagTelemetry.class);

        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.shard).isEqualTo(7);
            assertThat(record.grade).isEqualTo('A');
        });
    }

    @Test
    void shouldRejectInvalidBooleanAndMultiCharacterValues() {
        QueryResult invalidBoolean = result(series(
                List.of("enabled"), List.of(List.of("not-boolean")), Map.of()));
        QueryResult invalidCharacter = result(series(
                List.of("grade"), List.of(List.of("AB")), Map.of()));

        assertThatThrownBy(() -> mapper.map(invalidBoolean, StrictScalarTelemetry.class))
                .isInstanceOf(InfluxOperationException.class)
                .hasMessageContaining("enabled");
        assertThatThrownBy(() -> mapper.map(invalidCharacter, StrictScalarTelemetry.class))
                .isInstanceOf(InfluxOperationException.class)
                .hasMessageContaining("grade");
    }

    private static QueryResult result(QueryResult.Series series) {
        QueryResult.Result item = new QueryResult.Result();
        item.setSeries(List.of(series));
        QueryResult result = new QueryResult();
        result.setResults(List.of(item));
        return result;
    }

    private static QueryResult.Series series(List<String> columns, List<List<Object>> values,
                                             Map<String, String> tags) {
        QueryResult.Series series = new QueryResult.Series();
        series.setName("telemetry");
        series.setColumns(columns);
        series.setValues(values);
        series.setTags(tags);
        return series;
    }

    @Measurement(name = "telemetry")
    static class Telemetry {
        @TimeColumn LocalDateTime time;
        @Column(name = "device_id", tag = true) String deviceId;
        @Column Double value;
    }

    @Measurement(name = "telemetry")
    static class PrimitiveTagTelemetry {
        @Column(tag = true) int shard;
        @Column(tag = true) char grade;
        @Column Double value;
    }

    @Measurement(name = "telemetry")
    static class StrictScalarTelemetry {
        @Column Boolean enabled;
        @Column(tag = true) char grade;
    }
}
