package com.ss.nats.message;

import io.nats.client.Message;
import io.nats.client.impl.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;

/**
 * NATS 入站或响应消息的不可变快照。
 */
public final class NatsMessageContext {
    /**
     * 客户端键。
     */
    private final String clientKey;
    /**
     * NATS subject。
     */
    private final String subject;
    /**
     * 响应主题。
     */
    private final String replyTo;
    /**
     * 消息负载。
     */
    private final byte[] payload;
    /**
     * 表头或消息头集合。
     */
    private final Headers headers;

    /**
     * 从 JNATS 消息创建快照。
     *
     * @param clientKey 客户端键
     * @param message JNATS 消息
     */
    public NatsMessageContext(String clientKey, Message message) {
        if (clientKey == null || clientKey.isBlank()) {
            throw new IllegalArgumentException("NATS clientKey must not be blank");
        }
        Message source = Objects.requireNonNull(message, "message");
        this.clientKey = clientKey;
        this.subject = source.getSubject();
        this.replyTo = source.getReplyTo();
        byte[] data = source.getData();
        this.payload = data == null ? new byte[0] : data.clone();
        this.headers = source.hasHeaders() && source.getHeaders() != null
                ? new Headers(source.getHeaders()) : new Headers();
    }

    /** @return 客户端键 */ public String getClientKey() { return clientKey; }
    /** @return 实际消息主题 */ public String getSubject() { return subject; }
    /** @return 回复主题，未设置时为 {@code null} */ public String getReplyTo() { return replyTo; }
    /** @return payload 防御性副本 */ public byte[] getPayload() { return payload.clone(); }
    /** @return headers 防御性副本 */ public Headers getHeaders() { return new Headers(headers); }

    /**
     * 将 payload 按 UTF-8 解码。
     *
     * @return {@code payloadAsString}
     */
    public String getPayloadAsString() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    /**
     * 使用调用方提供的字节解码函数转换 payload。
     *
     * @param decoder 消息解码器
     * @return 返回的 {@code T} 结果
     */
    public <T> T decode(Function<byte[], T> decoder) {
        return Objects.requireNonNull(decoder, "decoder").apply(payload.clone());
    }

    /**
     * 使用调用方提供的文本解码函数转换 UTF-8 payload。
     *
     * @param decoder 消息解码器
     * @return 返回的 {@code T} 结果
     */
    public <T> T decodeText(Function<String, T> decoder) {
        return Objects.requireNonNull(decoder, "decoder").apply(getPayloadAsString());
    }
}
