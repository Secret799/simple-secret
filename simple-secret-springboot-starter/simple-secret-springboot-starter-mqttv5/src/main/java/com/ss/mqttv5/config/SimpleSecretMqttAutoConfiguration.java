package com.ss.mqttv5.config;

import com.ss.mqttv5.client.MqttClientManager;
import com.ss.mqttv5.handler.MqttMessageHandler;
import com.ss.mqttv5.waiter.DefaultMqttResponseWaiter;
import com.ss.mqttv5.waiter.MqttResponseWaiter;
import com.ss.mqttv5.lifecycle.MqttClientRefresher;
import com.ss.mqttv5.lifecycle.MqttConfigurationRefreshListener;
import com.ss.mqttv5.lifecycle.MqttLifecycle;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple Secret MQTT v5 的 Spring Boot 自动配置。
 */
@AutoConfiguration
@EnableConfigurationProperties(MqttProperties.class)
@ConditionalOnClass(MqttClientManager.class)
@ConditionalOnProperty(prefix = "simple-secret.mqtt", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SimpleSecretMqttAutoConfiguration {
    /**
     * 创建消息发布线程池。
     *
     * @param properties MQTT 属性
     * @return 发布执行器
     */
    @Bean(name = "mqttPublishExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "mqttPublishExecutor")
    ExecutorService mqttPublishExecutor(MqttProperties properties) {
        return boundedExecutor(properties.getPublishCoreSize(), properties.getPublishMaxSize(),
                properties.getPublishQueueCapacity(), "simple-secret-mqtt-publish-",
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 创建消息处理线程池。
     *
     * @param properties MQTT 属性
     * @return 消息处理执行器
     */
    @Bean(name = "mqttHandlerExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "mqttHandlerExecutor")
    ExecutorService mqttHandlerExecutor(MqttProperties properties) {
        return boundedExecutor(properties.getHandlerCoreSize(), properties.getHandlerMaxSize(),
                properties.getHandlerQueueCapacity(), "simple-secret-mqtt-handler-",
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 创建连接和重连调度线程池。
     *
     * @param properties MQTT 属性
     * @return 连接调度执行器
     */
    @Bean(name = "mqttConnectionExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "mqttConnectionExecutor")
    ScheduledExecutorService mqttConnectionExecutor(MqttProperties properties) {
        return new ScheduledThreadPoolExecutor(properties.getConnectionCoreSize(),
                new DaemonThreadFactory("simple-secret-mqtt-connection-"),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 创建默认响应等待器。
     *
     * @return 默认响应等待器
     */
    @Bean
    @ConditionalOnMissingBean(MqttResponseWaiter.class)
    MqttResponseWaiter mqttResponseWaiter() {
        return new DefaultMqttResponseWaiter();
    }

    /**
     * 创建 MQTT 客户端管理器。
     *
     * @param publishExecutor    发布执行器
     * @param handlerExecutor    消息处理执行器
     * @param connectionExecutor 连接调度执行器
     * @param responseWaiter     响应等待器
     * @return MQTT 客户端管理器
     */
    @Bean
    @ConditionalOnMissingBean(MqttClientManager.class)
    MqttClientManager mqttClientManager(
            @Qualifier("mqttPublishExecutor") ExecutorService publishExecutor,
            @Qualifier("mqttHandlerExecutor") ExecutorService handlerExecutor,
            @Qualifier("mqttConnectionExecutor") ScheduledExecutorService connectionExecutor,
            MqttResponseWaiter responseWaiter) {
        return new MqttClientManager(
                publishExecutor, handlerExecutor, connectionExecutor, responseWaiter);
    }

    /**
     * 创建客户端刷新协调器。
     *
     * @param properties    MQTT 属性
     * @param clientManager 客户端管理器
     * @param handlers      可选消息处理器
     * @return 客户端刷新协调器
     */
    @Bean
    @ConditionalOnMissingBean(MqttClientRefresher.class)
    MqttClientRefresher mqttClientRefresher(
            MqttProperties properties,
            MqttClientManager clientManager,
            ObjectProvider<MqttMessageHandler> handlers) {
        List<MqttMessageHandler> handlerList = handlers.orderedStream().toList();
        return new MqttClientRefresher(properties, clientManager, handlerList);
    }

    /**
     * 创建启动和关闭生命周期组件。
     *
     * @param refresher     客户端刷新协调器
     * @param clientManager 客户端管理器
     * @return MQTT 生命周期组件
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(MqttLifecycle.class)
    MqttLifecycle mqttLifecycle(
            MqttClientRefresher refresher, MqttClientManager clientManager) {
        return new MqttLifecycle(refresher, clientManager);
    }

    /**
     * 创建可选 Spring Cloud 配置刷新监听器。
     *
     * @param refresher 客户端刷新协调器
     * @return 配置刷新监听器
     */
    @Bean
    @ConditionalOnMissingBean(MqttConfigurationRefreshListener.class)
    MqttConfigurationRefreshListener mqttConfigurationRefreshListener(
            MqttClientRefresher refresher) {
        return new MqttConfigurationRefreshListener(refresher);
    }

    private static ExecutorService boundedExecutor(
            int coreSize, int maxSize, int queueCapacity, String namePrefix,
            RejectedExecutionHandler rejectionHandler) {
        return new ThreadPoolExecutor(coreSize, maxSize, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new DaemonThreadFactory(namePrefix),
                rejectionHandler);
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger sequence = new AtomicInteger();

        private DaemonThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, namePrefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
