package com.ss.zlm4j.support;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;

/**
 * zlm4j 模块内部的 Spring 上下文工具。
 *
 * <p>替代迁移前依赖的 honeybee toolbox SpringUtils 与 hutool SpringUtil，
 * 仅提供本模块实际用到的静态入口：取 Bean 与发布事件。</p>
 */
@Component
public final class SpringUtils implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    private SpringUtils() {
    }

    @Override
    public void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
    }

    /**
     * 按类型获取 Bean，未找到时返回 {@code null}（与迁移前 hutool SpringUtil 行为一致）。
     *
     * @param type Bean 类型
     * @param <T>  Bean 类型
     * @return Bean，未找到时为 {@code null}
     */
    public static <T> T getBean(Class<T> type) {
        if (type == null) {
            return null;
        }
        try {
            return applicationContext.getBean(type);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按名称与类型获取 Bean。
     *
     * @param name Bean 名称
     * @param type Bean 类型
     * @param <T>  Bean 类型
     * @return Bean
     */
    public static <T> T getBean(String name, Class<T> type) {
        return applicationContext.getBean(name, type);
    }

    /**
     * 发布应用事件。
     *
     * @param event 事件
     */
    public static void publishEvent(Object event) {
        applicationContext.publishEvent(event);
    }

    /**
     * 获取 zlm4j 内置的调度执行器。
     *
     * @return 调度执行器
     */
    public static ScheduledExecutorService getSimpleSecretScheduledExecutor() {
        Objects.requireNonNull(applicationContext, "Spring application context is not initialized");
        return getBean("zlmScheduledExecutor", ScheduledExecutorService.class);
    }
}
