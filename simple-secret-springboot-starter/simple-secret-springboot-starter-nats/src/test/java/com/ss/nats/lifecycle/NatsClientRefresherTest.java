package com.ss.nats.lifecycle;

import com.ss.nats.client.NatsClientManager;
import com.ss.nats.config.NatsClientOptions;
import com.ss.nats.config.NatsProperties;
import com.ss.nats.handler.NatsMessageHandler;
import com.ss.nats.message.NatsMessageContext;
import io.nats.client.Dispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

class NatsClientRefresherTest {
    private final ExecutorService publishExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService handlerExecutor = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdownExecutors() {
        publishExecutor.shutdownNow();
        handlerExecutor.shutdownNow();
    }

    @Test
    void refreshShouldConnectEnabledClientsAndSubscribeMatchingHandlers() {
        NatsProperties properties = new NatsProperties();
        properties.setClients(Map.of(
                "edge", enabledOptions(),
                "disabled", new NatsClientOptions()));
        RecordingManager manager = new RecordingManager();
        NatsMessageHandler edgeHandler = handler("edge", "events.edge");
        NatsMessageHandler otherHandler = handler("other", "events.other");

        new NatsClientRefresher(properties, manager, List.of(edgeHandler, otherHandler)).refresh();

        assertThat(manager.refreshedClients).containsOnlyKeys("edge");
        assertThat(manager.subscriptions).containsExactly(edgeHandler);
    }

    @Test
    void disabledPropertiesShouldCloseAllClients() {
        NatsProperties properties = new NatsProperties();
        properties.setEnabled(false);
        properties.setClients(Map.of("edge", enabledOptions()));
        RecordingManager manager = new RecordingManager();

        new NatsClientRefresher(properties, manager, List.of()).refresh();

        assertThat(manager.refreshedClients).isEmpty();
    }

    private static NatsClientOptions enabledOptions() {
        NatsClientOptions options = new NatsClientOptions();
        options.setEnabled(true);
        options.setUrl("nats://localhost:4222");
        return options;
    }

    private static NatsMessageHandler handler(String clientKey, String subject) {
        return new NatsMessageHandler() {
            @Override public String clientKey() { return clientKey; }
            @Override public String subject() { return subject; }
            @Override public void handle(NatsMessageContext message) { }
        };
    }

    private final class RecordingManager extends NatsClientManager {
        private Map<String, NatsClientOptions> refreshedClients = Map.of();
        private final List<NatsMessageHandler> subscriptions = new ArrayList<>();

        private RecordingManager() {
            super(publishExecutor, handlerExecutor);
        }

        @Override
        public synchronized void refreshClients(
                Map<String, NatsClientOptions> configuredClients,
                BiConsumer<String, NatsClientOptions> onConnected) {
            refreshedClients = Map.copyOf(configuredClients);
            configuredClients.forEach(onConnected);
        }

        @Override
        public synchronized Dispatcher subscribe(String clientKey, NatsMessageHandler handler) {
            subscriptions.add(handler);
            return null;
        }
    }
}
