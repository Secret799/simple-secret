package com.ss.easymedia.webrtc.metrics;

import com.ss.easymedia.webrtc.domain.WebRtcOperation;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;

import java.time.Duration;

/**
 * WebRTC 会话指标接口。
 */
public interface WebRtcSessionMetrics {

    /**
     * 记录创建 WHIP 或 WHEP 会话的结果和耗时。

     *
     * @param type 目标类型
     * @param outcome 操作结果
     * @param duration 持续时间
     */
    void recordCreate(WebRtcSessionType type, String outcome, Duration duration);

    /**
     * 记录 PATCH 或 DELETE 会话操作的结果和耗时。

     *
     * @param operation 操作类型
     * @param outcome 操作结果
     * @param duration 持续时间
     */
    void recordMutation(WebRtcOperation operation, String outcome, Duration duration);

    /**
     * 记录后台删除补偿的一次执行结果。

     *
     * @param outcome 操作结果
     */
    void incrementCleanupRetry(String outcome);
}
