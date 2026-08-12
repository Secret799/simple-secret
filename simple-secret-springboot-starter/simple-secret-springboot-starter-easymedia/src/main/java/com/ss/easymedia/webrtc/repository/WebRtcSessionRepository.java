package com.ss.easymedia.webrtc.repository;

import com.ss.easymedia.webrtc.domain.WebRtcSessionRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * WebRTC 会话状态仓储。
 */
public interface WebRtcSessionRepository {

    /**
     * 原子创建会话；已有同名会话时不覆盖。
     *
     * @return 新记录是否已写入

     *
     * @param record 会话记录
     * @param ttl 缓存有效期
     */
    boolean create(WebRtcSessionRecord record, Duration ttl);

    /**
     * @return 会话副本；会话不存在或过期时为空。
     *
     * @param sessionId 会话 ID
     */
    Optional<WebRtcSessionRecord> find(String sessionId);

    /**
     * 保存会话并重置其存活时间。

     *
     * @param record 会话记录
     * @param ttl 缓存有效期
     */
    void save(WebRtcSessionRecord record, Duration ttl);

    /**
     * 保存会话但保留当前剩余存活时间。
     *
     * @return 会话尚存在且已保存时为 true

     *
     * @param record 会话记录
     */
    boolean savePreservingTtl(WebRtcSessionRecord record);

    /**
     * 删除会话及其待关闭索引。
     *
     * @return 是否删除了会话记录

     *
     * @param sessionId 会话 ID
     */
    boolean delete(String sessionId);

    /**
     * 在指定会话的分布式锁内执行操作。

     *
     * @param sessionId 会话 ID
     * @param action 会话记录原子更新函数
     * @return 返回的 {@code T} 结果
     */
    <T> T withSessionLock(String sessionId, Supplier<T> action);

    /**
     * 将会话加入按下次重试时间排序的关闭补偿队列。

     *
     * @param sessionId 会话 ID
     * @param retryAt 下次重试时间
     */
    void scheduleClosingRetry(String sessionId, Instant retryAt);

    /**
     * 取出截至指定时间到期的关闭补偿会话。
     *
     * @return 最多 limit 个待处理会话 ID

     *
     * @param now 当前时间
     * @param limit 数量上限
     */
    List<String> pollClosingRetries(Instant now, int limit);

    /**
     * 从关闭补偿队列中移除会话。

     *
     * @param sessionId 会话 ID
     */
    void removeClosingRetry(String sessionId);
}
