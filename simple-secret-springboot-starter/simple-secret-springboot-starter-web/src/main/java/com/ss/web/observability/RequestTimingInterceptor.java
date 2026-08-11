package com.ss.web.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** 记录非敏感请求耗时的 WebMVC 拦截器。 */
public final class RequestTimingInterceptor implements AsyncHandlerInterceptor {

    private static final Log LOGGER = LogFactory.getLog(RequestTimingInterceptor.class);
    private static final String START_TIME_NANOS_ATTRIBUTE =
            RequestTimingInterceptor.class.getName() + ".startNanos";
    private static final String COMPLETION_LOGGED_ATTRIBUTE =
            RequestTimingInterceptor.class.getName() + ".completionLogged";
    private static final String UNMAPPED_ROUTE = "<unmapped>";

    private final long slowRequestThresholdNanos;

    /** 使用慢请求阈值创建拦截器。 */
    public RequestTimingInterceptor(Duration slowRequestThreshold) {
        Objects.requireNonNull(slowRequestThreshold, "slowRequestThreshold");
        if (slowRequestThreshold.isNegative()) {
            throw new IllegalArgumentException("Slow request threshold must be non-negative.");
        }
        this.slowRequestThresholdNanos = saturatingNanos(slowRequestThreshold);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (Boolean.TRUE.equals(request.getAttribute(COMPLETION_LOGGED_ATTRIBUTE))) {
            return true;
        }
        if (request.getAttribute(START_TIME_NANOS_ATTRIBUTE) == null) {
            request.setAttribute(START_TIME_NANOS_ATTRIBUTE, System.nanoTime());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception exception) {
        if (Boolean.TRUE.equals(request.getAttribute(COMPLETION_LOGGED_ATTRIBUTE))) {
            return;
        }
        Object startedAt = request.getAttribute(START_TIME_NANOS_ATTRIBUTE);
        try {
            if (!(startedAt instanceof Long startNanos)) {
                return;
            }

            long durationNanos = Math.max(0L, System.nanoTime() - startNanos);
            boolean slowRequest = durationNanos >= slowRequestThresholdNanos;
            if (slowRequest ? !LOGGER.isWarnEnabled() : !LOGGER.isDebugEnabled()) {
                return;
            }

            Object matchedPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String route = matchedPattern instanceof String pattern ? pattern : UNMAPPED_ROUTE;
            int responseStatus = response.getStatus();
            int effectiveStatus = exception != null && responseStatus < 400 ? 500 : responseStatus;
            String message = "method=" + request.getMethod()
                    + ", route=" + route
                    + ", status=" + effectiveStatus
                    + ", duration=" + TimeUnit.NANOSECONDS.toMillis(durationNanos) + "ms";

            if (slowRequest) {
                LOGGER.warn(message);
            } else {
                LOGGER.debug(message);
            }
        } finally {
            request.removeAttribute(START_TIME_NANOS_ATTRIBUTE);
            request.setAttribute(COMPLETION_LOGGED_ATTRIBUTE, Boolean.TRUE);
        }
    }

    private static long saturatingNanos(Duration duration) {
        long secondsNanos = TimeUnit.SECONDS.toNanos(duration.getSeconds());
        long nanos = duration.getNano();
        if (secondsNanos == Long.MAX_VALUE || secondsNanos > Long.MAX_VALUE - nanos) {
            return Long.MAX_VALUE;
        }
        return secondsNanos + nanos;
    }
}
