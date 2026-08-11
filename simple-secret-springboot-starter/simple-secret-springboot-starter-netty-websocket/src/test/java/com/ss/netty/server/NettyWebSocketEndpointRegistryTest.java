package com.ss.netty.server;

import com.ss.netty.auth.NettyWebSocketAuthenticator;
import com.ss.netty.config.NettyWebSocketProperties;
import com.ss.netty.handler.NettyWebSocketMessageHandler;
import com.ss.netty.message.NettyWebSocketMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NettyWebSocketEndpointRegistryTest {

    @Test
    void shouldRequireAtLeastOneEnabledEndpoint() {
        assertThatThrownBy(() -> new NettyWebSocketEndpointRegistry(
                new NettyWebSocketProperties(), List.of(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void shouldRequireAuthenticatorForProtectedEndpoint() {
        NettyWebSocketProperties properties = propertiesWithEndpoint("events", "/events", true);

        assertThatThrownBy(() -> new NettyWebSocketEndpointRegistry(
                properties, List.of(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authenticator");
    }

    @Test
    void shouldRejectDuplicatePathsAndHandlersOutsideConfiguration() {
        NettyWebSocketProperties properties = propertiesWithEndpoint("events", "/events", false);
        NettyWebSocketProperties.Endpoint duplicate = new NettyWebSocketProperties.Endpoint();
        duplicate.setPath(" /events ");
        duplicate.setAuthenticationRequired(false);
        properties.getEndpoints().put("duplicate", duplicate);

        assertThatThrownBy(() -> new NettyWebSocketEndpointRegistry(
                properties, List.of(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");

        NettyWebSocketProperties clean = propertiesWithEndpoint("events", "/events", false);
        assertThatThrownBy(() -> new NettyWebSocketEndpointRegistry(
                clean, List.of(handler("/missing")), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configured endpoint");
    }

    @Test
    void shouldAllowPushOnlyEndpointAndIndexConfiguredHandler() {
        NettyWebSocketProperties properties = propertiesWithEndpoint("events", "/events", false);
        NettyWebSocketMessageHandler handler = handler("/events");

        NettyWebSocketEndpointRegistry pushOnly = new NettyWebSocketEndpointRegistry(
                properties, List.of(), null);
        NettyWebSocketEndpointRegistry withHandler = new NettyWebSocketEndpointRegistry(
                properties, List.of(handler), null);

        assertThat(pushOnly.endpoint("/events")).isPresent();
        assertThat(pushOnly.handler("/events")).isEmpty();
        assertThat(withHandler.handler("/events")).contains(handler);
    }

    @Test
    void shouldApplySameOriginByDefaultAndExactConfiguredOrigins() {
        NettyWebSocketProperties properties = propertiesWithEndpoint("events", "/events", false);
        NettyWebSocketEndpointRegistry registry = new NettyWebSocketEndpointRegistry(
                properties, List.of(), null);

        assertThat(registry.isOriginAllowed("/events", null, "localhost:9839")).isTrue();
        assertThat(registry.isOriginAllowed(
                "/events", "http://localhost:9839", "localhost:9839")).isTrue();
        assertThat(registry.isOriginAllowed(
                "/events", "https://attacker.example", "localhost:9839")).isFalse();

        properties.getEndpoints().get("events").setAllowedOrigins(
                List.of("https://console.example.com"));
        registry = new NettyWebSocketEndpointRegistry(properties, List.of(), null);
        assertThat(registry.isOriginAllowed(
                "/events", "https://console.example.com", "internal:9839")).isTrue();
        assertThat(registry.isOriginAllowed(
                "/events", "https://other.example.com", "internal:9839")).isFalse();
    }

    @Test
    void shouldRejectInvalidServerAndExecutorLimits() {
        NettyWebSocketProperties properties = propertiesWithEndpoint("events", "/events", false);
        properties.setHost(" ");
        assertInvalid(properties, "host");

        properties = propertiesWithEndpoint("events", "/events", false);
        properties.setPort(65_536);
        assertInvalid(properties, "port");

        properties = propertiesWithEndpoint("events", "/events", false);
        properties.setMaxFramePayloadLength(0);
        assertInvalid(properties, "max-frame-payload-length");

        properties = propertiesWithEndpoint("events", "/events", false);
        properties.setHandshakeTimeout(Duration.ZERO);
        assertInvalid(properties, "handshake-timeout");

        properties = propertiesWithEndpoint("events", "/events", false);
        properties.setHandlerCoreSize(9);
        properties.setHandlerMaxSize(8);
        assertInvalid(properties, "handler-max-size");

        properties = propertiesWithEndpoint("events", "/events", false);
        properties.setHandlerMaxSize(Integer.MAX_VALUE);
        properties.setHandlerQueueCapacity(1);
        assertInvalid(properties, "handler capacity");
    }

    @Test
    void shouldDefensivelyCopyPublicEndpointOrigins() {
        Set<String> origins = new LinkedHashSet<>(List.of("https://console.example.com"));
        NettyWebSocketEndpoint endpoint = new NettyWebSocketEndpoint(
                "events", "/events", false, origins);

        origins.add("https://attacker.example");

        assertThat(endpoint.allowedOrigins())
                .containsExactly("https://console.example.com");
        assertThatThrownBy(() -> endpoint.allowedOrigins().add("https://other.example"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertInvalid(NettyWebSocketProperties properties, String text) {
        NettyWebSocketAuthenticator authenticator = request -> Optional.empty();
        assertThatThrownBy(() -> new NettyWebSocketEndpointRegistry(
                properties, List.of(), authenticator))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(text);
    }

    private static NettyWebSocketProperties propertiesWithEndpoint(
            String name, String path, boolean authenticationRequired) {
        NettyWebSocketProperties properties = new NettyWebSocketProperties();
        NettyWebSocketProperties.Endpoint endpoint = new NettyWebSocketProperties.Endpoint();
        endpoint.setPath(path);
        endpoint.setAuthenticationRequired(authenticationRequired);
        properties.getEndpoints().put(name, endpoint);
        return properties;
    }

    private static NettyWebSocketMessageHandler handler(String path) {
        return new NettyWebSocketMessageHandler() {
            @Override
            public String path() {
                return path;
            }

            @Override
            public void handle(NettyWebSocketMessage message) {
                // Not used by endpoint-index tests.
            }
        };
    }
}
