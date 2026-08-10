package com.ss.easymedia.webrtc.service;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.repository.WebRtcSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.util.List;

/**
 * 重试未能及时删除的 WebRTC 会话。
 */
public class WebRtcSessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(WebRtcSessionCleanupJob.class);

    /** 读取到期关闭补偿任务的会话仓储。 */
    private final WebRtcSessionRepository repository;
    /** 执行上游会话删除补偿的生命周期服务。 */
    private final WebRtcSessionService service;
    /** 提供清理批次大小和调度配置。 */
    private final WebRtcProperties properties;
    /** 计算到期时间的统一时钟。 */
    private final Clock clock;

    /**
     * 创建关闭会话补偿任务。
     */
    public WebRtcSessionCleanupJob(WebRtcSessionRepository repository,
                                   WebRtcSessionService service,
                                   WebRtcProperties properties,
                                   Clock clock) {
        this.repository = repository;
        this.service = service;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 轮询到期的关闭会话并逐个重试删除；单个会话失败不会阻塞当前批次。
     */
    @Scheduled(
            fixedDelayString = "${simple-secret.easymedia.webrtc.cleanup-interval:PT10S}",
            initialDelayString = "${simple-secret.easymedia.webrtc.cleanup-initial-delay:PT30S}")
    public void retryClosingSessions() {
        List<String> sessionIds = repository.pollClosingRetries(
                clock.instant(), properties.getCleanupBatchSize());
        for (String sessionId : sessionIds) {
            try {
                service.retryDelete(sessionId);
            } catch (RuntimeException exception) {
                log.warn("WebRTC cleanup retry failed, sessionId={}, exception={}",
                        sessionId, exception.getClass().getSimpleName());
            }
        }
    }
}
