package com.ss.nats.handler;

import com.ss.nats.message.NatsMessageContext;

/**
 * NATS 入站消息处理器。
 */
public interface NatsMessageHandler {

    /** @return 客户端键，默认 {@code default} */
    default String clientKey() { return "default"; }

    /** @return 订阅 subject，可包含 NATS 订阅通配符 */
    String subject();

    /** @return queue group；空字符串表示普通订阅 */
    default String queue() { return ""; }

    /**
     * @return {@code true} 时在 Dispatcher 回调线程顺序处理，
     *         {@code false} 时提交到消息处理执行器
     */
    default boolean ordered() { return false; }

    /**
     * 处理消息。
     *
     * @param message 消息
     */
    void handle(NatsMessageContext message);
}
