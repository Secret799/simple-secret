package com.ss.websocket.config;

import com.ss.websocket.auth.WebSocketAuthenticationInterceptor;
import com.ss.websocket.auth.WebSocketHandshakeAuthenticator;
import com.ss.websocket.broker.WebSocketBrokerBridge;
import com.ss.websocket.broker.WebSocketMessageBroker;
import com.ss.websocket.handler.AbstractAuthenticatedWebSocketHandler;
import com.ss.websocket.handler.AbstractSimpleSecretWebSocketHandler;
import com.ss.websocket.message.WebSocketMessageSender;
import com.ss.websocket.message.WebSocketMessenger;
import com.ss.websocket.session.WebSocketSessionRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Simple Secret WebSocket 基础设施自动配置。 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {"jakarta.servlet.Servlet", "org.springframework.web.socket.WebSocketHandler"})
@ConditionalOnProperty(prefix = "simple-secret.websocket", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WebSocketProperties.class)
@EnableWebSocket
public class SimpleSecretWebSocketAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    WebSocketSessionRegistry simpleSecretWebSocketSessionRegistry(WebSocketProperties properties) {
        Duration timeLimit = properties.getSendTimeLimit();
        if (timeLimit == null || timeLimit.isZero() || timeLimit.isNegative()
                || timeLimit.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalStateException("WebSocket send-time-limit must be positive and fit milliseconds");
        }
        return new WebSocketSessionRegistry(
                Math.toIntExact(timeLimit.toMillis()), properties.getSendBufferSize());
    }

    @Bean
    @ConditionalOnMissingBean
    WebSocketMessageSender simpleSecretWebSocketMessageSender() {
        return new WebSocketMessageSender();
    }

    @Bean
    @ConditionalOnMissingBean
    WebSocketMessenger simpleSecretWebSocketMessenger(WebSocketSessionRegistry registry,
                                                       WebSocketMessageSender sender) {
        return new WebSocketMessenger(registry, sender);
    }

    @Bean
    @ConditionalOnBean(WebSocketHandshakeAuthenticator.class)
    @ConditionalOnMissingBean
    WebSocketAuthenticationInterceptor simpleSecretWebSocketAuthenticationInterceptor(
            WebSocketHandshakeAuthenticator authenticator) {
        return new WebSocketAuthenticationInterceptor(authenticator);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnBean(WebSocketMessageBroker.class)
    @ConditionalOnMissingBean
    WebSocketBrokerBridge simpleSecretWebSocketBrokerBridge(WebSocketProperties properties,
                                                             WebSocketMessenger messenger,
                                                             WebSocketMessageBroker broker) {
        return new WebSocketBrokerBridge(properties.getNodeId(), messenger, broker);
    }

    @Bean
    WebSocketConfigurer simpleSecretWebSocketConfigurer(
            List<AbstractSimpleSecretWebSocketHandler> handlers,
            WebSocketProperties properties,
            ObjectProvider<WebSocketAuthenticationInterceptor> authenticationInterceptor) {
        Map<String, AbstractSimpleSecretWebSocketHandler> handlersByPath = indexHandlers(handlers);
        List<AbstractSimpleSecretWebSocketHandler> selected =
                selectHandlers(handlersByPath, properties.getPaths());
        WebSocketAuthenticationInterceptor interceptor = authenticationInterceptor.getIfAvailable();
        if (interceptor == null && selected.stream()
                .anyMatch(AbstractAuthenticatedWebSocketHandler.class::isInstance)) {
            throw new IllegalStateException("Authenticated WebSocket handlers require a "
                    + "WebSocketHandshakeAuthenticator bean");
        }
        List<String> allowedOrigins = normalizedValues(properties.getAllowedOrigins());
        return registry -> selected.forEach(handler -> {
            WebSocketHandlerRegistration registration = registry.addHandler(handler, handler.path());
            if (handler instanceof AbstractAuthenticatedWebSocketHandler) {
                registration.addInterceptors(interceptor);
            }
            if (!allowedOrigins.isEmpty()) {
                registration.setAllowedOrigins(allowedOrigins.toArray(String[]::new));
            }
        });
    }

    private static Map<String, AbstractSimpleSecretWebSocketHandler> indexHandlers(
            List<AbstractSimpleSecretWebSocketHandler> handlers) {
        Map<String, AbstractSimpleSecretWebSocketHandler> indexed = new LinkedHashMap<>();
        handlers.forEach(handler -> {
            AbstractSimpleSecretWebSocketHandler previous = indexed.putIfAbsent(
                    handler.path(), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate WebSocket handler path: " + handler.path());
            }
        });
        return indexed;
    }

    private static List<AbstractSimpleSecretWebSocketHandler> selectHandlers(
            Map<String, AbstractSimpleSecretWebSocketHandler> indexed, List<String> configuredPaths) {
        List<String> paths = normalizedValues(configuredPaths);
        if (paths.isEmpty()) {
            return List.copyOf(indexed.values());
        }
        List<AbstractSimpleSecretWebSocketHandler> selected = new ArrayList<>();
        for (String path : paths) {
            if (!path.startsWith("/")) {
                throw new IllegalStateException("Configured WebSocket path must start with '/': " + path);
            }
            AbstractSimpleSecretWebSocketHandler handler = indexed.get(path);
            if (handler == null) {
                throw new IllegalStateException(
                        "No WebSocket handler found for configured path: " + path);
            }
            selected.add(handler);
        }
        return List.copyOf(selected);
    }

    private static List<String> normalizedValues(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
