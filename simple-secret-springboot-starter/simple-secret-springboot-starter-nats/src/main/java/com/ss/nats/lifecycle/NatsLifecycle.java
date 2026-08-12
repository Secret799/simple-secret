package com.ss.nats.lifecycle;

import com.ss.nats.client.NatsClientManager;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.Objects;

/**
 * 在 Spring Boot 启动完成后初始化 NATS 客户端，并在容器销毁时关闭连接。
 */
public class NatsLifecycle implements ApplicationRunner, AutoCloseable {
    private final NatsClientRefresher refresher;
    private final NatsClientManager clientManager;

    /**
     * 创建 NATS 生命周期组件。
     *
     * @param refresher NATS 客户端刷新器
     * @param clientManager 客户端管理器
     */
    public NatsLifecycle(NatsClientRefresher refresher, NatsClientManager clientManager) {
        this.refresher = Objects.requireNonNull(refresher, "refresher");
        this.clientManager = Objects.requireNonNull(clientManager, "clientManager");
    }

    /** 应用启动后执行首次配置刷新。 */
    @Override
    public void run(ApplicationArguments args) {
        refresher.refresh();
    }

    /** 关闭所有受管理连接。 */
    @Override
    public void close() {
        clientManager.close();
    }
}
