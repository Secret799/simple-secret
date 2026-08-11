package com.ss.core.config;

import com.ss.core.concurrent.LoggingScheduledThreadPoolExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/** 可选任务执行器与调度器自动配置。 */
@AutoConfiguration(after = SimpleSecretCoreAutoConfiguration.class)
public class CoreExecutorAutoConfiguration {

    /**
     * 创建应用任务执行器。
     *
     * @param properties core 配置
     * @return 任务执行器
     */
    @Bean(name = "simpleSecretTaskExecutor")
    @ConditionalOnMissingBean(name = "simpleSecretTaskExecutor")
    @ConditionalOnProperty(
            prefix = "simple-secret.core.task-executor", name = "enabled", havingValue = "true")
    public ThreadPoolTaskExecutor simpleSecretTaskExecutor(CoreProperties properties) {
        CoreProperties.TaskExecutor executorProperties = properties.getTaskExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(executorProperties.getCorePoolSize());
        executor.setMaxPoolSize(executorProperties.getMaxPoolSize());
        executor.setQueueCapacity(executorProperties.getQueueCapacity());
        executor.setKeepAliveSeconds(Math.toIntExact(executorProperties.getKeepAlive().toSeconds()));
        executor.setThreadNamePrefix(executorProperties.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /**
     * 创建定时任务调度器。
     *
     * @param properties core 配置
     * @return 调度器
     */
    @Bean(name = "simpleSecretScheduledExecutorService", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "simpleSecretScheduledExecutorService")
    @ConditionalOnProperty(prefix = "simple-secret.core.scheduler", name = "enabled", havingValue = "true")
    public ScheduledExecutorService simpleSecretScheduledExecutorService(CoreProperties properties) {
        CoreProperties.Scheduler schedulerProperties = properties.getScheduler();
        CustomizableThreadFactory threadFactory =
                new CustomizableThreadFactory(schedulerProperties.getThreadNamePrefix());
        threadFactory.setDaemon(true);
        LoggingScheduledThreadPoolExecutor executor = new LoggingScheduledThreadPoolExecutor(
                schedulerProperties.getPoolSize(),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
}
