package com.ss.mqttv3.lifecycle;

import com.ss.mqttv3.client.MqttClientManager;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.Objects;

/**
 * 在应用启动后初始化 MQTT 客户端，并在容器销毁时关闭客户端。
 */
public class MqttLifecycle implements ApplicationRunner, AutoCloseable {
    private final MqttClientRefresher refresher;
    private final MqttClientManager clientManager;

    /**
     * 创建 MQTT 生命周期组件。
     *
     * @param refresher     客户端刷新协调器
     * @param clientManager 客户端管理器
     */
    public MqttLifecycle(MqttClientRefresher refresher, MqttClientManager clientManager) {
        this.refresher = Objects.requireNonNull(refresher, "refresher");
        this.clientManager = Objects.requireNonNull(clientManager, "clientManager");
    }

    /**
     * 应用启动后执行首次客户端刷新。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        refresher.refresh();
    }

    /**
     * 关闭所有受管理 MQTT 客户端。
     */
    @Override
    public void close() {
        clientManager.close();
    }
}
