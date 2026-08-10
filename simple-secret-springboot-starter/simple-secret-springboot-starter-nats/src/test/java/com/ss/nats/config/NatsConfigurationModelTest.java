package com.ss.nats.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NatsConfigurationModelTest {

    @Test
    void defaultsShouldNeverConnectOrContainCredentials() {
        NatsProperties properties = new NatsProperties();
        NatsClientOptions client = new NatsClientOptions();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getClients()).isEmpty();
        assertThat(client.isEnabled()).isFalse();
        assertThat(client.getUrl()).isNull();
        assertThat(client.getUsername()).isNull();
        assertThat(client.getPassword()).isNull();
        assertThat(client.isReconnectEnabled()).isTrue();
        assertThat(client.resolveConnectionName("edge")).isEqualTo("simple-secret-nats-edge");
    }

    @Test
    void configuredConnectionNameShouldWinAndNullClientMapShouldNormalize() {
        NatsClientOptions client = new NatsClientOptions();
        client.setConnectionName("telemetry-consumer");
        NatsProperties properties = new NatsProperties();
        properties.setClients(null);

        assertThat(client.resolveConnectionName("edge")).isEqualTo("telemetry-consumer");
        assertThat(properties.getClients()).isEmpty();
        properties.setClients(Map.of("edge", client));
        assertThat(properties.getClients()).containsEntry("edge", client);
    }

    @Test
    void enabledClientShouldRequireValidUrlAndPositiveTimeouts() {
        NatsClientOptions client = new NatsClientOptions();
        client.setEnabled(true);

        assertThatThrownBy(() -> client.validate("edge"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL");

        client.setUrl("http://localhost:4222");
        assertThatThrownBy(() -> client.validate("edge"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scheme");

        client.setUrl("nats://localhost:4222");
        client.setPublishTimeoutMillis(0);
        assertThatThrownBy(() -> client.validate("edge"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publish timeout");
    }

    @Test
    void passwordShouldNeverBeSilentlyIgnoredWithoutUsername() {
        NatsClientOptions client = new NatsClientOptions();
        client.setEnabled(true);
        client.setUrl("nats://localhost:4222");
        client.setPassword("secret");

        assertThatThrownBy(() -> client.validate("edge"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username")
                .hasMessageNotContaining("secret");
    }
}
