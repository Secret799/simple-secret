package com.ss.mqttv3.config;

import com.ss.mqttv3.client.MqttClientManager;
import com.ss.mqttv3.waiter.DefaultMqttResponseWaiter;
import com.ss.mqttv3.waiter.MqttResponseWaiter;
import com.ss.mqttv3.lifecycle.MqttClientRefresher;
import com.ss.mqttv3.lifecycle.MqttConfigurationRefreshListener;
import com.ss.mqttv3.lifecycle.MqttLifecycle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleSecretMqttAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretMqttAutoConfiguration.class));

    @Test
    void createsDefaultBeansWithoutOpeningAnImplicitClient() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MqttProperties.class);
            assertThat(context).hasSingleBean(MqttResponseWaiter.class);
            assertThat(context).hasSingleBean(MqttClientManager.class);
            assertThat(context).hasSingleBean(MqttClientRefresher.class);
            assertThat(context).hasSingleBean(MqttLifecycle.class);
            assertThat(context).hasSingleBean(MqttConfigurationRefreshListener.class);
            assertThat(context).hasBean("mqttv3PublishExecutor");
            assertThat(context).hasBean("mqttv3HandlerExecutor");
            assertThat(context).hasBean("mqttv3ConnectionExecutor");
            assertThat(context.getBean("mqttv3PublishExecutor"))
                    .isInstanceOf(ThreadPoolExecutor.class);
            assertThat(context.getBean(MqttClientManager.class).containsClient("default"))
                    .isFalse();
        });
    }

    @Test
    void canBeDisabled() {
        runner.withPropertyValues("simple-secret.mqttv3.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MqttProperties.class);
                    assertThat(context).doesNotHaveBean(MqttClientManager.class);
                    assertThat(context).doesNotHaveBean("mqttv3PublishExecutor");
                });
    }

    @Test
    void saturatedPublishExecutorRejectsInsteadOfRunningOnCaller() {
        runner.withPropertyValues(
                        "simple-secret.mqttv3.publish-core-size=1",
                        "simple-secret.mqttv3.publish-max-size=1",
                        "simple-secret.mqttv3.publish-queue-capacity=1")
                .run(context -> {
                    ThreadPoolExecutor executor = context.getBean(
                            "mqttv3PublishExecutor", ThreadPoolExecutor.class);
                    CountDownLatch workerStarted = new CountDownLatch(1);
                    CountDownLatch releaseWorker = new CountDownLatch(1);
                    try {
                        executor.execute(() -> {
                            workerStarted.countDown();
                            awaitUnchecked(releaseWorker);
                        });
                        assertThat(workerStarted.await(1, TimeUnit.SECONDS)).isTrue();
                        executor.execute(() -> awaitUnchecked(releaseWorker));

                        assertThatThrownBy(() -> executor.execute(() -> { }))
                                .isInstanceOf(RejectedExecutionException.class);
                    } finally {
                        releaseWorker.countDown();
                    }
                });
    }

    @Test
    void saturatedHandlerExecutorRejectsInsteadOfRunningOnCaller() {
        runner.withPropertyValues(
                        "simple-secret.mqttv3.handler-core-size=1",
                        "simple-secret.mqttv3.handler-max-size=1",
                        "simple-secret.mqttv3.handler-queue-capacity=1")
                .run(context -> {
                    ThreadPoolExecutor executor = context.getBean(
                            "mqttv3HandlerExecutor", ThreadPoolExecutor.class);
                    CountDownLatch workerStarted = new CountDownLatch(1);
                    CountDownLatch releaseWorker = new CountDownLatch(1);
                    try {
                        executor.execute(() -> {
                            workerStarted.countDown();
                            awaitUnchecked(releaseWorker);
                        });
                        assertThat(workerStarted.await(1, TimeUnit.SECONDS)).isTrue();
                        executor.execute(() -> awaitUnchecked(releaseWorker));

                        assertThatThrownBy(() -> executor.execute(() -> { }))
                                .isInstanceOf(RejectedExecutionException.class);
                    } finally {
                        releaseWorker.countDown();
                    }
                });
    }

    @Test
    void bindsDefaultsAndConfiguredClient() {
        runner.withPropertyValues(
                        "simple-secret.mqttv3.clients.edge.enabled=true",
                        "simple-secret.mqttv3.clients.edge.broker=tcp://localhost:1883",
                        "simple-secret.mqttv3.clients.edge.client-id=edge-1",
                        "simple-secret.mqttv3.clients.edge.clean-session=false",
                        "simple-secret.mqttv3.clients.edge.keep-alive-seconds=45",
                        "simple-secret.mqttv3.clients.edge.connection-timeout-seconds=12",
                        "simple-secret.mqttv3.clients.edge.publish-timeout-seconds=13",
                        "simple-secret.mqttv3.clients.edge.reconnect-enabled=false",
                        "simple-secret.mqttv3.clients.edge.reconnect-delay-millis=2500",
                        "simple-secret.mqttv3.clients.edge.persistence-directory=/tmp/mqtt-edge",
                        "simple-secret.mqttv3.clients.edge.will.enabled=true",
                        "simple-secret.mqttv3.clients.edge.will.topic=status/edge",
                        "simple-secret.mqttv3.clients.edge.will.payload=offline",
                        "simple-secret.mqttv3.clients.edge.will.qos=1",
                        "simple-secret.mqttv3.clients.edge.will.retained=true")
                .run(context -> {
                    MqttProperties properties = context.getBean(MqttProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getHandlerCoreSize()).isEqualTo(4);
                    assertThat(properties.getHandlerMaxSize()).isEqualTo(16);
                    assertThat(properties.getHandlerQueueCapacity()).isEqualTo(1024);
                    assertThat(properties.getPublishCoreSize()).isEqualTo(2);
                    assertThat(properties.getPublishMaxSize()).isEqualTo(8);
                    assertThat(properties.getPublishQueueCapacity()).isEqualTo(1024);
                    assertThat(properties.getConnectionCoreSize()).isEqualTo(2);

                    MqttClientOptions edge = properties.getClients().get("edge");
                    assertThat(edge.isEnabled()).isTrue();
                    assertThat(edge.getBroker()).isEqualTo("tcp://localhost:1883");
                    assertThat(edge.resolveClientId()).isEqualTo("edge-1");
                    assertThat(edge.isCleanSession()).isFalse();
                    assertThat(edge.getKeepAliveSeconds()).isEqualTo(45);
                    assertThat(edge.getConnectionTimeoutSeconds()).isEqualTo(12);
                    assertThat(edge.getPublishTimeoutSeconds()).isEqualTo(13);
                    assertThat(edge.isReconnectEnabled()).isFalse();
                    assertThat(edge.getReconnectDelayMillis()).isEqualTo(2500);
                    assertThat(edge.getPersistenceDirectory()).isEqualTo("/tmp/mqtt-edge");
                    assertThat(edge.getWill().isEnabled()).isTrue();
                    assertThat(edge.getWill().getTopic()).isEqualTo("status/edge");
                    assertThat(edge.getWill().getPayload()).isEqualTo("offline");
                    assertThat(edge.getWill().getQos()).isEqualTo(1);
                    assertThat(edge.getWill().isRetained()).isTrue();
                });
    }

    @Test
    void backsOffForCustomWaiterAndManager() {
        runner.withUserConfiguration(UserBeans.class).run(context -> {
            assertThat(context).hasSingleBean(MqttResponseWaiter.class);
            assertThat(context.getBean(MqttResponseWaiter.class))
                    .isSameAs(context.getBean("customWaiter"));
            assertThat(context).hasSingleBean(MqttClientManager.class);
            assertThat(context.getBean(MqttClientManager.class))
                    .isSameAs(context.getBean("customManager"));
        });
    }

    @Test
    void coexistsWithMqttV5AutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SimpleSecretMqttAutoConfiguration.class,
                        com.ss.mqttv5.config.SimpleSecretMqttAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MqttClientManager.class);
                    assertThat(context).hasSingleBean(
                            com.ss.mqttv5.client.MqttClientManager.class);
                    assertThat(context).hasBean("mqttv3PublishExecutor");
                    assertThat(context).hasBean("mqttv3HandlerExecutor");
                    assertThat(context).hasBean("mqttv3ConnectionExecutor");
                    assertThat(context).hasBean("mqttv3ResponseWaiter");
                    assertThat(context).hasBean("mqttv3ClientManager");
                    assertThat(context).hasBean("mqttv3ClientRefresher");
                    assertThat(context).hasBean("mqttv3Lifecycle");
                    assertThat(context).hasBean("mqttv3ConfigurationRefreshListener");
                    assertThat(context).hasBean("mqttPublishExecutor");
                });
    }

    private static void awaitUnchecked(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserBeans {
        @Bean
        MqttResponseWaiter customWaiter() {
            return new DefaultMqttResponseWaiter();
        }

        @Bean
        MqttClientManager customManager(
                @Qualifier("mqttv3PublishExecutor") ExecutorService publishExecutor,
                @Qualifier("mqttv3HandlerExecutor") ExecutorService handlerExecutor,
                @Qualifier("mqttv3ConnectionExecutor") ScheduledExecutorService connectionExecutor,
                MqttResponseWaiter responseWaiter) {
            return new MqttClientManager(
                    publishExecutor, handlerExecutor, connectionExecutor, responseWaiter);
        }
    }
}
