package com.ss.idempotent.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 标记需要在指定时间窗口内拒绝重复执行的 Spring Bean 方法。
 */
@Inherited
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RepeatSubmit {

    /**
     * 重复提交保护窗口。
     *
     * @return 时间数值
     */
    int interval() default 5000;

    /**
     * 保护窗口的时间单位。
     *
     * @return 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * 重复提交消息；使用 {@code {code}} 时通过 Spring MessageSource 解析。
     *
     * @return 错误消息或消息码
     */
    String message() default "{repeat.submit.message}";

    /**
     * 被调用方法抛出异常时是否释放本次租约。
     *
     * @return true 表示允许异常请求立即重试
     */
    boolean releaseOnException() default true;
}
