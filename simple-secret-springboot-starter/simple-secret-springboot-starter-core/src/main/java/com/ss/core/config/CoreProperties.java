package com.ss.core.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Core starter 配置。
 *
 * <p>所有会创建线程或改变框架行为的功能默认关闭。</p>
 */
@ConfigurationProperties("simple-secret.core")
public class CoreProperties implements InitializingBean {

    private final Project project = new Project();
    private final TaskExecutor taskExecutor = new TaskExecutor();
    private final Scheduler scheduler = new Scheduler();
    private final Async async = new Async();
    private final Validation validation = new Validation();

    /** 返回项目元数据配置。 */
    public Project getProject() {
        return project;
    }

    /** 返回任务执行器配置。 */
    public TaskExecutor getTaskExecutor() {
        return taskExecutor;
    }

    /** 返回调度器配置。 */
    public Scheduler getScheduler() {
        return scheduler;
    }

    /** 返回异步配置。 */
    public Async getAsync() {
        return async;
    }

    /** 返回校验配置。 */
    public Validation getValidation() {
        return validation;
    }

    /** 校验显式启用功能的配置。 */
    @Override
    public void afterPropertiesSet() {
        if (taskExecutor.enabled) {
            requirePositive(taskExecutor.corePoolSize, "task-executor.core-pool-size");
            if (taskExecutor.maxPoolSize < taskExecutor.corePoolSize) {
                throw invalid("task-executor.max-pool-size");
            }
            if (taskExecutor.queueCapacity < 0) {
                throw invalid("task-executor.queue-capacity");
            }
            Duration keepAlive = taskExecutor.keepAlive;
            if (keepAlive == null
                    || keepAlive.isNegative()
                    || keepAlive.toSeconds() > Integer.MAX_VALUE) {
                throw invalid("task-executor.keep-alive");
            }
            requireText(taskExecutor.threadNamePrefix, "task-executor.thread-name-prefix");
        }
        if (scheduler.enabled) {
            requirePositive(scheduler.poolSize, "scheduler.pool-size");
            requireText(scheduler.threadNamePrefix, "scheduler.thread-name-prefix");
        }
    }

    private static void requirePositive(int value, String property) {
        if (value <= 0) {
            throw invalid(property);
        }
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw invalid(property);
        }
    }

    private static IllegalArgumentException invalid(String property) {
        return new IllegalArgumentException("simple-secret.core." + property + " 配置无效");
    }

    /** 项目展示元数据。 */
    public static class Project {
        private String name;
        private String description;
        private String version;
        private Integer copyrightYear;

        /** 返回项目名称。 */
        public String getName() {
            return name;
        }

        /** 设置项目名称。 */
        public void setName(String name) {
            this.name = name;
        }

        /** 返回项目描述。 */
        public String getDescription() {
            return description;
        }

        /** 设置项目描述。 */
        public void setDescription(String description) {
            this.description = description;
        }

        /** 返回项目版本。 */
        public String getVersion() {
            return version;
        }

        /** 设置项目版本。 */
        public void setVersion(String version) {
            this.version = version;
        }

        /** 返回版权年份。 */
        public Integer getCopyrightYear() {
            return copyrightYear;
        }

        /** 设置版权年份。 */
        public void setCopyrightYear(Integer copyrightYear) {
            this.copyrightYear = copyrightYear;
        }
    }

    /** 应用任务执行器配置。 */
    public static class TaskExecutor {
        private boolean enabled;
        private int corePoolSize = 4;
        private int maxPoolSize = 8;
        private int queueCapacity = 256;
        private Duration keepAlive = Duration.ofSeconds(60);
        private String threadNamePrefix = "ss-task-";

        /** 返回是否启用。 */
        public boolean isEnabled() {
            return enabled;
        }

        /** 设置是否启用。 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** 返回核心线程数。 */
        public int getCorePoolSize() {
            return corePoolSize;
        }

        /** 设置核心线程数。 */
        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        /** 返回最大线程数。 */
        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        /** 设置最大线程数。 */
        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        /** 返回队列容量。 */
        public int getQueueCapacity() {
            return queueCapacity;
        }

        /** 设置队列容量。 */
        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        /** 返回线程空闲存活时间。 */
        public Duration getKeepAlive() {
            return keepAlive;
        }

        /** 设置线程空闲存活时间。 */
        public void setKeepAlive(Duration keepAlive) {
            this.keepAlive = keepAlive;
        }

        /** 返回线程名前缀。 */
        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        /** 设置线程名前缀。 */
        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }
    }

    /** 定时任务调度器配置。 */
    public static class Scheduler {
        private boolean enabled;
        private int poolSize = 2;
        private String threadNamePrefix = "ss-schedule-";

        /** 返回是否启用。 */
        public boolean isEnabled() {
            return enabled;
        }

        /** 设置是否启用。 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** 返回线程池大小。 */
        public int getPoolSize() {
            return poolSize;
        }

        /** 设置线程池大小。 */
        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }

        /** 返回线程名前缀。 */
        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        /** 设置线程名前缀。 */
        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }
    }

    /** Spring 异步方法配置。 */
    public static class Async {
        private boolean enabled;

        /** 返回是否启用。 */
        public boolean isEnabled() {
            return enabled;
        }

        /** 设置是否启用。 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** Bean Validation 配置。 */
    public static class Validation {
        private boolean failFast;

        /** 返回是否启用快速失败。 */
        public boolean isFailFast() {
            return failFast;
        }

        /** 设置是否启用快速失败。 */
        public void setFailFast(boolean failFast) {
            this.failFast = failFast;
        }
    }
}
