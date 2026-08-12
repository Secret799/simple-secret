package com.ss.nats.handler;

import com.ss.nats.message.NatsMessageContext;

/**
 * 可选的 NATS 入站消息校验器。
 */
@FunctionalInterface
public interface NatsMessageValidator {

    /**
     * @return 允许处理时返回 {@code true}
     *
     * @param message 消息
     */
    boolean validate(NatsMessageContext message);
}
