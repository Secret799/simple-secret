package com.ss.consumer.mqttv5;

import com.ss.mqttv5.client.MqttClientManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies MQTT v5 auto-configuration without contacting a broker. */
class Mqttv5StarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void createsManagerWithNoConfiguredClients() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MqttClientManager.class);
            assertThat(context.getBean(MqttClientManager.class)
                    .containsClient(MqttClientManager.DEFAULT_CLIENT_KEY)).isFalse();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}
