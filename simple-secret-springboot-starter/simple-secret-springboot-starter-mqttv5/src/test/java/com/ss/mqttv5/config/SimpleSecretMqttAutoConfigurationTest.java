package com.ss.mqttv5.config;

import com.ss.mqttv5.client.MqttClientManager;
import com.ss.mqttv5.waiter.DefaultMqttResponseWaiter;
import com.ss.mqttv5.waiter.MqttResponseWaiter;
import com.ss.mqttv5.lifecycle.MqttClientRefresher;
import com.ss.mqttv5.lifecycle.MqttConfigurationRefreshListener;
import com.ss.mqttv5.lifecycle.MqttLifecycle;
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
            assertThat(context).hasBean("mqttPublishExecutor");
            assertThat(context).hasBean("mqttHandlerExecutor");
            assertThat(context).hasBean("mqttConnectionExecutor");
            assertThat(context.getBean("mqttPublishExecutor"))
                    .isInstanceOf(ThreadPoolExecutor.class);
            assertThat(context.getBean(MqttClientManager.class).containsClient("default"))
                    .isFalse();
        });
    }

    @Test
    void canBeDisabled() {
        runner.withPropertyValues("simple-secret.mqtt.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MqttProperties.class);
                    assertThat(context).doesNotHaveBean(MqttClientManager.class);
                    assertThat(context).doesNotHaveBean("mqttPublishExecutor");
                });
    }

    @Test
    void saturatedPublishExecutorRejectsInsteadOfRunningOnCaller() {
        runner.withPropertyValues(
                        "simple-secret.mqtt.publish-core-size=1",
                        "simple-secret.mqtt.publish-max-size=1",
                        "simple-secret.mqtt.publish-queue-capacity=1")
                .run(context -> {
                    ThreadPoolExecutor executor = context.getBean(
                            "mqttPublishExecutor", ThreadPoolExecutor.class);
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
                        "simple-secret.mqtt.handler-core-size=1",
                        "simple-secret.mqtt.handler-max-size=1",
                        "simple-secret.mqtt.handler-queue-capacity=1")
                .run(context -> {
                    ThreadPoolExecutor executor = context.getBean(
                            "mqttHandlerExecutor", ThreadPoolExecutor.class);
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
                        "simple-secret.mqtt.clients.edge.enabled=true",
                        "simple-secret.mqtt.clients.edge.broker=tcp://localhost:1883",
                        "simple-secret.mqtt.clients.edge.client-id=edge-1",
                        "simple-secret.mqtt.clients.edge.clean-start=false",
                        "simple-secret.mqtt.clients.edge.keep-alive-seconds=45",
                        "simple-secret.mqtt.clients.edge.connection-timeout-seconds=12",
                        "simple-secret.mqtt.clients.edge.publish-timeout-seconds=13",
                        "simple-secret.mqtt.clients.edge.reconnect-enabled=false",
                        "simple-secret.mqtt.clients.edge.reconnect-delay-millis=2500",
                        "simple-secret.mqtt.clients.edge.persistence-directory=/tmp/mqtt-edge",
                        "simple-secret.mqtt.clients.edge.will.enabled=true",
                        "simple-secret.mqtt.clients.edge.will.topic=status/edge",
                        "simple-secret.mqtt.clients.edge.will.payload=offline",
                        "simple-secret.mqtt.clients.edge.will.qos=1",
                        "simple-secret.mqtt.clients.edge.will.retained=true")
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
                    assertThat(edge.isCleanStart()).isFalse();
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
                @Qualifier("mqttPublishExecutor") ExecutorService publishExecutor,
                @Qualifier("mqttHandlerExecutor") ExecutorService handlerExecutor,
                @Qualifier("mqttConnectionExecutor") ScheduledExecutorService connectionExecutor,
                MqttResponseWaiter responseWaiter) {
            return new MqttClientManager(
                    publishExecutor, handlerExecutor, connectionExecutor, responseWaiter);
        }
    }
}
