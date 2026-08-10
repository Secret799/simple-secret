package com.ss.influxdb.query;

import com.ss.influxdb.mapping.InfluxMetadataRegistry;
import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LambdaQueryWrapperTest {
    private final InfluxMetadataRegistry registry = new InfluxMetadataRegistry();

    @Test
    void shouldBuildEscapedSelectConditionsGroupingAndOrder() {
        String query = LambdaQueryWrapper.of(Telemetry.class, registry)
                .select(Telemetry::getDeviceId, Telemetry::getValue)
                .eq(Telemetry::getDeviceId, "a'b\\c")
                .between(Telemetry::getValue, 10, 20)
                .in(Telemetry::getDeviceId, List.of("a", "b"))
                .groupBy(Telemetry::getDeviceId)
                .groupByTime("5m")
                .orderByTimeDesc()
                .build();

        assertThat(query).isEqualTo("SELECT \"device_id\", \"value\" FROM \"archive\".\"telemetry\" "
                + "WHERE \"device_id\" = 'a\\'b\\\\c' AND (\"value\" >= 10 AND \"value\" <= 20) "
                + "AND (\"device_id\" = 'a' OR \"device_id\" = 'b') "
                + "GROUP BY \"device_id\", time(5m) ORDER BY time DESC");
    }

    @Test
    void shouldBuildAggregateNestedConditionsAndTimeLiteral() {
        String query = LambdaQueryWrapper.of(Telemetry.class, registry)
                .function("last", Telemetry::getValue, "latest_value")
                .and(nested -> nested.eq(Telemetry::getDeviceId, "a")
                        .or(inner -> inner.gt(Telemetry::getValue, 5)
                                .lt(Telemetry::getValue, 10)))
                .ge(Telemetry::getCreatedAt, Instant.parse("2026-08-09T00:00:00Z"))
                .limit(1)
                .build();

        assertThat(query).contains("last(\"value\") AS \"latest_value\"")
                .contains("(\"device_id\" = 'a' OR (\"value\" > 5 AND \"value\" < 10))")
                .contains("\"created_at\" >= '2026-08-09T00:00:00Z'")
                .endsWith("LIMIT 1");
    }

    @Test
    void shouldBuildRemainingComparisonsDynamicMeasurementAndAscendingOrder() {
        String query = LambdaQueryWrapper.of(Telemetry.class, registry)
                .measurement("telemetry_2026")
                .retentionPolicy("cold_archive")
                .ne(Telemetry::getDeviceId, "offline")
                .le(Telemetry::getValue, 99.5)
                .notIn(Telemetry::getDeviceId, List.of("retired", "deleted"))
                .lt(Telemetry::getCreatedAt, LocalDateTime.of(2026, 8, 10, 0, 0))
                .orderByTimeAsc()
                .limit(20)
                .offset(5)
                .build();

        assertThat(query).isEqualTo("SELECT * FROM \"cold_archive\".\"telemetry_2026\" "
                + "WHERE \"device_id\" != 'offline' AND \"value\" <= 99.5 "
                + "AND (\"device_id\" != 'retired' AND \"device_id\" != 'deleted') "
                + "AND \"created_at\" < '2026-08-10T00:00:00Z' "
                + "ORDER BY time ASC LIMIT 20 OFFSET 5");
    }

    @Test
    void shouldRejectEmptyCollectionsNullValuesAndInvalidDynamicIdentifiers() {
        assertThatThrownBy(() -> LambdaQueryWrapper.of(Telemetry.class, registry)
                .in(Telemetry::getDeviceId, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LambdaQueryWrapper.of(Telemetry.class, registry)
                .eq(Telemetry::getDeviceId, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LambdaQueryWrapper.of(Telemetry.class, registry)
                .measurement("telemetry\nDROP DATABASE x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LambdaQueryWrapper.of(Telemetry.class, registry)
                .retentionPolicy(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidFunctionsDurationsAndPaging() {
        assertThatThrownBy(() -> LambdaQueryWrapper.of(Telemetry.class, registry)
                .function("last); drop database x; --", Telemetry::getValue, "value"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LambdaQueryWrapper.of(Telemetry.class, registry)
                .groupByTime("5m fill(none)"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LambdaQueryWrapper.of(Telemetry.class, registry).page(0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LambdaQueryWrapper.of(Telemetry.class, registry).limit(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LambdaQueryWrapper.of(Telemetry.class, registry).offset(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LambdaQueryWrapper.of(Telemetry.class, registry)
                .eq(Telemetry::getValue, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LambdaQueryWrapper.of(Telemetry.class, registry)
                .eq(Telemetry::getValue, new MaliciousNumber()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldBuildCountWithoutMutatingDataQuery() {
        LambdaQueryWrapper<Telemetry> wrapper = LambdaQueryWrapper.of(Telemetry.class, registry)
                .eq(Telemetry::getDeviceId, "a")
                .orderByTimeDesc()
                .page(2, 25);

        assertThat(wrapper.buildCount(Telemetry::getValue)).isEqualTo(
                "SELECT count(\"value\") AS \"ss_total\" FROM \"archive\".\"telemetry\" "
                        + "WHERE \"device_id\" = 'a'");
        assertThat(wrapper.build()).endsWith("ORDER BY time DESC LIMIT 25 OFFSET 25");
        assertThat(wrapper.copy().page(1, 10).build()).endsWith("ORDER BY time DESC LIMIT 10 OFFSET 0");
        assertThat(wrapper.build()).endsWith("ORDER BY time DESC LIMIT 25 OFFSET 25");
    }

    @Test
    void shouldRejectTagCountAndTagGroupedPaging() {
        assertThatThrownBy(() -> LambdaQueryWrapper.of(Telemetry.class, registry)
                .buildCount(Telemetry::getDeviceId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
        assertThatThrownBy(() -> LambdaQueryWrapper.of(Telemetry.class, registry)
                .groupBy(Telemetry::getDeviceId)
                .page(1, 20)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("group");
    }

    @Test
    void shouldResolveInheritedGetterWhenSubclassMapsAllFields() {
        String query = LambdaQueryWrapper.of(AllFieldsTelemetry.class, registry)
                .eq(AllFieldsTelemetry::getInheritedValue, "active")
                .build();

        assertThat(query).isEqualTo(
                "SELECT * FROM \"AllFieldsTelemetry\" WHERE \"inheritedValue\" = 'active'");
    }

    @Measurement(name = "telemetry", retentionPolicy = "archive")
    static class Telemetry {
        @Column(name = "device_id", tag = true) private String deviceId;
        @Column private Double value;
        @Column(name = "created_at") private String createdAt;
        String getDeviceId() { return deviceId; }
        Double getValue() { return value; }
        String getCreatedAt() { return createdAt; }
    }

    static class AllFieldsBase {
        private String inheritedValue;

        String getInheritedValue() {
            return inheritedValue;
        }
    }

    @Measurement(name = "AllFieldsTelemetry", allFields = true)
    static class AllFieldsTelemetry extends AllFieldsBase {
    }

    static final class MaliciousNumber extends Number {
        @Override
        public int intValue() {
            return 0;
        }

        @Override
        public long longValue() {
            return 0L;
        }

        @Override
        public float floatValue() {
            return 0F;
        }

        @Override
        public double doubleValue() {
            return 0D;
        }

        @Override
        public String toString() {
            return "0 OR true";
        }
    }
}
