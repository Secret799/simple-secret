package com.ss.application.djisei.parser;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 一条已解析的 SEI 消息。
 *
 * @author junpzx
 * @since 2026-08-13
 */
public final class SeiMessage {

    /** SEI 负载类型。 */
    private final int payloadType;

    /** 防御性复制后的 SEI 负载。 */
    private final byte[] payload;

    /**
     * 创建 SEI 消息。
     *
     * @param payloadType SEI 负载类型
     * @param payload SEI 负载
     */
    public SeiMessage(int payloadType, byte[] payload) {
        this.payloadType = payloadType;
        Objects.requireNonNull(payload, "payload");
        this.payload = Arrays.copyOf(payload, payload.length);
    }

    /**
     * 获取 SEI 负载类型。
     *
     * @return SEI 负载类型
     */
    public int payloadType() {
        return payloadType;
    }

    /**
     * 获取 SEI 负载的防御性复制。
     *
     * @return SEI 负载副本
     */
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    /**
     * 获取用户数据未注册 SEI 的 UUID。
     *
     * @return payload 类型为 5 且至少包含 16 字节时的 UUID；否则为空
     */
    public Optional<UUID> uuid() {
        if (payloadType != 5 || payload.length < 16) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
    }
}
