package com.ss.mqttv3.lifecycle;

import com.ss.mqttv3.client.MqttClientManager;
import com.ss.mqttv3.config.MqttClientOptions;
import com.ss.mqttv3.waiter.DefaultMqttResponseWaiter;
import com.ss.mqttv3.config.MqttProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MqttConfigurationRefreshListenerTest {

    @Test
    void refreshesOnlyForSimpleSecretMqttKeys() {
        ExecutorService publishExecutor = Executors.newSingleThreadExecutor();
        ExecutorService handlerExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService connectionExecutor = Executors.newSingleThreadScheduledExecutor();
        RecordingManager manager = new RecordingManager(
                publishExecutor, handlerExecutor, connectionExecutor);
        try {
            MqttClientRefresher refresher = new MqttClientRefresher(
                    new MqttProperties(), manager, java.util.List.of());
            MqttConfigurationRefreshListener listener =
                    new MqttConfigurationRefreshListener(refresher);

            listener.onApplicationEvent(new EnvironmentChangeEvent(
                    List.of("server.port")));
            listener.onApplicationEvent(new EnvironmentChangeEvent(
                    List.of("simple-secret.mqttv3.clients.default.broker")));

            assertEquals(1, manager.refreshCount);
        } finally {
            manager.close();
            publishExecutor.shutdownNow();
            handlerExecutor.shutdownNow();
            connectionExecutor.shutdownNow();
        }
    }

    private static final class RecordingManager extends MqttClientManager {
        private int refreshCount;

        private RecordingManager(ExecutorService publishExecutor,
                                 ExecutorService handlerExecutor,
                                 ScheduledExecutorService connectionExecutor) {
            super(publishExecutor, handlerExecutor, connectionExecutor,
                    new DefaultMqttResponseWaiter());
        }

        @Override
        public void refreshClients(Map<String, MqttClientOptions> enabledClients,
                                   BiConsumer<String, MqttClientOptions> onConnected) {
            refreshCount++;
        }
    }
}
