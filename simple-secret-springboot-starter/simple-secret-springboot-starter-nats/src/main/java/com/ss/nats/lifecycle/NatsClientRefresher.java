package com.ss.nats.lifecycle;

import com.ss.nats.client.NatsClientManager;
import com.ss.nats.config.NatsClientOptions;
import com.ss.nats.config.NatsProperties;
import com.ss.nats.handler.NatsMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 根据当前配置刷新 NATS 客户端，并为新连接恢复对应处理器订阅。
 */
public class NatsClientRefresher {
    private static final Logger LOGGER = LoggerFactory.getLogger(NatsClientRefresher.class);

    private final NatsProperties properties;
    private final NatsClientManager clientManager;
    private final List<NatsMessageHandler> handlers;

    /**
     * 创建 NATS 客户端刷新协调器。
     *
     * @param properties 模块配置
     * @param clientManager 客户端管理器
     * @param handlers 处理器集合
     */
    public NatsClientRefresher(NatsProperties properties, NatsClientManager clientManager,
                               List<NatsMessageHandler> handlers) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clientManager = Objects.requireNonNull(clientManager, "clientManager");
        this.handlers = List.copyOf(Objects.requireNonNull(handlers, "handlers"));
    }

    /**
     * 应用最新配置。全局禁用时关闭全部连接，仅显式启用的客户端会建立连接。
     */
    public void refresh() {
        properties.validate();
        if (!properties.isEnabled()) {
            clientManager.refreshClients(Map.of(), this::subscribeHandlers);
            return;
        }
        Map<String, NatsClientOptions> enabledClients = new LinkedHashMap<>();
        properties.getClients().forEach((clientKey, options) -> {
            if (clientKey == null || clientKey.isBlank()) {
                throw new IllegalArgumentException("NATS client key must not be blank");
            }
            if (options == null) {
                throw new IllegalArgumentException("NATS client options must not be null: " + clientKey);
            }
            options.validate(clientKey);
            if (options.isEnabled()) {
                enabledClients.put(clientKey, options);
            }
        });
        clientManager.refreshClients(enabledClients, this::subscribeHandlers);
    }

    private void subscribeHandlers(String clientKey, NatsClientOptions options) {
        RuntimeException firstFailure = null;
        for (NatsMessageHandler handler : handlers) {
            if (!clientKey.equals(handler.clientKey())) {
                continue;
            }
            try {
                clientManager.subscribe(clientKey, handler);
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
                }
                LOGGER.error("NATS handler subscription failed clientKey={} handler={}",
                        clientKey, handler.getClass().getName(), exception);
            }
        }
        if (firstFailure != null) {
            throw new IllegalStateException(
                    "One or more NATS handler subscriptions failed for client " + clientKey,
                    firstFailure);
        }
    }
}
