package com.ss.easymedia.webrtc.metrics;

import com.ss.easymedia.webrtc.domain.WebRtcOperation;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Locale;

/**
 * Micrometer WebRTC 会话指标。
 */
public class MicrometerWebRtcSessionMetrics implements WebRtcSessionMetrics {

    /** 注册 WebRTC Timer 和 Counter 的 Micrometer 仪表盘。 */
    private final MeterRegistry registry;

    /**
     * 创建 Micrometer 指标适配器。
     */
    public MicrometerWebRtcSessionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 记录创建会话的类型、结果和耗时。 */
    @Override
    public void recordCreate(WebRtcSessionType type, String outcome, Duration duration) {
        Timer.builder("simple-secret.webrtc.session.create")
                .tag("type", type.name().toLowerCase(Locale.ROOT))
                .tag("outcome", outcome)
                .register(registry)
                .record(duration);
    }

    /** 记录 PATCH 或 DELETE 操作的结果和耗时。 */
    @Override
    public void recordMutation(WebRtcOperation operation, String outcome, Duration duration) {
        Timer.builder("simple-secret.webrtc.session.mutation")
                .tag("operation", operation.name().toLowerCase(Locale.ROOT))
                .tag("outcome", outcome)
                .register(registry)
                .record(duration);
    }

    /** 递增后台删除补偿的结果计数。 */
    @Override
    public void incrementCleanupRetry(String outcome) {
        Counter.builder("simple-secret.webrtc.cleanup.retry")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }
}
