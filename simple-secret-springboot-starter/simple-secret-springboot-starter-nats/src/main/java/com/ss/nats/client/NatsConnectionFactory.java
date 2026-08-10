package com.ss.nats.client;

import io.nats.client.Connection;
import io.nats.client.Options;

import java.io.IOException;

/**
 * NATS 连接创建边界，包内可替换以便在不启动服务器时测试生命周期。
 */
@FunctionalInterface
interface NatsConnectionFactory {

    /** 创建一个同步 NATS 连接。 */
    Connection connect(Options options) throws IOException, InterruptedException;
}
