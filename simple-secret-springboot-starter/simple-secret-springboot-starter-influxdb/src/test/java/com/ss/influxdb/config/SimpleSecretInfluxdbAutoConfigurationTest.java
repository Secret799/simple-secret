package com.ss.influxdb.config;

import com.ss.influxdb.client.InfluxClientFactory;
import com.ss.influxdb.client.InfluxManagementOperations;
import com.ss.influxdb.client.InfluxOperations;
import com.ss.influxdb.init.InfluxInitializer;
import com.ss.influxdb.mapping.InfluxMetadataRegistry;
import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleSecretInfluxdbAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretInfluxdbAutoConfiguration.class));

    @Test
    void shouldCreateNoBeansWhenDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(InfluxDB.class);
            assertThat(context).doesNotHaveBean(InfluxOperations.class);
            assertThat(context).doesNotHaveBean(InfluxdbProperties.class);
        });
    }

    @Test
    void shouldCreateConfiguredBeansWithoutEnablingBatchByDefaultAndCloseClient() {
        RecordingClient recording = new RecordingClient();
        runner.withBean(InfluxClientFactory.class, () -> properties -> recording.proxy())
                .withPropertyValues(
                        "simple-secret.influxdb.enabled=true",
                        "simple-secret.influxdb.url=http://localhost:8086",
                        "simple-secret.influxdb.database.name=metrics",
                        "simple-secret.influxdb.consistency=quorum")
                .run(context -> {
                    assertThat(context).hasSingleBean(InfluxDB.class);
                    assertThat(context).hasSingleBean(InfluxMetadataRegistry.class);
                    assertThat(context).hasSingleBean(InfluxOperations.class);
                    assertThat(context).hasSingleBean(InfluxInitializer.class);
                    assertThat(recording.consistency).isEqualTo(InfluxDB.ConsistencyLevel.QUORUM);
                    assertThat(recording.logLevel).isEqualTo(InfluxDB.LogLevel.NONE);
                    assertThat(recording.batchOptions).isNull();
                });
        assertThat(recording.closed).isTrue();
    }

    @Test
    void shouldBackOffForCustomClientWithoutRequiringConnectionProperties() {
        RecordingClient recording = new RecordingClient();
        InfluxDB customClient = recording.proxy();

        runner.withBean(InfluxDB.class, () -> customClient)
                .withPropertyValues(
                        "simple-secret.influxdb.enabled=true",
                        "simple-secret.influxdb.database.name=metrics")
                .run(context -> {
                    assertThat(context).hasSingleBean(InfluxDB.class);
                    assertThat(context.getBean(InfluxDB.class)).isSameAs(customClient);
                    assertThat(context).hasSingleBean(InfluxOperations.class);
                    assertThat(recording.logLevel).isNull();
                });
    }

    @Test
    void shouldApplyExplicitBatchSettings() {
        RecordingClient recording = new RecordingClient();
        runner.withBean(InfluxClientFactory.class, () -> properties -> recording.proxy())
                .withPropertyValues(
                        "simple-secret.influxdb.enabled=true",
                        "simple-secret.influxdb.url=http://localhost:8086",
                        "simple-secret.influxdb.database.name=metrics",
                        "simple-secret.influxdb.batch-write.enabled=true",
                        "simple-secret.influxdb.batch-write.actions=25",
                        "simple-secret.influxdb.batch-write.flush-duration-millis=750",
                        "simple-secret.influxdb.batch-write.consistency=all")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(recording.batchOptions).isNotNull();
                    assertThat(recording.batchOptions.getActions()).isEqualTo(25);
                    assertThat(recording.batchOptions.getFlushDuration()).isEqualTo(750);
                    assertThat(recording.batchOptions.getConsistency()).isEqualTo(InfluxDB.ConsistencyLevel.ALL);
                });
    }

    @Test
    void shouldInitializeDatabaseBeforeRetentionPolicyOnlyWhenExplicitlyEnabled() {
        RecordingClient recording = new RecordingClient();
        recording.results.add(new QueryResult());
        recording.results.add(new QueryResult());
        recording.results.add(new QueryResult());
        recording.results.add(new QueryResult());

        runner.withBean(InfluxClientFactory.class, () -> properties -> recording.proxy())
                .withPropertyValues(
                        "simple-secret.influxdb.enabled=true",
                        "simple-secret.influxdb.url=http://localhost:8086",
                        "simple-secret.influxdb.database.name=metrics",
                        "simple-secret.influxdb.database.auto-create=true",
                        "simple-secret.influxdb.retention-policy.name=archive",
                        "simple-secret.influxdb.retention-policy.duration=30d",
                        "simple-secret.influxdb.retention-policy.auto-create=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(recording.commands).containsExactly(
                            "SHOW DATABASES",
                            "CREATE DATABASE \"metrics\"",
                            "SHOW RETENTION POLICIES ON \"metrics\"",
                            "CREATE RETENTION POLICY \"archive\" ON \"metrics\" DURATION 30d REPLICATION 1");
                });
    }

    @Test
    void shouldCompleteInitializationBeforeBusinessBeanUsesOperations() {
        RecordingClient recording = new RecordingClient();
        recording.results.add(new QueryResult());
        recording.results.add(new QueryResult());
        recording.results.add(new QueryResult());
        recording.results.add(new QueryResult());
        recording.results.add(new QueryResult());

        runner.withBean(InfluxClientFactory.class, () -> properties -> recording.proxy())
                .withUserConfiguration(BusinessConsumerConfiguration.class)
                .withPropertyValues(
                        "simple-secret.influxdb.enabled=true",
                        "simple-secret.influxdb.url=http://localhost:8086",
                        "simple-secret.influxdb.database.name=metrics",
                        "simple-secret.influxdb.database.auto-create=true",
                        "simple-secret.influxdb.retention-policy.name=archive",
                        "simple-secret.influxdb.retention-policy.duration=30d",
                        "simple-secret.influxdb.retention-policy.auto-create=true")
                .run(context -> assertThat(recording.commands).containsExactly(
                        "SHOW DATABASES",
                        "CREATE DATABASE \"metrics\"",
                        "SHOW RETENTION POLICIES ON \"metrics\"",
                        "CREATE RETENTION POLICY \"archive\" ON \"metrics\" DURATION 30d REPLICATION 1",
                        "SELECT value FROM telemetry"));
    }

    @Test
    void shouldFailFastWhenDefaultFactoryConfigurationIsInvalid() {
        runner.withPropertyValues(
                        "simple-secret.influxdb.enabled=true",
                        "simple-secret.influxdb.database.name=metrics")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailFastWhenClientFactoryReturnsNull() {
        runner.withBean(InfluxClientFactory.class, () -> properties -> null)
                .withPropertyValues(
                        "simple-secret.influxdb.enabled=true",
                        "simple-secret.influxdb.url=http://localhost:8086",
                        "simple-secret.influxdb.database.name=metrics")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NullPointerException.class)
                            .hasStackTraceContaining("InfluxDB client factory must not return null");
                });
    }

    @Test
    void shouldUseCustomNamedInitializerWithoutDependingOnDefaultBeanName() {
        RecordingClient recording = new RecordingClient();

        runner.withBean(InfluxClientFactory.class, () -> properties -> recording.proxy())
                .withUserConfiguration(CustomInitializerConfiguration.class)
                .withPropertyValues(
                        "simple-secret.influxdb.enabled=true",
                        "simple-secret.influxdb.url=http://localhost:8086",
                        "simple-secret.influxdb.database.name=metrics")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(InfluxInitializer.class);
                    assertThat(context).hasSingleBean(InfluxOperations.class);
                });
    }

    private static final class RecordingClient {
        private final Deque<QueryResult> results = new ArrayDeque<>();
        private final List<String> commands = new ArrayList<>();
        private InfluxDB.ConsistencyLevel consistency;
        private InfluxDB.LogLevel logLevel;
        private BatchOptions batchOptions;
        private boolean closed;

        private InfluxDB proxy() {
            return (InfluxDB) Proxy.newProxyInstance(InfluxDB.class.getClassLoader(),
                    new Class<?>[]{InfluxDB.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "setConsistency" -> {
                            consistency = (InfluxDB.ConsistencyLevel) args[0];
                            yield proxy;
                        }
                        case "setLogLevel" -> {
                            logLevel = (InfluxDB.LogLevel) args[0];
                            yield proxy;
                        }
                        case "enableBatch" -> {
                            batchOptions = (BatchOptions) args[0];
                            yield proxy;
                        }
                        case "query" -> {
                            commands.add(((Query) args[0]).getCommand());
                            yield results.isEmpty() ? new QueryResult() : results.removeFirst();
                        }
                        case "close" -> {
                            closed = true;
                            yield null;
                        }
                        case "toString" -> "RecordingInfluxDB";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> method.getReturnType() == boolean.class ? false : null;
                    });
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BusinessConsumerConfiguration {
        @Bean
        Object businessConsumer(InfluxOperations operations) {
            operations.query("SELECT value FROM telemetry");
            return new Object();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomInitializerConfiguration {
        @Bean("customDatabaseBootstrap")
        InfluxInitializer customInitializer(InfluxManagementOperations management,
                                            InfluxdbProperties properties) {
            return new InfluxInitializer(management, properties);
        }
    }
}
