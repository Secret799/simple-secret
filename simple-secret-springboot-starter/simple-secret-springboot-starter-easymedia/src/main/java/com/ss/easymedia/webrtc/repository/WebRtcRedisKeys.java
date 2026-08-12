package com.ss.easymedia.webrtc.repository;

import com.ss.easymedia.webrtc.domain.WebRtcOperation;
import com.ss.easymedia.webrtc.support.Sha256Utils;

import java.util.Locale;

/**
 * WebRTC Redis 键生成器。
 */
public class WebRtcRedisKeys {

    /** 所有 WebRTC Redis 数据的命名空间前缀。 */
    private static final String PREFIX = "ems:webrtc:";

    /**
     * @return 指定会话快照的 Redis 键。
     *
     * @param sessionId 会话 ID
     */
    public String session(String sessionId) {
        return PREFIX + "session:" + sessionId;
    }

    /**
     * @return 指定会话串行化操作使用的分布式锁键。
     *
     * @param sessionId 会话 ID
     */
    public String sessionLock(String sessionId) {
        return PREFIX + "lock:session:" + sessionId;
    }

    /** @return 保存待补偿删除会话的有序集合键。 */
    public String closingIndex() {
        return PREFIX + "closing";
    }

    /**
     * 构造按操作、主体和客户端 IP 隔离的限流键。
     *
     * @return 不暴露原始身份信息的 Redis 限流键

     *
     * @param operation 操作类型
     * @param identityKey 身份维度缓存键
     * @param clientIp 客户端 IP 地址
     */
    public String rateLimit(WebRtcOperation operation, String identityKey, String clientIp) {
        return PREFIX + "rate:" + operation.name().toLowerCase(Locale.ROOT)
                + ":" + keyHash(identityKey) + ":" + keyHash(clientIp);
    }

    /**
     * 对可能包含隐私信息的限流维度执行固定长度哈希。
     */
    private String keyHash(String value) {
        String normalized = value == null ? "unknown" : value;
        return Sha256Utils.sha256Hex(normalized).substring(0, 32);
    }
}
