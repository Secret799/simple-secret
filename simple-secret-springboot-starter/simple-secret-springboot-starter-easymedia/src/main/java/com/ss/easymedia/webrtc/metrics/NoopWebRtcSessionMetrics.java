package com.ss.easymedia.webrtc.metrics;

import com.ss.easymedia.webrtc.domain.WebRtcOperation;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;

import java.time.Duration;

/**
 * 未配置 MeterRegistry 时使用的空指标实现。
 */
public class NoopWebRtcSessionMetrics implements WebRtcSessionMetrics {

    /** 不记录创建指标。 */
    @Override
    public void recordCreate(WebRtcSessionType type, String outcome, Duration duration) {
    }

    /** 不记录会话操作指标。 */
    @Override
    public void recordMutation(WebRtcOperation operation, String outcome, Duration duration) {
    }

    /** 不记录清理补偿指标。 */
    @Override
    public void incrementCleanupRetry(String outcome) {
    }
}
