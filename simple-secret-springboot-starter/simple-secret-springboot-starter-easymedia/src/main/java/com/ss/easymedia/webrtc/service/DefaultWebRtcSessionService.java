package com.ss.easymedia.webrtc.service;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.client.ZlmWebRtcSignalingClient;
import com.ss.easymedia.webrtc.client.ZlmWebRtcUriPolicy;
import com.ss.easymedia.webrtc.domain.WebRtcGatewayResponse;
import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.domain.WebRtcMediaTypes;
import com.ss.easymedia.webrtc.domain.WebRtcOperation;
import com.ss.easymedia.webrtc.domain.WebRtcSessionRecord;
import com.ss.easymedia.webrtc.domain.WebRtcSessionState;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import com.ss.easymedia.webrtc.domain.ZlmWebRtcResponse;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;
import com.ss.easymedia.webrtc.id.WebRtcSessionIdGenerator;
import com.ss.easymedia.webrtc.metrics.WebRtcSessionMetrics;
import com.ss.easymedia.webrtc.repository.WebRtcSessionRepository;
import com.ss.easymedia.webrtc.security.WebRtcAccessPolicy;
import com.ss.easymedia.webrtc.security.WebRtcIdentityProvider;
import com.ss.easymedia.webrtc.security.WebRtcRateLimiter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 单节点 WebRTC 会话服务。
 */
public class DefaultWebRtcSessionService implements WebRtcSessionService {

    /** Redis 写入冲突时生成会话 ID 的最大尝试次数。 */
    private static final int SESSION_ID_ATTEMPTS = 3;
    /** app 与 stream 参数允许的字符和长度。 */
    private static final Pattern STREAM_PART_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1,128}");
    /** 对外会话 ID 的固定格式。 */
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{32}");
    /** 上游删除操作使用的空请求头。 */
    private static final HttpHeaders EMPTY_HEADERS = new HttpHeaders();
    /** 上游删除操作使用的空请求体。 */
    private static final byte[] EMPTY_BODY = new byte[0];

    /** 从当前请求解析租户和认证主体。 */
    private final WebRtcIdentityProvider identityProvider;
    /** 校验创建与会话操作权限。 */
    private final WebRtcAccessPolicy accessPolicy;
    /** 控制信令接口调用频率。 */
    private final WebRtcRateLimiter rateLimiter;
    /** 与 ZLM 交换 SDP 和会话操作的信令客户端。 */
    private final ZlmWebRtcSignalingClient client;
    /** 验证上游会话资源地址。 */
    private final ZlmWebRtcUriPolicy uriPolicy;
    /** 保存受管 WebRTC 会话状态。 */
    private final WebRtcSessionRepository repository;
    /** 生成不可预测的公开会话 ID。 */
    private final WebRtcSessionIdGenerator idGenerator;
    /** WebRTC 功能和生命周期配置。 */
    private final WebRtcProperties properties;
    /** 为持久化时间和重试调度提供统一时钟。 */
    private final Clock clock;
    /** 记录创建、更新和清理结果的指标。 */
    private final WebRtcSessionMetrics metrics;

    /**
     * 创建编排 WebRTC 会话生命周期的服务。

     *
     * @param identityProvider 身份提供器
     * @param accessPolicy 访问控制策略
     * @param rateLimiter 请求限流器
     * @param client ZLM WebRTC 信令客户端
     * @param uriPolicy 外部 URI 安全策略
     * @param repository 会话仓储
     * @param idGenerator 安全会话 ID 生成器
     * @param properties 模块配置
     * @param clock 用于获取当前时间的时钟
     * @param metrics 指标记录器
     */
    public DefaultWebRtcSessionService(WebRtcIdentityProvider identityProvider,
                                       WebRtcAccessPolicy accessPolicy,
                                       WebRtcRateLimiter rateLimiter,
                                       ZlmWebRtcSignalingClient client,
                                       ZlmWebRtcUriPolicy uriPolicy,
                                       WebRtcSessionRepository repository,
                                       WebRtcSessionIdGenerator idGenerator,
                                       WebRtcProperties properties,
                                       Clock clock,
                                       WebRtcSessionMetrics metrics) {
        this.identityProvider = identityProvider;
        this.accessPolicy = accessPolicy;
        this.rateLimiter = rateLimiter;
        this.client = client;
        this.uriPolicy = uriPolicy;
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.clock = clock;
        this.metrics = metrics;
    }

    /**
     * 创建 WHIP 或 WHEP 会话，并在支持受管会话的上游中保存对应关系。
     *
     * @return 可直接返回给客户端的 SDP Answer 响应
     */
    @Override
    public WebRtcGatewayResponse create(WebRtcSessionType type, String app, String stream,
                                        HttpHeaders requestHeaders, byte[] offerSdp, String clientIp) {
        long started = System.nanoTime();
        String outcome = "success";
        try {
            validateCreate(type, app, stream, offerSdp);
            WebRtcIdentity identity = identityProvider.current(clientIp);
            accessPolicy.authorizeCreate(identity, type, app, stream);
            checkRateLimit(type.getOperation(), identity, clientIp);

            ZlmWebRtcResponse upstream;
            try {
                upstream = client.create(type, app, stream,
                        Objects.requireNonNullElseGet(requestHeaders, HttpHeaders::new), offerSdp);
            } catch (RuntimeException exception) {
                outcome = "upstream_error";
                throw WebRtcSessionException.badGateway(
                        "WEBRTC_UPSTREAM_CREATE_FAILED", "Unable to create ZLM WebRTC session");
            }
            if (upstream.status().value() != HttpStatus.CREATED.value()) {
                outcome = "upstream_error";
                return gatewayResponseWithoutLocation(upstream);
            }

            if (!upstream.managedSession()) {
                MediaType contentType = upstream.headers().getContentType();
                if (contentType == null || !WebRtcMediaTypes.APPLICATION_SDP.isCompatibleWith(contentType)) {
                    outcome = "upstream_error";
                    throw WebRtcSessionException.badGateway(
                            "WEBRTC_UPSTREAM_CONTENT_TYPE_INVALID", "ZLM did not return application/sdp");
                }
                return gatewayResponseWithoutLocation(upstream);
            }

            URI upstreamLocation = requireTrustedLocation(upstream);
            MediaType contentType = upstream.headers().getContentType();
            if (contentType == null || !WebRtcMediaTypes.APPLICATION_SDP.isCompatibleWith(contentType)) {
                bestEffortDelete(upstreamLocation);
                outcome = "upstream_error";
                throw WebRtcSessionException.badGateway(
                        "WEBRTC_UPSTREAM_CONTENT_TYPE_INVALID", "ZLM did not return application/sdp");
            }

            for (int attempt = 0; attempt < SESSION_ID_ATTEMPTS; attempt++) {
                String sessionId = idGenerator.generate();
                WebRtcSessionRecord record = buildRecord(
                        sessionId, type, identity, app, stream, upstreamLocation, upstream);
                try {
                    if (repository.create(record, properties.getSessionTtl())) {
                        return publicCreatedResponse(upstream, sessionId);
                    }
                } catch (RuntimeException exception) {
                    bestEffortDelete(upstreamLocation);
                    outcome = "storage_error";
                    throw WebRtcSessionException.serviceUnavailable(
                            "WEBRTC_SESSION_STORAGE_UNAVAILABLE");
                }
            }

            bestEffortDelete(upstreamLocation);
            outcome = "storage_error";
            throw WebRtcSessionException.serviceUnavailable("WEBRTC_SESSION_ID_COLLISION");
        } catch (WebRtcSessionException exception) {
            if ("success".equals(outcome)) {
                outcome = "rejected";
            }
            throw exception;
        } finally {
            recordCreateMetric(type, outcome, started);
        }
    }

    /**
     * 将 Trickle ICE SDP Fragment 转发到指定的受管上游会话。
     *
     * @return 上游返回的会话更新响应
     */
    @Override
    public WebRtcGatewayResponse patch(String sessionId, HttpHeaders requestHeaders,
                                       byte[] sdpFragment, String clientIp) {
        long started = System.nanoTime();
        String outcome = "success";
        try {
            validateSessionId(sessionId);
            validateSdpBody(sdpFragment);
            WebRtcIdentity identity = identityProvider.current(clientIp);
            WebRtcSessionRecord snapshot = withSessionLock(sessionId, () -> {
                WebRtcSessionRecord record = repository.find(sessionId)
                        .orElseThrow(() -> WebRtcSessionException.notFound("WEBRTC_SESSION_NOT_FOUND"));
                accessPolicy.authorizeSession(identity, record);
                checkRateLimit(WebRtcOperation.PATCH, identity, clientIp);
                if (record.getState() != WebRtcSessionState.ACTIVE) {
                    throw WebRtcSessionException.conflict(
                            "WEBRTC_SESSION_CLOSING", "WebRTC session is closing");
                }
                return record;
            });

            ZlmWebRtcResponse upstream;
            try {
                upstream = client.exchange(URI.create(snapshot.getUpstreamLocation()),
                        HttpMethod.PATCH,
                        Objects.requireNonNullElseGet(requestHeaders, HttpHeaders::new), sdpFragment);
            } catch (RuntimeException exception) {
                outcome = "upstream_error";
                throw WebRtcSessionException.badGateway(
                        "WEBRTC_UPSTREAM_PATCH_FAILED", "Unable to update ZLM WebRTC session");
            }

            if (upstream.status().is2xxSuccessful() && upstream.headers().getETag() != null) {
                persistUpdatedEtag(sessionId, upstream.headers().getETag());
            }
            if (!upstream.status().is2xxSuccessful()) {
                outcome = "upstream_error";
            }
            return gatewayResponseWithoutLocation(upstream);
        } catch (WebRtcSessionException exception) {
            if ("success".equals(outcome)) {
                outcome = "rejected";
            }
            throw exception;
        } finally {
            recordMutationMetric(WebRtcOperation.PATCH, outcome, started);
        }
    }

    /**
     * 关闭会话；暂时无法删除时将其加入补偿重试队列。
     */
    @Override
    public void delete(String sessionId, String clientIp) {
        long started = System.nanoTime();
        String outcome = "missing";
        try {
            validateSessionId(sessionId);
            WebRtcIdentity identity = identityProvider.current(clientIp);
            checkRateLimit(WebRtcOperation.DELETE, identity, clientIp);
            outcome = withSessionLock(sessionId, () -> {
                Optional<WebRtcSessionRecord> optional = repository.find(sessionId);
                if (optional.isEmpty()) {
                    return "missing";
                }
                WebRtcSessionRecord record = optional.get();
                accessPolicy.authorizeSession(identity, record);
                if (record.getState() == WebRtcSessionState.CLOSING) {
                    return "already_closing";
                }
                record.setState(WebRtcSessionState.CLOSING);
                record.setUpdatedAt(clock.millis());
                record.setNextDeleteRetryAt(clock.millis());
                repository.save(record, properties.getClosingTtl());
                repository.scheduleClosingRetry(
                        record.getSessionId(), Instant.ofEpochMilli(record.getNextDeleteRetryAt()));
                return deleteUpstream(record);
            });
        } finally {
            recordMutationMetric(WebRtcOperation.DELETE, outcome, started);
        }
    }

    /**
     * 执行一次已关闭会话的上游删除补偿。
     */
    @Override
    public void retryDelete(String sessionId) {
        validateSessionId(sessionId);
        String outcome = withSessionLock(sessionId, () -> {
            Optional<WebRtcSessionRecord> optional = repository.find(sessionId);
            if (optional.isEmpty()) {
                repository.removeClosingRetry(sessionId);
                return "missing";
            }
            WebRtcSessionRecord current = optional.get();
            if (current.getState() != WebRtcSessionState.CLOSING) {
                repository.removeClosingRetry(sessionId);
                return "missing";
            }
            if (current.getNextDeleteRetryAt() > clock.millis()) {
                return "not_due";
            }
            return deleteUpstream(current);
        });
        safeCleanupMetric(outcome);
    }

    /**
     * 在会话仍处于活动状态时保存上游返回的最新 ETag。
     */
    private void persistUpdatedEtag(String sessionId, String etag) {
        withSessionLock(sessionId, () -> {
            Optional<WebRtcSessionRecord> optional = repository.find(sessionId);
            if (optional.isPresent() && optional.get().getState() == WebRtcSessionState.ACTIVE) {
                WebRtcSessionRecord record = optional.get();
                record.setUpstreamEtag(etag);
                record.setUpdatedAt(clock.millis());
                repository.savePreservingTtl(record);
            }
            return null;
        });
    }

    /**
     * 删除上游会话，并按 HTTP 状态决定成功、重试或永久失败。
     *
     * @return 用于指标记录和清理调度的删除结果
     */
    private String deleteUpstream(WebRtcSessionRecord record) {
        URI upstreamLocation;
        try {
            upstreamLocation = URI.create(record.getUpstreamLocation());
        } catch (IllegalArgumentException exception) {
            repository.removeClosingRetry(record.getSessionId());
            return "permanent_failure";
        }

        ZlmWebRtcResponse upstream;
        try {
            upstream = client.exchange(upstreamLocation, HttpMethod.DELETE, EMPTY_HEADERS, EMPTY_BODY);
        } catch (RuntimeException exception) {
            scheduleDeleteRetry(record);
            return "retry";
        }

        int status = upstream.status().value();
        if (upstream.status().is2xxSuccessful()
                || status == HttpStatus.NOT_FOUND.value()
                || status == HttpStatus.GONE.value()) {
            repository.delete(record.getSessionId());
            return "success";
        }
        if (status == HttpStatus.REQUEST_TIMEOUT.value()
                || status == HttpStatus.TOO_MANY_REQUESTS.value()
                || upstream.status().is5xxServerError()) {
            scheduleDeleteRetry(record);
            return "retry";
        }
        repository.removeClosingRetry(record.getSessionId());
        return "permanent_failure";
    }

    /**
     * 使用有上限的指数退避安排下一次删除补偿。
     */
    private void scheduleDeleteRetry(WebRtcSessionRecord record) {
        int retryCount = record.getDeleteRetryCount() + 1;
        long delaySeconds = Math.min(60, 1L << Math.min(retryCount, 6));
        long nextRetryAt = clock.millis() + Duration.ofSeconds(delaySeconds).toMillis();
        record.setDeleteRetryCount(retryCount);
        record.setNextDeleteRetryAt(nextRetryAt);
        record.setUpdatedAt(clock.millis());
        repository.save(record, properties.getClosingTtl());
        repository.scheduleClosingRetry(record.getSessionId(), Instant.ofEpochMilli(nextRetryAt));
    }

    /**
     * 在分布式会话锁中执行操作，并将仓储故障统一转换为服务不可用。
     */
    private <T> T withSessionLock(String sessionId, Supplier<T> action) {
        try {
            return repository.withSessionLock(sessionId, action);
        } catch (WebRtcSessionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw WebRtcSessionException.serviceUnavailable(
                    "WEBRTC_SESSION_STORAGE_UNAVAILABLE");
        }
    }

    /**
     * 检查操作限流，并屏蔽限流基础设施的实现异常。
     */
    private void checkRateLimit(WebRtcOperation operation, WebRtcIdentity identity, String clientIp) {
        try {
            rateLimiter.check(operation, identity, clientIp);
        } catch (WebRtcSessionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw WebRtcSessionException.serviceUnavailable("WEBRTC_RATE_LIMIT_UNAVAILABLE");
        }
    }

    /**
     * 校验创建会话所需的类型、流标识和 SDP Offer。
     */
    private void validateCreate(WebRtcSessionType type, String app, String stream, byte[] offerSdp) {
        if (type == null) {
            throw WebRtcSessionException.badRequest("WEBRTC_TYPE_REQUIRED", "Session type is required");
        }
        validateStreamPart(app);
        validateStreamPart(stream);
        validateSdpBody(offerSdp);
    }

    /**
     * 校验 SDP 请求体非空且未超过配置大小限制。
     */
    private void validateSdpBody(byte[] body) {
        if (body == null || body.length == 0) {
            throw WebRtcSessionException.badRequest("WEBRTC_SDP_REQUIRED", "SDP body is required");
        }
        if (body.length > properties.getMaxSdpBytes()) {
            throw new WebRtcSessionException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "WEBRTC_SDP_TOO_LARGE", "SDP body exceeds configured limit");
        }
    }

    /**
     * 校验 app 或 stream 路径片段，阻止路径穿越和非协议字符。
     */
    private void validateStreamPart(String value) {
        if (value == null || !STREAM_PART_PATTERN.matcher(value).matches() || value.contains("..")) {
            throw WebRtcSessionException.badRequest(
                    "WEBRTC_STREAM_INVALID", "app and stream contain unsupported characters");
        }
    }

    /**
     * 校验公开会话 ID 是否符合生成器约定的固定格式。
     */
    private void validateSessionId(String sessionId) {
        if (sessionId == null || !SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw WebRtcSessionException.badRequest(
                    "WEBRTC_SESSION_ID_INVALID", "WebRTC session ID is invalid");
        }
    }

    /**
     * 提取并校验 ZLM 返回的受管会话 Location。
     *
     * @return 可安全用于后续 PATCH 和 DELETE 的上游地址
     */
    private URI requireTrustedLocation(ZlmWebRtcResponse upstream) {
        URI location = upstream.headers().getLocation();
        if (location == null) {
            throw WebRtcSessionException.badGateway(
                    "WEBRTC_UPSTREAM_LOCATION_MISSING", "ZLM did not return a session Location");
        }
        try {
            return uriPolicy.requireTrustedLocation(upstream.requestUri(), location);
        } catch (IllegalArgumentException exception) {
            throw WebRtcSessionException.badGateway(
                    "WEBRTC_UPSTREAM_LOCATION_INVALID", "ZLM returned an untrusted session Location");
        }
    }

    /**
     * 将创建结果转换为 Redis 中持久化的会话快照。
     */
    private WebRtcSessionRecord buildRecord(String sessionId, WebRtcSessionType type,
                                             WebRtcIdentity identity, String app, String stream,
                                             URI upstreamLocation, ZlmWebRtcResponse upstream) {
        long now = clock.millis();
        return new WebRtcSessionRecord(
                sessionId, type, WebRtcSessionState.ACTIVE,
                identity.tenantId(), identity.subject(), app, stream, "local-zlm",
                upstreamLocation.toString(), upstream.headers().getETag(),
                now, now, 0L, 0);
    }

    /**
     * 用本服务的公开会话地址替换受管上游 Location。
     */
    private WebRtcGatewayResponse publicCreatedResponse(ZlmWebRtcResponse upstream, String sessionId) {
        HttpHeaders headers = copyHeaders(upstream.headers());
        headers.setLocation(URI.create(properties.getPublicSessionBasePath() + "/" + sessionId));
        return new WebRtcGatewayResponse(upstream.status(), headers, upstream.body());
    }

    /**
     * 返回不暴露上游会话资源地址的网关响应。
     */
    private WebRtcGatewayResponse gatewayResponseWithoutLocation(ZlmWebRtcResponse upstream) {
        HttpHeaders headers = copyHeaders(upstream.headers());
        headers.remove(HttpHeaders.LOCATION);
        return new WebRtcGatewayResponse(upstream.status(), headers, upstream.body());
    }

    /**
     * 创建独立响应头副本，避免后续修改上游对象。
     */
    private HttpHeaders copyHeaders(HttpHeaders source) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(source);
        return headers;
    }

    /**
     * 创建后的本地持久化失败时尝试删除已创建的上游会话。
     */
    private void bestEffortDelete(URI upstreamLocation) {
        try {
            client.exchange(upstreamLocation, HttpMethod.DELETE, EMPTY_HEADERS, EMPTY_BODY);
        } catch (RuntimeException ignored) {
            // ZLM 会根据 ICE/DTLS 超时回收无法补偿删除的会话。
        }
    }

    /**
     * 安全记录会话创建耗时，指标故障不影响信令请求。
     */
    private void recordCreateMetric(WebRtcSessionType type, String outcome, long startedNanos) {
        if (type == null) {
            return;
        }
        try {
            metrics.recordCreate(type, outcome, Duration.ofNanos(System.nanoTime() - startedNanos));
        } catch (RuntimeException ignored) {
            // 指标故障不能影响信令请求。
        }
    }

    /**
     * 安全记录 PATCH 或 DELETE 操作的耗时和结果。
     */
    private void recordMutationMetric(WebRtcOperation operation, String outcome, long startedNanos) {
        try {
            metrics.recordMutation(operation, outcome,
                    Duration.ofNanos(System.nanoTime() - startedNanos));
        } catch (RuntimeException ignored) {
            // 指标故障不能影响信令请求。
        }
    }

    /**
     * 安全记录后台删除补偿的执行结果。
     */
    private void safeCleanupMetric(String outcome) {
        try {
            metrics.incrementCleanupRetry(outcome);
        } catch (RuntimeException ignored) {
            // 指标故障不能影响关闭补偿。
        }
    }


}
