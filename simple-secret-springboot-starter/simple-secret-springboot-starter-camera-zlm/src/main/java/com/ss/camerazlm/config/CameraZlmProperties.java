package com.ss.camerazlm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Camera SDK 到 ZLM 的有界转推配置。
 *
 * @author junpzx
 * @since 2026-08-13
 */
@ConfigurationProperties("simple-secret.camera-zlm")
public class CameraZlmProperties {

    /** 队列允许配置的最大帧数。 */
    private static final int MAX_QUEUE_CAPACITY = 10_000;
    /** 原生适配层允许配置的最大单帧字节数。 */
    private static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;
    /** 单路流允许配置的最大待处理字节数。 */
    private static final long MAX_BUFFERED_BYTES = 1024L * 1024L * 1024L;

    /** 每路流在内存中最多等待消费的帧数。 */
    private int queueCapacity = 150;

    /** 单帧允许进入适配层的最大字节数。 */
    private int maxFrameBytes = 4 * 1024 * 1024;

    /** 单路排队和推送中的最大总字节数。 */
    private long maxBufferedBytes = 32L * 1024L * 1024L;

    /** 关闭单路消费线程的最大等待时长。 */
    private Duration closeTimeout = Duration.ofSeconds(5);

    /**
     * 获取每路流队列容量。
     *
     * @return 正整数队列容量
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * 设置每路流队列容量。
     *
     * @param queueCapacity 正整数队列容量
     */
    public void setQueueCapacity(int queueCapacity) {
        if (queueCapacity <= 0 || queueCapacity > MAX_QUEUE_CAPACITY) {
            throw new IllegalArgumentException("queueCapacity must be between 1 and 10000");
        }
        this.queueCapacity = queueCapacity;
    }

    /**
     * 获取单帧最大字节数。
     *
     * @return 单帧最大字节数
     */
    public int getMaxFrameBytes() {
        return maxFrameBytes;
    }

    /**
     * 设置单帧最大字节数。
     *
     * @param maxFrameBytes 正整数单帧字节上限
     */
    public void setMaxFrameBytes(int maxFrameBytes) {
        if (maxFrameBytes <= 0 || maxFrameBytes > MAX_FRAME_BYTES) {
            throw new IllegalArgumentException("maxFrameBytes must be between 1 and 16777216");
        }
        this.maxFrameBytes = maxFrameBytes;
    }

    /**
     * 获取单路待处理总字节预算。
     *
     * @return 单路总字节预算
     */
    public long getMaxBufferedBytes() {
        return maxBufferedBytes;
    }

    /**
     * 设置单路待处理总字节预算。
     *
     * @param maxBufferedBytes 正整数总字节预算
     */
    public void setMaxBufferedBytes(long maxBufferedBytes) {
        if (maxBufferedBytes <= 0L || maxBufferedBytes > MAX_BUFFERED_BYTES) {
            throw new IllegalArgumentException(
                    "maxBufferedBytes must be between 1 and 1073741824");
        }
        this.maxBufferedBytes = maxBufferedBytes;
    }

    /**
     * 获取关闭等待时长。
     *
     * @return 正数等待时长
     */
    public Duration getCloseTimeout() {
        return closeTimeout;
    }

    /**
     * 设置关闭等待时长。
     *
     * @param closeTimeout 正数等待时长
     */
    public void setCloseTimeout(Duration closeTimeout) {
        this.closeTimeout = closeTimeout;
    }
}
