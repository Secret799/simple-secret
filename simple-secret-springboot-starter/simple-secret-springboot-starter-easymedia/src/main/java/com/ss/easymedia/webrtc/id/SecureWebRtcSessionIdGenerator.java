package com.ss.easymedia.webrtc.id;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 生成 192 位 URL 安全随机会话 ID。
 */
public final class SecureWebRtcSessionIdGenerator implements WebRtcSessionIdGenerator {

    /** URL 安全且不带填充的 Base64 编码器。 */
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /** 提供不可预测熵源的随机数生成器。 */
    private final SecureRandom secureRandom;

    /**
     * 创建会话 ID 生成器。
     */
    public SecureWebRtcSessionIdGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /**
     * 生成 24 字节随机值编码而成的 192 位会话标识。
     *
     * @return URL 安全的随机会话标识
     */
    @Override
    public String generate() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
