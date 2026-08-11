package com.ss.netty.config;

import com.ss.netty.auth.NettyWebSocketAuthenticator;
import com.ss.netty.server.NettyWebSocketEndpointRegistry;
import com.ss.netty.server.NettyWebSocketServer;
import com.ss.netty.session.NettyWebSocketChannelRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleSecretNettyWebSocketAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleSecretNettyWebSocketAutoConfiguration.class));

    @Test
    void shouldRemainDisabledUntilExplicitlyEnabled() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(NettyWebSocketServer.class);
            assertThat(context).doesNotHaveBean(NettyWebSocketChannelRegistry.class);
        });
    }

    @Test
    void shouldCreateAnonymousEndpointWithoutBindingWhenAutoStartupIsDisabled() {
        runner.withPropertyValues(
                        "simple-secret.netty.websocket.enabled=true",
                        "simple-secret.netty.websocket.auto-startup=false",
                        "simple-secret.netty.websocket.endpoints.events.path=/events",
                        "simple-secret.netty.websocket.endpoints.events.authentication-required=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(NettyWebSocketEndpointRegistry.class);
                    assertThat(context).hasSingleBean(NettyWebSocketChannelRegistry.class);
                    assertThat(context).hasSingleBean(NettyWebSocketServer.class);
                    assertThat(context.getBean(NettyWebSocketServer.class).isRunning()).isFalse();
                });
    }

    @Test
    void shouldFailProtectedEndpointWithoutAuthenticator() {
        runner.withPropertyValues(
                        "simple-secret.netty.websocket.enabled=true",
                        "simple-secret.netty.websocket.auto-startup=false",
                        "simple-secret.netty.websocket.endpoints.private.path=/private")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldUseConsumerAuthenticatorAndExecutor() {
        runner.withUserConfiguration(ConsumerOverrides.class)
                .withPropertyValues(
                        "simple-secret.netty.websocket.enabled=true",
                        "simple-secret.netty.websocket.auto-startup=false",
                        "simple-secret.netty.websocket.endpoints.private.path=/private")
                .run(context -> {
                    assertThat(context).hasSingleBean(NettyWebSocketAuthenticator.class);
                    assertThat(context).hasBean("nettyWebSocketHandlerExecutor");
                    assertThat(context.getBean("nettyWebSocketHandlerExecutor"))
                            .isSameAs(ConsumerOverrides.EXECUTOR);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerOverrides {

        private static final Executor EXECUTOR = Runnable::run;

        @Bean
        NettyWebSocketAuthenticator authenticator() {
            return request -> Optional.empty();
        }

        @Bean("nettyWebSocketHandlerExecutor")
        Executor nettyWebSocketHandlerExecutor() {
            return EXECUTOR;
        }
    }
}
