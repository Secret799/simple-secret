package com.ss.idempotent.aspect;

import com.ss.idempotent.annotation.RepeatSubmit;
import com.ss.idempotent.exception.RepeatSubmitException;
import com.ss.idempotent.key.IdempotencyKeyGenerator;
import com.ss.idempotent.store.IdempotencyStore;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/** 在方法调用栈内管理重复提交租约的 Spring AOP 切面。 */
@Aspect
public final class RepeatSubmitAspect {

    private static final long MINIMUM_INTERVAL_MILLIS = 1000L;

    private final IdempotencyStore store;
    private final IdempotencyKeyGenerator keyGenerator;
    private final MessageSource messageSource;

    /**
     * 创建重复提交切面。
     *
     * @param store 原子租约存储
     * @param keyGenerator key 生成器
     * @param messageSource 国际化消息源
     */
    public RepeatSubmitAspect(
            IdempotencyStore store,
            IdempotencyKeyGenerator keyGenerator,
            MessageSource messageSource) {
        this.store = Objects.requireNonNull(store, "store");
        this.keyGenerator = Objects.requireNonNull(keyGenerator, "keyGenerator");
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
    }

    /**
     * 获取租约后执行目标方法。
     *
     * @param joinPoint 目标调用
     * @param repeatSubmit 方法注解
     * @return 目标方法结果
     * @throws Throwable 目标方法或存储异常
     */
    @Around("@annotation(repeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatSubmit repeatSubmit) throws Throwable {
        Duration ttl = ttl(repeatSubmit);
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        HttpServletRequest request = currentRequest();
        String key = keyGenerator.generate(method, joinPoint.getArgs(), request);
        String owner = UUID.randomUUID().toString();

        if (!store.tryAcquire(key, owner, ttl)) {
            throw new RepeatSubmitException(resolveMessage(repeatSubmit.message()));
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable failure) {
            if (repeatSubmit.releaseOnException()) {
                try {
                    store.release(key, owner);
                } catch (RuntimeException releaseFailure) {
                    failure.addSuppressed(releaseFailure);
                }
            }
            throw failure;
        }
    }

    private static Duration ttl(RepeatSubmit repeatSubmit) {
        long millis = repeatSubmit.timeUnit().toMillis(repeatSubmit.interval());
        if (millis < MINIMUM_INTERVAL_MILLIS) {
            throw new RepeatSubmitException(
                    "Repeat submit interval must be at least 1 second.");
        }
        return Duration.ofMillis(millis);
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        throw new RepeatSubmitException(
                "Repeat submit protection requires an active Servlet request.");
    }

    private String resolveMessage(String message) {
        if (message != null && message.length() > 2
                && message.startsWith("{") && message.endsWith("}")) {
            String code = message.substring(1, message.length() - 1);
            return messageSource.getMessage(
                    code, null, message, LocaleContextHolder.getLocale());
        }
        return message;
    }
}
