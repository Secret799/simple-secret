package com.ss.nats.config;

import com.ss.nats.client.NatsClientManager;
import com.ss.nats.lifecycle.NatsClientRefresher;
import com.ss.nats.lifecycle.NatsLifecycle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleSecretNatsAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretNatsAutoConfiguration.class));

    @Test
    void shouldCreateDefaultBeansWithoutOpeningAnImplicitClient() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(NatsProperties.class);
            assertThat(context).hasSingleBean(NatsClientManager.class);
            assertThat(context).hasSingleBean(NatsClientRefresher.class);
            assertThat(context).hasSingleBean(NatsLifecycle.class);
            assertThat(context).hasBean("natsPublishExecutor");
            assertThat(context).hasBean("natsHandlerExecutor");
            assertThat(context.getBean(NatsClientManager.class).containsClient("default")).isFalse();
        });
    }

    @Test
    void shouldBeDisabledExplicitly() {
        runner.withPropertyValues("simple-secret.nats.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(NatsProperties.class);
                    assertThat(context).doesNotHaveBean(NatsClientManager.class);
                    assertThat(context).doesNotHaveBean("natsPublishExecutor");
                });
    }

    @Test
    void shouldBackOffForCustomManager() {
        runner.withUserConfiguration(UserBeans.class).run(context -> {
            assertThat(context).hasSingleBean(NatsClientManager.class);
            assertThat(context.getBean(NatsClientManager.class))
                    .isSameAs(context.getBean("customManager"));
        });
    }

    @Test
    void shouldBindConfiguredClientWithoutDefaultCredentials() {
        runner.withPropertyValues(
                        "simple-secret.nats.clients.edge.enabled=true",
                        "simple-secret.nats.clients.edge.url=nats://localhost:4222",
                        "simple-secret.nats.clients.edge.connection-name=edge-app",
                        "simple-secret.nats.clients.edge.reconnect-enabled=false",
                        "simple-secret.nats.clients.edge.publish-timeout-millis=2500")
                .run(context -> {
                    NatsProperties properties = context.getBean(NatsProperties.class);
                    NatsClientOptions edge = properties.getClients().get("edge");
                    assertThat(edge.isEnabled()).isTrue();
                    assertThat(edge.getUrl()).isEqualTo("nats://localhost:4222");
                    assertThat(edge.getConnectionName()).isEqualTo("edge-app");
                    assertThat(edge.isReconnectEnabled()).isFalse();
                    assertThat(edge.getPublishTimeoutMillis()).isEqualTo(2500L);
                    assertThat(edge.getUsername()).isNull();
                    assertThat(edge.getPassword()).isNull();
                });
    }

    @Test
    void saturatedPublishExecutorShouldRejectInsteadOfRunningOnCaller() {
        runner.withPropertyValues(
                        "simple-secret.nats.publish-core-size=1",
                        "simple-secret.nats.publish-max-size=1",
                        "simple-secret.nats.publish-queue-capacity=1")
                .run(context -> {
                    ThreadPoolExecutor executor = context.getBean(
                            "natsPublishExecutor", ThreadPoolExecutor.class);
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

    private static void awaitUnchecked(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserBeans {
        @Bean
        NatsClientManager customManager(
                @Qualifier("natsPublishExecutor") ExecutorService publishExecutor,
                @Qualifier("natsHandlerExecutor") ExecutorService handlerExecutor) {
            return new NatsClientManager(publishExecutor, handlerExecutor);
        }
    }
}
