package com.ss.influxdb.client;

import com.ss.influxdb.config.InfluxdbProperties;
import com.ss.influxdb.domain.InfluxPage;
import com.ss.influxdb.exception.InfluxOperationException;
import com.ss.influxdb.mapping.InfluxMetadataRegistry;
import com.ss.influxdb.mapping.InfluxPointMapper;
import com.ss.influxdb.mapping.InfluxResultMapper;
import com.ss.influxdb.query.LambdaQueryWrapper;
import org.influxdb.InfluxDB;
import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfluxOperationsTest {
    @Test
    void shouldWriteSingleAndBatchPointsUsingEntityOverrides() {
        RecordingClient handler = new RecordingClient();
        InfluxOperations operations = operations(handler);

        operations.save(new Telemetry("device-a", 12.5));
        operations.saveBatch(List.of(
                new Telemetry("device-b", 13.5),
                new Telemetry("device-c", 14.5)));

        assertThat(handler.writes).hasSize(2);
        assertThat(handler.writes.get(0).getDatabase()).isEqualTo("entity_db");
        assertThat(handler.writes.get(0).getRetentionPolicy()).isEqualTo("entity_rp");
        assertThat(handler.writes.get(0).getConsistency()).isEqualTo(InfluxDB.ConsistencyLevel.QUORUM);
        assertThat(handler.writes.get(0).getPoints()).hasSize(1);
        assertThat(handler.writes.get(1).getPoints()).hasSize(2);
    }

    @Test
    void shouldQueueSinglePointWhenClientBatchIsEnabled() {
        RecordingClient handler = new RecordingClient();
        handler.batchEnabled = true;

        operations(handler).save(new Telemetry("device-a", 12.5));

        assertThat(handler.writes).isEmpty();
        assertThat(handler.queuedWrites).singleElement().satisfies(write -> {
            assertThat(write.database()).isEqualTo("entity_db");
            assertThat(write.retentionPolicy()).isEqualTo("entity_rp");
            assertThat(write.point()).isNotNull();
        });
    }

    @Test
    void shouldQueryAndMapUsingWrapperDatabase() {
        RecordingClient handler = new RecordingClient();
        handler.results.add(result(List.of(
                List.of("device-a", 12.5),
                List.of("device-b", 13.5))));
        InfluxOperations operations = operations(handler);

        List<Telemetry> records = operations.list(LambdaQueryWrapper.of(Telemetry.class,
                new InfluxMetadataRegistry()).orderByTimeDesc());

        assertThat(records).extracting(Telemetry::getDeviceId)
                .containsExactly("device-a", "device-b");
        assertThat(handler.queries).singleElement().satisfies(query -> {
            assertThat(query.getDatabase()).isEqualTo("entity_db");
            assertThat(query.getCommand()).isEqualTo(
                    "SELECT * FROM \"entity_rp\".\"telemetry\" ORDER BY time DESC");
        });
    }

    @Test
    void shouldRejectServerErrorsAndMultipleRowsWithoutLeakingServerMessage() {
        RecordingClient errorHandler = new RecordingClient();
        QueryResult error = new QueryResult();
        error.setError("password=do-not-leak");
        errorHandler.results.add(error);
        InfluxOperations errorOperations = operations(errorHandler);

        assertThatThrownBy(() -> errorOperations.query("SELECT * FROM telemetry"))
                .isInstanceOf(InfluxOperationException.class)
                .hasMessageNotContaining("do-not-leak");

        RecordingClient multipleHandler = new RecordingClient();
        multipleHandler.results.add(result(List.of(
                List.of("device-a", 12.5),
                List.of("device-b", 13.5))));

        assertThatThrownBy(() -> operations(multipleHandler).one(
                LambdaQueryWrapper.of(Telemetry.class, new InfluxMetadataRegistry())))
                .isInstanceOf(InfluxOperationException.class)
                .hasMessageContaining("more than one");
    }

    @Test
    void shouldManageDatabaseAndRetentionPolicyWithValidatedNames() {
        RecordingClient handler = new RecordingClient();
        handler.results.add(databases("metrics", "logs"));
        handler.results.add(retentionPolicies("autogen", "archive"));
        handler.results.add(new QueryResult());
        handler.results.add(new QueryResult());
        InfluxOperations operations = operations(handler);

        assertThat(operations.databaseExists("metrics")).isTrue();
        assertThat(operations.retentionPolicyExists("metrics", "archive")).isTrue();
        operations.createDatabase("new_metrics");
        operations.createRetentionPolicy("metrics", "cold", "30d", 2, false);

        assertThat(handler.queries).extracting(Query::getCommand).containsExactly(
                "SHOW DATABASES",
                "SHOW RETENTION POLICIES ON \"metrics\"",
                "CREATE DATABASE \"new_metrics\"",
                "CREATE RETENTION POLICY \"cold\" ON \"metrics\" DURATION 30d REPLICATION 2");
        assertThat(handler.queries).extracting(Query::getDatabase)
                .containsOnlyNulls();
        assertThatThrownBy(() -> operations.createDatabase("metrics\nDROP DATABASE x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operations.createRetentionPolicy("metrics", "cold", "30d DEFAULT", 1, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldWrapClientManagementFailuresWithSafeContext() {
        RecordingClient handler = new RecordingClient();
        handler.queryFailure = new IllegalStateException("credential=do-not-leak");

        assertThatThrownBy(() -> operations(handler)
                .createRetentionPolicy("metrics", "cold", "30d", 1, true))
                .isInstanceOf(InfluxOperationException.class)
                .hasMessageNotContaining("do-not-leak")
                .satisfies(exception -> assertThat(causeMessages(exception))
                        .doesNotContain("credential=do-not-leak"));
    }

    @Test
    void shouldNotLeakPointFromClientWriteFailureCause() {
        RecordingClient handler = new RecordingClient();
        handler.writeFailure = new IllegalStateException("line=telemetry secret-field=private");

        assertThatThrownBy(() -> operations(handler).save(new Telemetry("device-a", 12.5)))
                .isInstanceOf(InfluxOperationException.class)
                .satisfies(exception -> assertThat(causeMessages(exception))
                        .doesNotContain("secret-field=private"));
    }

    @Test
    void shouldReturnImmutablePageUsingExplicitCountField() {
        RecordingClient handler = new RecordingClient();
        handler.results.add(countResult(2, 1));
        handler.results.add(result(List.of(List.of("device-c", 14.5))));
        InfluxOperations operations = operations(handler);

        InfluxPage<Telemetry> page = operations.page(
                LambdaQueryWrapper.of(Telemetry.class, new InfluxMetadataRegistry())
                        .eq(Telemetry::getDeviceId, "active"),
                Telemetry::getValue, 2, 2);

        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getCurrent()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getRecords()).extracting(Telemetry::getDeviceId).containsExactly("device-c");
        assertThatThrownBy(() -> page.getRecords().add(new Telemetry()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(handler.queries).extracting(Query::getCommand).containsExactly(
                "SELECT count(\"value\") AS \"ss_total\" FROM \"entity_rp\".\"telemetry\" "
                        + "WHERE \"device_id\" = 'active'",
                "SELECT * FROM \"entity_rp\".\"telemetry\" WHERE \"device_id\" = 'active' LIMIT 2 OFFSET 2");
    }

    @Test
    void shouldSkipDataQueryWhenPageTotalIsZero() {
        RecordingClient handler = new RecordingClient();
        handler.results.add(countResult(0));

        InfluxPage<Telemetry> page = operations(handler).page(
                LambdaQueryWrapper.of(Telemetry.class, new InfluxMetadataRegistry()),
                Telemetry::getValue, 1, 20);

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
        assertThat(handler.queries).extracting(Query::getCommand)
                .containsExactly("SELECT count(\"value\") AS \"ss_total\" FROM \"entity_rp\".\"telemetry\"");
    }

    private static InfluxOperations operations(RecordingClient handler) {
        InfluxdbProperties properties = new InfluxdbProperties();
        properties.getDatabase().setName("metrics");
        properties.getRetentionPolicy().setName("default_rp");
        properties.setConsistency(InfluxDB.ConsistencyLevel.QUORUM);
        InfluxMetadataRegistry registry = new InfluxMetadataRegistry();
        return new InfluxOperations(handler.proxy(), properties, registry,
                new InfluxPointMapper(registry), new InfluxResultMapper(registry));
    }

    private static QueryResult result(List<List<Object>> rows) {
        QueryResult.Series series = new QueryResult.Series();
        series.setName("telemetry");
        series.setColumns(List.of("device_id", "value"));
        series.setValues(rows);
        QueryResult.Result item = new QueryResult.Result();
        item.setSeries(List.of(series));
        QueryResult result = new QueryResult();
        result.setResults(List.of(item));
        return result;
    }

    private static QueryResult retentionPolicies(String... names) {
        QueryResult.Series series = new QueryResult.Series();
        series.setName("metrics");
        series.setColumns(List.of("name", "duration"));
        List<List<Object>> values = new ArrayList<>();
        for (String name : names) {
            values.add(List.of(name, "0s"));
        }
        series.setValues(values);
        QueryResult.Result item = new QueryResult.Result();
        item.setSeries(List.of(series));
        QueryResult result = new QueryResult();
        result.setResults(List.of(item));
        return result;
    }

    private static QueryResult databases(String... names) {
        QueryResult.Series series = new QueryResult.Series();
        series.setName("databases");
        series.setColumns(List.of("name"));
        List<List<Object>> values = new ArrayList<>();
        for (String name : names) {
            values.add(List.of(name));
        }
        series.setValues(values);
        QueryResult.Result item = new QueryResult.Result();
        item.setSeries(List.of(series));
        QueryResult result = new QueryResult();
        result.setResults(List.of(item));
        return result;
    }

    private static QueryResult countResult(long... counts) {
        QueryResult.Series series = new QueryResult.Series();
        series.setName("telemetry");
        series.setColumns(List.of("time", "ss_total"));
        List<List<Object>> values = new ArrayList<>();
        for (long count : counts) {
            values.add(List.of("1970-01-01T00:00:00Z", (double) count));
        }
        series.setValues(values);
        QueryResult.Result item = new QueryResult.Result();
        item.setSeries(List.of(series));
        QueryResult result = new QueryResult();
        result.setResults(List.of(item));
        return result;
    }

    private static String causeMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            messages.append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return messages.toString();
    }

    @Measurement(name = "telemetry", database = "entity_db", retentionPolicy = "entity_rp")
    static class Telemetry {
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

        Double getValue() {
            return value;
        }
    }

    private static final class RecordingClient implements InvocationHandler {
        private final List<BatchPoints> writes = new ArrayList<>();
        private final List<QueuedWrite> queuedWrites = new ArrayList<>();
        private final List<Query> queries = new ArrayList<>();
        private final Deque<QueryResult> results = new ArrayDeque<>();
        private RuntimeException queryFailure;
        private RuntimeException writeFailure;
        private boolean batchEnabled;

        private InfluxDB proxy() {
            return (InfluxDB) Proxy.newProxyInstance(InfluxDB.class.getClassLoader(),
                    new Class<?>[]{InfluxDB.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "write" -> {
                    if (writeFailure != null) {
                        throw writeFailure;
                    }
                    if (args != null && args.length == 1 && args[0] instanceof BatchPoints points) {
                        writes.add(points);
                    } else if (args != null && args.length == 3 && args[2] instanceof Point point) {
                        queuedWrites.add(new QueuedWrite((String) args[0], (String) args[1], point));
                    }
                    yield null;
                }
                case "isBatchEnabled" -> batchEnabled;
                case "query" -> {
                    if (queryFailure != null) {
                        throw queryFailure;
                    }
                    queries.add((Query) args[0]);
                    yield results.isEmpty() ? new QueryResult() : results.removeFirst();
                }
                case "toString" -> "RecordingInfluxDB";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            };
        }

        private record QueuedWrite(String database, String retentionPolicy, Point point) {
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0F;
            if (type == double.class) return 0D;
            if (type == char.class) return '\0';
            return null;
        }
    }
}
