package com.ss.consumer.mqttv3;

import com.ss.mqttv3.client.MqttClientManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that the MQTT v3 and v5 starters can share one consumer application. */
class Mqttv3StarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void createsBothManagersWithoutOpeningImplicitClients() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MqttClientManager.class);
            assertThat(context).hasSingleBean(
                    com.ss.mqttv5.client.MqttClientManager.class);
            assertThat(context.getBean(MqttClientManager.class)
                    .containsClient(MqttClientManager.DEFAULT_CLIENT_KEY)).isFalse();
            assertThat(context.getBean(com.ss.mqttv5.client.MqttClientManager.class)
                    .containsClient(com.ss.mqttv5.client.MqttClientManager.DEFAULT_CLIENT_KEY))
                    .isFalse();
            assertThat(context).hasBean("mqttv3ClientManager");
            assertThat(context).hasBean("mqttClientManager");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}
