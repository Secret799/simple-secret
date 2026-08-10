package com.ss.influxdb.service;

import com.ss.influxdb.client.InfluxOperations;
import com.ss.influxdb.config.InfluxdbProperties;
import com.ss.influxdb.domain.BaseInfluxModel;
import com.ss.influxdb.mapping.InfluxMetadataRegistry;
import com.ss.influxdb.mapping.InfluxPointMapper;
import com.ss.influxdb.mapping.InfluxResultMapper;
import org.influxdb.InfluxDB;
import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaseInfluxServiceTest {
    @Test
    void shouldExposeEntityMetadataAndDelegateWritesAndQueries() {
        List<BatchPoints> writes = new ArrayList<>();
        Deque<QueryResult> results = new ArrayDeque<>();
        results.add(result("device-a", 12.5));
        InfluxDB client = (InfluxDB) Proxy.newProxyInstance(InfluxDB.class.getClassLoader(),
                new Class<?>[]{InfluxDB.class}, (proxy, method, args) -> {
                    if (method.getName().equals("write") && args[0] instanceof BatchPoints points) {
                        writes.add(points);
                        return null;
                    }
                    if (method.getName().equals("query")) {
                        return results.removeFirst();
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
        InfluxdbProperties properties = new InfluxdbProperties();
        properties.getDatabase().setName("metrics");
        InfluxMetadataRegistry registry = new InfluxMetadataRegistry();
        InfluxOperations operations = new InfluxOperations(client, properties, registry,
                new InfluxPointMapper(registry), new InfluxResultMapper(registry));
        TelemetryService service = new TelemetryService(operations);

        service.save(new Telemetry("device-a", 12.5));
        List<Telemetry> records = service.list(service.wrapper().eq(Telemetry::getDeviceId, "device-a"));

        assertThat(service.getEntityType()).isEqualTo(Telemetry.class);
        assertThat(service.wrapper().build()).isEqualTo("SELECT * FROM \"telemetry\"");
        assertThat(writes).singleElement().satisfies(points -> {
            assertThat(points.getDatabase()).isEqualTo("metrics");
            assertThat(points.getPoints()).hasSize(1);
        });
        assertThat(records).extracting(Telemetry::getDeviceId).containsExactly("device-a");
    }

    @Test
    void shouldExposeBaseModelMetadataWithoutRequiringInheritanceForMapping() {
        Telemetry telemetry = new Telemetry();

        assertThat(telemetry.getMeasurementName()).isEqualTo("telemetry");
        assertThat(telemetry.getDatabaseName()).isNull();
        assertThat(telemetry.getRetentionPolicy()).isNull();
    }

    private static QueryResult result(String deviceId, double value) {
        QueryResult.Series series = new QueryResult.Series();
        series.setColumns(List.of("device_id", "value"));
        series.setValues(List.of(List.of(deviceId, value)));
        QueryResult.Result item = new QueryResult.Result();
        item.setSeries(List.of(series));
        QueryResult result = new QueryResult();
        result.setResults(List.of(item));
        return result;
    }

    private static final class TelemetryService extends BaseInfluxService<Telemetry> {
        private TelemetryService(InfluxOperations operations) {
            super(operations, Telemetry.class);
        }
    }

    @Measurement(name = "telemetry")
    static class Telemetry extends BaseInfluxModel {
        @Column(name = "device_id", tag = true) private String deviceId;
        @Column private Double value;

        Telemetry() {
        }

        Telemetry(String deviceId, Double value) {
            this.deviceId = deviceId;
            this.value = value;
        }

        String getDeviceId() {
            return deviceId;
        }
    }
}
