package com.ss.mqttv3.lifecycle;

import com.ss.mqttv3.client.MqttClientManager;
import com.ss.mqttv3.config.MqttClientOptions;
import com.ss.mqttv3.handler.MqttMessageHandler;
import com.ss.mqttv3.message.MqttMessageContext;
import com.ss.mqttv3.waiter.DefaultMqttResponseWaiter;
import com.ss.mqttv3.config.MqttProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttClientRefresherTest {

    @Test
    void refreshesOnlyEnabledClientsAndPartitionsHandlersByClientKey() {
        Fixture fixture = new Fixture();
        try {
            MqttClientOptions edge = options(true);
            MqttClientOptions disabled = options(false);
            fixture.properties.setClients(new LinkedHashMap<>(Map.of(
                    "edge", edge,
                    "disabled", disabled)));
            MqttMessageHandler defaultHandler = handler("default", "default/#");
            MqttMessageHandler edgeHandler = handler("edge", "edge/#");
            MqttClientRefresher refresher = new MqttClientRefresher(
                    fixture.properties, fixture.manager, List.of(defaultHandler, edgeHandler));

            refresher.refresh();

            assertEquals(Map.of("edge", edge), fixture.manager.refreshedClients);
            assertEquals(List.of("edge:" + edgeHandler.topic()), fixture.manager.subscriptions);
        } finally {
            fixture.close();
        }
    }

    @Test
    void rejectsBlankClientKeyBeforeRefreshingManager() {
        Fixture fixture = new Fixture();
        try {
            fixture.properties.setClients(new LinkedHashMap<>());
            fixture.properties.getClients().put(" ", options(true));
            MqttClientRefresher refresher = new MqttClientRefresher(
                    fixture.properties, fixture.manager, List.of());

            assertThrows(IllegalArgumentException.class, refresher::refresh);
            assertTrue(fixture.manager.refreshedClients.isEmpty());
        } finally {
            fixture.close();
        }
    }

    @Test
    void topLevelDisableClosesAllManagedClients() {
        Fixture fixture = new Fixture();
        try {
            fixture.properties.setClients(Map.of("edge", options(true)));
            MqttClientRefresher refresher = new MqttClientRefresher(
                    fixture.properties, fixture.manager, List.of());
            refresher.refresh();
            assertEquals(1, fixture.manager.refreshedClients.size());

            fixture.properties.setEnabled(false);
            refresher.refresh();

            assertTrue(fixture.manager.refreshedClients.isEmpty());
        } finally {
            fixture.close();
        }
    }

    @Test
    void handlerSubscribeFailureDoesNotSkipRemainingHandlers() {
        Fixture fixture = new Fixture();
        try {
            fixture.properties.setClients(Map.of("edge", options(true)));
            MqttMessageHandler failing = handler("edge", "edge/failing");
            MqttMessageHandler succeeding = handler("edge", "edge/succeeding");
            fixture.manager.failingTopic = failing.topic();
            MqttClientRefresher refresher = new MqttClientRefresher(
                    fixture.properties, fixture.manager, List.of(failing, succeeding));

            assertThrows(IllegalStateException.class, refresher::refresh);
            assertEquals(List.of("edge:" + succeeding.topic()), fixture.manager.subscriptions);
        } finally {
            fixture.close();
        }
    }

    @Test
    void lifecycleRunsInitialRefreshAndClosesManager() throws Exception {
        Fixture fixture = new Fixture();
        try {
            MqttClientRefresher refresher = new MqttClientRefresher(
                    fixture.properties, fixture.manager, List.of());
            MqttLifecycle lifecycle = new MqttLifecycle(refresher, fixture.manager);

            lifecycle.run(null);
            lifecycle.close();

            assertEquals(1, fixture.manager.refreshCount);
            assertEquals(1, fixture.manager.closeCount);
        } finally {
            fixture.closeExecutors();
        }
    }

    private static MqttClientOptions options(boolean enabled) {
        MqttClientOptions options = new MqttClientOptions();
        options.setEnabled(enabled);
        options.setBroker("tcp://test");
        return options;
    }

    private static MqttMessageHandler handler(String clientKey, String topic) {
        return new MqttMessageHandler() {
            @Override
            public String clientKey() {
                return clientKey;
            }

            @Override
            public String topic() {
                return topic;
            }

            @Override
            public void handle(MqttMessageContext message) {
            }
        };
    }

    private static final class Fixture implements AutoCloseable {
        private final ExecutorService publishExecutor = Executors.newSingleThreadExecutor();
        private final ExecutorService handlerExecutor = Executors.newSingleThreadExecutor();
        private final ScheduledExecutorService connectionExecutor = Executors.newSingleThreadScheduledExecutor();
        private final MqttProperties properties = new MqttProperties();
        private final RecordingManager manager = new RecordingManager(
                publishExecutor, handlerExecutor, connectionExecutor);

        @Override
        public void close() {
            manager.close();
            closeExecutors();
        }

        private void closeExecutors() {
            publishExecutor.shutdownNow();
            handlerExecutor.shutdownNow();
            connectionExecutor.shutdownNow();
        }
    }

    private static final class RecordingManager extends MqttClientManager {
        private Map<String, MqttClientOptions> refreshedClients = Map.of();
        private final List<String> subscriptions = new ArrayList<>();
        private String failingTopic;
        private int refreshCount;
        private int closeCount;

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
            refreshedClients = new LinkedHashMap<>(enabledClients);
            enabledClients.forEach(onConnected);
        }

        @Override
        public void subscribe(String clientKey, MqttMessageHandler handler) {
            if (handler.topic().equals(failingTopic)) {
                throw new IllegalStateException("subscribe failed");
            }
            subscriptions.add(clientKey + ":" + handler.topic());
        }

        @Override
        public synchronized void close() {
            closeCount++;
        }
    }
}
