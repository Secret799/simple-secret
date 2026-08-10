package com.ss.mqttv5.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;

/**
 * 以反射方式监听可选的 Spring Cloud 环境配置变更事件。
 */
public class MqttConfigurationRefreshListener {
    private static final Logger LOG =
            LoggerFactory.getLogger(MqttConfigurationRefreshListener.class);
    private static final String ENVIRONMENT_CHANGE_EVENT =
            "org.springframework.cloud.context.environment.EnvironmentChangeEvent";
    private static final String MQTT_PREFIX = "simple-secret.mqtt";

    private final MqttClientRefresher refresher;

    /**
     * 创建 MQTT 配置刷新监听器。
     *
     * @param refresher 客户端刷新协调器
     */
    public MqttConfigurationRefreshListener(MqttClientRefresher refresher) {
        this.refresher = Objects.requireNonNull(refresher, "refresher");
    }

    /**
     * 接收应用事件，仅在 MQTT 配置键发生变化时刷新客户端。
     *
     * @param event 应用事件
     */
    @EventListener
    public void onApplicationEvent(Object event) {
        if (event == null || !ENVIRONMENT_CHANGE_EVENT.equals(event.getClass().getName())) {
            return;
        }
        Collection<?> keys = changedKeys(event);
        if (keys == null || keys.stream().noneMatch(this::isMqttKey)) {
            return;
        }
        refresher.refresh();
    }

    private Collection<?> changedKeys(Object event) {
        try {
            Method method = event.getClass().getMethod("getKeys");
            Object value = method.invoke(event);
            if (value instanceof Collection<?> collection) {
                return collection;
            }
            LOG.warn("Ignoring MQTT configuration event with non-collection keys");
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("Unable to read MQTT configuration change keys", e);
        }
        return null;
    }

    private boolean isMqttKey(Object key) {
        return key instanceof String value
                && (value.equals(MQTT_PREFIX) || value.startsWith(MQTT_PREFIX + "."));
    }
}
