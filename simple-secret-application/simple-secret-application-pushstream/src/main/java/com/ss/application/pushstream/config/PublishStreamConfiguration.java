package com.ss.application.pushstream.config;

import com.ss.application.pushstream.process.DefaultProcessLauncher;
import com.ss.application.pushstream.process.FfmpegCommandFactory;
import com.ss.application.pushstream.process.FfmpegProcessManager;
import com.ss.application.pushstream.process.ManagedStreamProcesses;
import com.ss.application.pushstream.process.ProcessLauncher;
import com.ss.application.pushstream.scan.MediaFileScanner;
import com.ss.application.pushstream.service.MediaServerClient;
import com.ss.application.pushstream.service.PublishStreamScheduler;
import com.ss.application.pushstream.service.PublishStreamService;
import com.ss.application.pushstream.service.ZlmMediaServerClient;
import com.ss.zlm4j.service.IZlmMediaService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Clock;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 本地媒体文件推流应用配置。
 *
 * @author junpzx
 * @since 2026-08-12
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PublishStreamProperties.class)
public class PublishStreamConfiguration {

    /**
     * 创建外部进程启动器。
     *
     * @return 默认进程启动器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "simple-secret.publish-stream", name = "enabled", havingValue = "true")
    public ProcessLauncher processLauncher() {
        return new DefaultProcessLauncher();
    }

    /**
     * 创建 FFmpeg 进程管理器。
     *
     * @param properties 推流配置
     * @param processLauncher 进程启动器
     * @return 受管 FFmpeg 进程
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "simple-secret.publish-stream", name = "enabled", havingValue = "true")
    public FfmpegProcessManager ffmpegProcessManager(PublishStreamProperties properties,
                                                     ProcessLauncher processLauncher) {
        FfmpegCommandFactory commandFactory = new FfmpegCommandFactory(properties.getFfmpegExecutable(),
                properties.getRtspHost(), properties.getRtspPort(), properties.getApp());
        return new FfmpegProcessManager(commandFactory, processLauncher,
                properties.getMaxConcurrentStreams(), properties.getShutdownTimeout());
    }

    /**
     * 创建 ZLMediaKit 查询客户端。
     *
     * @param mediaService zlm4j 媒体服务
     * @return 媒体服务器查询客户端
     */
    @Bean
    @ConditionalOnMissingBean(MediaServerClient.class)
    @ConditionalOnProperty(prefix = "simple-secret.publish-stream", name = "enabled", havingValue = "true")
    public MediaServerClient mediaServerClient(IZlmMediaService mediaService) {
        return new ZlmMediaServerClient(mediaService);
    }

    /**
     * 创建推流应用服务。
     *
     * @param properties 推流配置
     * @param processes 受管 FFmpeg 进程
     * @param mediaServerClient 媒体服务器客户端
     * @return 推流应用服务
     */
    @Bean
    @ConditionalOnProperty(prefix = "simple-secret.publish-stream", name = "enabled", havingValue = "true")
    public PublishStreamService publishStreamService(PublishStreamProperties properties,
                                                     ManagedStreamProcesses processes,
                                                     MediaServerClient mediaServerClient) {
        MediaFileScanner scanner = new MediaFileScanner(properties.getScanDirectory(),
                properties.getAllowedSuffixes(), properties.isRecursive(), properties.getMaxScannedFiles());
        return new PublishStreamService(scanner, processes, mediaServerClient,
                properties.getApp(), Clock.systemUTC());
    }

    /**
     * 创建单线程推流扫描执行器。
     *
     * @return 单线程调度器
     */
    @Bean(name = "publishStreamExecutor", destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "simple-secret.publish-stream", name = "enabled", havingValue = "true")
    public ScheduledExecutorService publishStreamExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "simple-secret-publish-stream-scan");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    /**
     * 创建推流扫描生命周期组件。
     *
     * @param publishStreamService 推流应用服务
     * @param publishStreamExecutor 单线程调度器
     * @param properties 推流配置
     * @return 推流扫描调度器
     */
    @Bean
    @ConditionalOnProperty(prefix = "simple-secret.publish-stream", name = "enabled", havingValue = "true")
    public PublishStreamScheduler publishStreamScheduler(PublishStreamService publishStreamService,
                                                         @Qualifier("publishStreamExecutor")
                                                         ScheduledExecutorService publishStreamExecutor,
                                                         PublishStreamProperties properties) {
        return new PublishStreamScheduler(publishStreamService,
                publishStreamExecutor, properties.getScanInterval());
    }
}
