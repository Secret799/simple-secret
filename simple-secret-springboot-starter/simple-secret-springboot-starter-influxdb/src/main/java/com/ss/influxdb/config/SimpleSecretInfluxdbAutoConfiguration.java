package com.ss.influxdb.config;

import com.ss.influxdb.client.DefaultInfluxClientFactory;
import com.ss.influxdb.client.InfluxClientFactory;
import com.ss.influxdb.client.InfluxManagementOperations;
import com.ss.influxdb.client.InfluxOperations;
import com.ss.influxdb.init.InfluxInitializer;
import com.ss.influxdb.mapping.InfluxMetadataRegistry;
import com.ss.influxdb.mapping.InfluxPointMapper;
import com.ss.influxdb.mapping.InfluxResultMapper;
import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple Secret InfluxDB 1.x Spring Boot 自动配置。
 */
@AutoConfiguration
@EnableConfigurationProperties(InfluxdbProperties.class)
@ConditionalOnClass(InfluxDB.class)
@ConditionalOnProperty(prefix = "simple-secret.influxdb", name = "enabled", havingValue = "true")
public class SimpleSecretInfluxdbAutoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleSecretInfluxdbAutoConfiguration.class);

    /** 创建默认客户端工厂。 */
    @Bean
    @ConditionalOnMissingBean(InfluxClientFactory.class)
    InfluxClientFactory influxClientFactory() {
        return new DefaultInfluxClientFactory();
    }

    /** 创建并配置默认 InfluxDB 客户端。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(InfluxDB.class)
    InfluxDB influxDB(InfluxClientFactory factory, InfluxdbProperties properties) {
        properties.validate();
        InfluxDB client = Objects.requireNonNull(factory.create(properties),
                "InfluxDB client factory must not return null");
        try {
            client.setLogLevel(properties.getLogLevel());
            client.setConsistency(properties.getConsistency());
            if (properties.getBatchWrite().isEnabled()) {
                BatchOptions options = BatchOptions.DEFAULTS
                        .actions(properties.getBatchWrite().getActions())
                        .flushDuration(properties.getBatchWrite().getFlushDurationMillis())
                        .consistency(properties.getBatchWrite().getConsistency())
                        .threadFactory(new DaemonThreadFactory())
                        .exceptionHandler((points, failure) -> LOGGER.error(
                                "InfluxDB asynchronous batch write failed: {}",
                                failure == null ? "unknown" : failure.getClass().getSimpleName()));
                client.enableBatch(options);
            }
            return client;
        } catch (RuntimeException exception) {
            client.close();
            throw exception;
        }
    }

    /** 创建实体元数据注册表。 */
    @Bean
    @ConditionalOnMissingBean(InfluxMetadataRegistry.class)
    InfluxMetadataRegistry influxMetadataRegistry() {
        return new InfluxMetadataRegistry();
    }

    /** 创建 Point 映射器。 */
    @Bean
    @ConditionalOnMissingBean(InfluxPointMapper.class)
    InfluxPointMapper influxPointMapper(InfluxMetadataRegistry registry) {
        return new InfluxPointMapper(registry);
    }

    /** 创建查询结果映射器。 */
    @Bean
    @ConditionalOnMissingBean(InfluxResultMapper.class)
    InfluxResultMapper influxResultMapper(InfluxMetadataRegistry registry) {
        return new InfluxResultMapper(registry);
    }

    /**
     * 创建无默认数据库上下文的管理操作入口。
     *
     * @param client InfluxDB 客户端
     * @return 管理操作入口
     */
    @Bean
    @ConditionalOnMissingBean(InfluxManagementOperations.class)
    InfluxManagementOperations influxManagementOperations(InfluxDB client) {
        return new InfluxManagementOperations(client);
    }

    /**
     * 创建显式数据库初始化器。
     *
     * @param management 数据库管理操作入口
     * @param properties InfluxDB 配置
     * @return 数据库初始化器
     */
    @Bean
    @ConditionalOnMissingBean(InfluxInitializer.class)
    InfluxInitializer influxInitializer(InfluxManagementOperations management,
                                        InfluxdbProperties properties) {
        return new InfluxInitializer(management, properties);
    }

    /**
     * 创建同步客户端操作入口。
     *
     * @param client                InfluxDB 客户端
     * @param properties            InfluxDB 配置
     * @param registry              实体元数据注册表
     * @param pointMapper           Point 映射器
     * @param resultMapper          查询结果映射器
     * @param initializationBarrier 已完成回调的数据库初始化器
     * @return 同步客户端操作入口
     */
    @Bean
    @ConditionalOnMissingBean(InfluxOperations.class)
    InfluxOperations influxOperations(InfluxDB client, InfluxdbProperties properties,
                                      InfluxMetadataRegistry registry, InfluxPointMapper pointMapper,
                                      InfluxResultMapper resultMapper,
                                      InfluxInitializer initializationBarrier) {
        Objects.requireNonNull(initializationBarrier, "initializationBarrier");
        return new InfluxOperations(client, properties, registry, pointMapper, resultMapper);
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "simple-secret-influxdb-batch-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
