package com.ss.mqttv3.lifecycle;

import com.ss.mqttv3.client.MqttClientManager;
import com.ss.mqttv3.config.MqttClientOptions;
import com.ss.mqttv3.handler.MqttMessageHandler;
import com.ss.mqttv3.config.MqttProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 根据当前属性协调 MQTT 客户端集合与消息处理器订阅。
 */
public class MqttClientRefresher {
    private static final System.Logger LOG =
            System.getLogger(MqttClientRefresher.class.getName());

    private final MqttProperties properties;
    private final MqttClientManager clientManager;
    private final List<MqttMessageHandler> handlers;

    /**
     * 创建 MQTT 客户端刷新协调器。
     *
     * @param properties    MQTT 配置属性
     * @param clientManager MQTT 客户端管理器
     * @param handlers      容器中的消息处理器
     */
    public MqttClientRefresher(MqttProperties properties,
                               MqttClientManager clientManager,
                               List<MqttMessageHandler> handlers) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clientManager = Objects.requireNonNull(clientManager, "clientManager");
        this.handlers = List.copyOf(Objects.requireNonNull(handlers, "handlers"));
    }

    /**
     * 按最新配置刷新启用客户端，并在连接完成后恢复对应处理器订阅。
     */
    public void refresh() {
        if (!properties.isEnabled()) {
            clientManager.refreshClients(Map.of(), this::subscribeHandlers);
            return;
        }
        Map<String, MqttClientOptions> enabledClients = new LinkedHashMap<>();
        properties.getClients().forEach((clientKey, options) -> {
            if (clientKey == null || clientKey.isBlank()) {
                throw new IllegalArgumentException("MQTT client key must not be blank");
            }
            if (options == null) {
                throw new IllegalArgumentException(
                        "MQTT client options must not be null: " + clientKey);
            }
            if (options.isEnabled()) {
                enabledClients.put(clientKey, options);
            }
        });
        clientManager.refreshClients(enabledClients, this::subscribeHandlers);
    }

    private void subscribeHandlers(String clientKey, MqttClientOptions options) {
        RuntimeException firstFailure = null;
        for (MqttMessageHandler handler : handlers) {
            try {
                if (clientKey.equals(handler.clientKey())) {
                    clientManager.subscribe(clientKey, handler);
                }
            } catch (RuntimeException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
                LOG.log(System.Logger.Level.ERROR,
                        "MQTT handler subscription failed, clientKey=" + clientKey
                                + ", handler=" + handler.getClass().getName(), e);
            }
        }
        if (firstFailure != null) {
            throw new IllegalStateException(
                    "One or more MQTT handler subscriptions failed: " + clientKey,
                    firstFailure);
        }
    }
}
