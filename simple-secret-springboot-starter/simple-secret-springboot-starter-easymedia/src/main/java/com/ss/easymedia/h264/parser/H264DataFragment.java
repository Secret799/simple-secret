package com.ss.easymedia.h264.parser;

import java.util.concurrent.CountDownLatch;

/**
 * H.264 数据片段
 *
 * @author junpzx
 */
class H264DataFragment {
    /** 片段数据副本。 */
    private final byte[] data;
    /** 接收时间戳。 */
    private final long timestamp;
    /** 解析完成信号。 */
    private final CountDownLatch processed = new CountDownLatch(1);
    /** 解析失败。 */
    private volatile RuntimeException failure;
    /** 处理后解析器仍保留的累积字节数。 */
    private volatile int retainedBytes;

    /**
     * 创建持有独立数据副本的 H.264 片段。
     *
     * @param data H.264 数据
     */
    public H264DataFragment(byte[] data) {
        this(data, true);
    }

    /**
     * 创建 H.264 片段。
     *
     * @param data H.264 数据
     * @param copyData 是否复制输入数据
     */
    H264DataFragment(byte[] data, boolean copyData) {
        byte[] source = data != null ? data : new byte[0];
        this.data = copyData ? source.clone() : source;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 获取片段数据。
     *
     * @return H.264 数据
     */
    public byte[] getData() {
        return data;
    }

    /**
     * 获取片段接收时间戳。
     *
     * @return 毫秒时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 发布解析结果并唤醒同步等待方。
     *
     * @param processingFailure 解析失败，成功时为 null
     * @param currentRetainedBytes 解析器当前保留的累积字节数
     */
    void complete(RuntimeException processingFailure, int currentRetainedBytes) {
        failure = processingFailure;
        retainedBytes = currentRetainedBytes;
        processed.countDown();
    }

    /**
     * 等待该片段解析完成。
     *
     * @return 解析器当前保留的累积字节数
     * @throws InterruptedException 等待线程被中断
     */
    int awaitProcessed() throws InterruptedException {
        processed.await();
        if (failure != null) {
            throw failure;
        }
        return retainedBytes;
    }

    @Override
    public String toString() {
        return "H264DataFragment{" +
                "size=" + data.length +
                ", timestamp=" + timestamp +
                '}';
    }
}
