package com.ss.nats.config;

import com.ss.nats.client.NatsClientManager;
import com.ss.nats.handler.NatsMessageHandler;
import com.ss.nats.lifecycle.NatsClientRefresher;
import com.ss.nats.lifecycle.NatsLifecycle;
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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple Secret NATS 多客户端 Spring Boot 自动配置。
 */
@AutoConfiguration
@EnableConfigurationProperties(NatsProperties.class)
@ConditionalOnClass(NatsClientManager.class)
@ConditionalOnProperty(prefix = "simple-secret.nats", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SimpleSecretNatsAutoConfiguration {

    /** 创建有界消息发布执行器。 */
    @Bean(name = "natsPublishExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "natsPublishExecutor")
    ExecutorService natsPublishExecutor(NatsProperties properties) {
        properties.validate();
        return boundedExecutor(properties.getPublishCoreSize(), properties.getPublishMaxSize(),
                properties.getPublishQueueCapacity(), "simple-secret-nats-publish-");
    }

    /** 创建有界异步消息处理执行器。 */
    @Bean(name = "natsHandlerExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "natsHandlerExecutor")
    ExecutorService natsHandlerExecutor(NatsProperties properties) {
        properties.validate();
        return boundedExecutor(properties.getHandlerCoreSize(), properties.getHandlerMaxSize(),
                properties.getHandlerQueueCapacity(), "simple-secret-nats-handler-");
    }

    /** 创建默认 NATS 多客户端管理器。 */
    @Bean
    @ConditionalOnMissingBean(NatsClientManager.class)
    NatsClientManager natsClientManager(
            @Qualifier("natsPublishExecutor") ExecutorService publishExecutor,
            @Qualifier("natsHandlerExecutor") ExecutorService handlerExecutor) {
        return new NatsClientManager(publishExecutor, handlerExecutor);
    }

    /** 创建配置和处理器订阅刷新器。 */
    @Bean
    @ConditionalOnMissingBean(NatsClientRefresher.class)
    NatsClientRefresher natsClientRefresher(
            NatsProperties properties,
            NatsClientManager clientManager,
            ObjectProvider<NatsMessageHandler> handlers) {
        List<NatsMessageHandler> handlerList = handlers.orderedStream().toList();
        return new NatsClientRefresher(properties, clientManager, handlerList);
    }

    /** 创建应用启动和容器关闭生命周期组件。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(NatsLifecycle.class)
    NatsLifecycle natsLifecycle(NatsClientRefresher refresher, NatsClientManager clientManager) {
        return new NatsLifecycle(refresher, clientManager);
    }

    private static ExecutorService boundedExecutor(int coreSize, int maxSize, int queueCapacity,
                                                   String threadNamePrefix) {
        return new ThreadPoolExecutor(coreSize, maxSize, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new DaemonThreadFactory(threadNamePrefix),
                new ThreadPoolExecutor.AbortPolicy());
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
