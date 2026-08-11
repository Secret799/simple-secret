package com.ss.websocket.config;

import com.ss.websocket.auth.WebSocketAuthenticationInterceptor;
import com.ss.websocket.auth.WebSocketHandshakeAuthenticator;
import com.ss.websocket.broker.WebSocketBrokerBridge;
import com.ss.websocket.broker.WebSocketBrokerMessage;
import com.ss.websocket.broker.WebSocketMessageBroker;
import com.ss.websocket.handler.AbstractAnonymousWebSocketHandler;
import com.ss.websocket.handler.AbstractAuthenticatedWebSocketHandler;
import com.ss.websocket.message.WebSocketMessageSender;
import com.ss.websocket.message.WebSocketMessenger;
import com.ss.websocket.session.WebSocketPrincipal;
import com.ss.websocket.session.WebSocketSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 WebSocket starter 的条件装配和端点校验。 */
class SimpleSecretWebSocketAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretWebSocketAutoConfiguration.class));

    @Test
    void shouldRemainDisabledByDefault() {
        runner.withUserConfiguration(AnonymousHandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(WebSocketSessionRegistry.class);
                    assertThat(context).doesNotHaveBean(WebSocketConfigurer.class);
                });
    }

    @Test
    void shouldCreateInfrastructureAndRegisterAnonymousHandlerWhenEnabled() {
        runner.withPropertyValues("simple-secret.websocket.enabled=true")
                .withUserConfiguration(AnonymousHandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(WebSocketSessionRegistry.class);
                    assertThat(context).hasSingleBean(WebSocketMessageSender.class);
                    assertThat(context).hasSingleBean(WebSocketMessenger.class);
                    assertThat(context).doesNotHaveBean(WebSocketBrokerBridge.class);

                    AbstractAnonymousWebSocketHandler handler =
                            context.getBean(AbstractAnonymousWebSocketHandler.class);
                    RegistrationMocks mocks = register(context.getBean(WebSocketConfigurer.class));
                    verify(mocks.registry()).addHandler(handler, "/events");
                    verify(mocks.registration(), org.mockito.Mockito.never())
                            .addInterceptors(any(HandshakeInterceptor[].class));
                });
    }

    @Test
    void shouldRegisterAuthenticatedHandlerWithApplicationAuthenticator() {
        runner.withPropertyValues("simple-secret.websocket.enabled=true")
                .withUserConfiguration(AuthenticatedHandlerConfiguration.class,
                        AuthenticatorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(WebSocketAuthenticationInterceptor.class);
                    AbstractAuthenticatedWebSocketHandler handler =
                            context.getBean(AbstractAuthenticatedWebSocketHandler.class);
                    RegistrationMocks mocks = register(context.getBean(WebSocketConfigurer.class));
                    verify(mocks.registry()).addHandler(handler, "/secure");
                    verify(mocks.registration()).addInterceptors(
                            any(WebSocketAuthenticationInterceptor.class));
                });
    }

    @Test
    void shouldFailFastWhenAuthenticatedHandlerHasNoAuthenticator() {
        runner.withPropertyValues("simple-secret.websocket.enabled=true")
                .withUserConfiguration(AuthenticatedHandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Authenticated WebSocket handlers require a WebSocketHandshakeAuthenticator bean");
                });
    }

    @Test
    void shouldBackOffInfrastructureBeansAndCreateBrokerBridgeWhenBrokerExists() {
        WebSocketMessageSender sender = new WebSocketMessageSender();
        runner.withPropertyValues("simple-secret.websocket.enabled=true")
                .withUserConfiguration(AnonymousHandlerConfiguration.class,
                        BrokerConfiguration.class)
                .withBean(WebSocketMessageSender.class, () -> sender)
                .run(context -> {
                    assertThat(context.getBean(WebSocketMessageSender.class)).isSameAs(sender);
                    assertThat(context).hasSingleBean(WebSocketBrokerBridge.class);
                });
    }

    @Test
    void shouldRejectDuplicatePathsInvalidPathsAndMissingAllowedHandler() {
        runner.withPropertyValues("simple-secret.websocket.enabled=true")
                .withUserConfiguration(DuplicateHandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "Duplicate WebSocket handler path: /events");
                });

        runner.withPropertyValues("simple-secret.websocket.enabled=true")
                .withUserConfiguration(InvalidHandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "path must start with '/'");
                });

        runner.withPropertyValues(
                        "simple-secret.websocket.enabled=true",
                        "simple-secret.websocket.paths[0]=/missing")
                .withUserConfiguration(AnonymousHandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "No WebSocket handler found for configured path: /missing");
                });
    }

    private static RegistrationMocks register(WebSocketConfigurer configurer) {
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(any(WebSocketHandler.class), any(String[].class)))
                .thenReturn(registration);
        when(registration.addInterceptors(any(HandshakeInterceptor[].class)))
                .thenReturn(registration);
        when(registration.setAllowedOrigins(any(String[].class))).thenReturn(registration);
        configurer.registerWebSocketHandlers(registry);
        return new RegistrationMocks(registry, registration);
    }

    private record RegistrationMocks(WebSocketHandlerRegistry registry,
                                     WebSocketHandlerRegistration registration) {
    }

    @Configuration(proxyBeanMethods = false)
    static class AnonymousHandlerConfiguration {
        @Bean
        AbstractAnonymousWebSocketHandler anonymousHandler() {
            return new AbstractAnonymousWebSocketHandler("/events") { };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AuthenticatedHandlerConfiguration {
        @Bean
        AbstractAuthenticatedWebSocketHandler authenticatedHandler(
                WebSocketSessionRegistry registry) {
            return new AbstractAuthenticatedWebSocketHandler("/secure", registry) { };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AuthenticatorConfiguration {
        @Bean
        WebSocketHandshakeAuthenticator authenticator() {
            return request -> Optional.of(new WebSocketPrincipal("42", "alice", null));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateHandlerConfiguration {
        @Bean
        AbstractAnonymousWebSocketHandler firstHandler() {
            return new AbstractAnonymousWebSocketHandler("/events") { };
        }

        @Bean
        AbstractAnonymousWebSocketHandler secondHandler() {
            return new AbstractAnonymousWebSocketHandler("/events") { };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InvalidHandlerConfiguration {
        @Bean
        AbstractAnonymousWebSocketHandler invalidHandler() {
            return new AbstractAnonymousWebSocketHandler("events") { };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BrokerConfiguration {
        @Bean
        WebSocketMessageBroker broker() {
            return new WebSocketMessageBroker() {
                @Override
                public void publish(WebSocketBrokerMessage message) {
                }

                @Override
                public AutoCloseable subscribe(Consumer<WebSocketBrokerMessage> consumer) {
                    return () -> { };
                }
            };
        }
    }
}
