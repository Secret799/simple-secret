package com.ss.core.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/** 可选 Spring {@code @Async} 自动配置。 */
@EnableAsync(proxyTargetClass = true)
@AutoConfiguration(after = CoreExecutorAutoConfiguration.class)
@ConditionalOnMissingBean(AsyncConfigurer.class)
@ConditionalOnProperty(prefix = "simple-secret.core.async", name = "enabled", havingValue = "true")
public class CoreAsyncAutoConfiguration {

    private static final System.Logger LOGGER =
            System.getLogger(CoreAsyncAutoConfiguration.class.getName());

    /**
     * 创建异步执行配置。
     *
     * @param executor 名为 {@code simpleSecretTaskExecutor} 的执行器
     * @return 异步执行配置
    */
    @Bean
    public AsyncConfigurer simpleSecretAsyncConfigurer(
            @Qualifier("simpleSecretTaskExecutor") Executor executor) {
        return new AsyncConfigurer() {
            @Override
            public Executor getAsyncExecutor() {
                return executor;
            }

            @Override
            public org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
                    getAsyncUncaughtExceptionHandler() {
                return CoreAsyncAutoConfiguration::logAsyncFailure;
            }
        };
    }

    private static void logAsyncFailure(Throwable throwable, Method method, Object... arguments) {
        LOGGER.log(System.Logger.Level.ERROR,
                failureMessage(method),
                throwable);
    }

    static String failureMessage(Method method) {
        return "异步方法执行失败: " + method.toGenericString();
    }
}
