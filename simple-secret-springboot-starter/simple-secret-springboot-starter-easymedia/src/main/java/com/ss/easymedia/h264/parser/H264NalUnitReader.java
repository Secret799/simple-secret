package com.ss.easymedia.h264.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * H.264 NALU 读取器
 *
 * @author junpzx
 */
public class H264NalUnitReader implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(H264NalUnitReader.class);

    /** 默认片段队列容量。 */
    private static final int DEFAULT_FRAGMENT_CAPACITY = 150;

    /** 累积缓冲区初始容量，2 MiB。 */
    private static final int INITIAL_ACCUMULATOR_CAPACITY = 2 * 1024 * 1024;

    /** 单个待重组 NALU 允许占用的最大累积容量，16 MiB。 */
    private static final int MAX_ACCUMULATOR_CAPACITY = 16 * 1024 * 1024;

    // 片段队列
    private final BlockingQueue<H264DataFragment> fragmentQueue;
    // 运行状态
    private final AtomicBoolean running = new AtomicBoolean(false);
    // 后台读取线程
    private volatile Thread readerThread;
    // NALU 处理器
    private volatile H264NalUnitProcessor nalUnitProcessor;

    // 累积缓冲区，用于存放所有接收到但尚未处理成完整 NALU 的字节
    private ByteBuffer accumulatorBuffer = ByteBuffer.allocate(INITIAL_ACCUMULATOR_CAPACITY);
    // 缓冲区中已扫描记录下标
    private final Object reassemblyLock = new Object();

    /**
     * 创建并初始化实例。
     *
     * @param fragmentCapacity 单个 SEI 分片的最大字节数
     */
    public H264NalUnitReader(int fragmentCapacity) {
        if (fragmentCapacity <= 0) {
            throw new IllegalArgumentException("fragmentCapacity must be greater than 0");
        }
        this.fragmentQueue = new LinkedBlockingQueue<>(fragmentCapacity);
    }

    /**
     * 创建并初始化实例。
     */
    public H264NalUnitReader() {
        this(DEFAULT_FRAGMENT_CAPACITY);
    }

    /**
     * 供生产者线程调用，将数据片段写入队列。
     *
     * @param data 数据片段
     * @throws InterruptedException 处理队列添加数据时如果在等待时中断
     */
    public void writeFragment(byte[] data) throws InterruptedException {
        H264DataFragment fragment = new H264DataFragment(Objects.requireNonNull(data, "data"));
        enqueueFragment(fragment);
    }

    /**
     * 写入数据片段并等待解析线程完成该片段。
     *
     * @param data 数据片段
     * @return 处理完成后解析器仍保留的累积字节数
     * @throws InterruptedException 等待队列或处理完成时线程被中断
     */
    public int writeFragmentWithBackpressure(byte[] data) throws InterruptedException {
        H264DataFragment fragment = new H264DataFragment(
                Objects.requireNonNull(data, "data"), false);
        enqueueFragment(fragment);
        return fragment.awaitProcessed();
    }

    /**
     * 将片段放入有界解析队列。
     *
     * @param fragment 数据片段
     * @throws InterruptedException 等待队列容量时线程被中断
     */
    private void enqueueFragment(H264DataFragment fragment) throws InterruptedException {
        while (running.get()) {
            if (fragmentQueue.offer(fragment, 100, TimeUnit.MILLISECONDS)) {
                if (!running.get() && fragmentQueue.remove(fragment)) {
                    throw new IllegalStateException("H264 reader is not running");
                }
                return;
            }
        }
        throw new IllegalStateException("H264 reader is not running");
    }

    /**
     * 启动后台线程开始读取、累积、扫描、重组和处理 NALU。
     */
    public synchronized void startReading() {
        if (running.compareAndSet(false, true)) {
            readerThread = new Thread(() -> {
                log.info("H264NalUnitReader thread started.");
                try {
                    while (running.get()) {
                        H264DataFragment fragment = fragmentQueue.take();
                        RuntimeException processingFailure = null;
                        try {
                            synchronized (reassemblyLock) {
                                accumulateAndProcessFragment(fragment);
                            }
                        } catch (RuntimeException exception) {
                            processingFailure = exception;
                            log.error("Error during reading/accumulation/reassembly: {}",
                                    exception.getMessage(), exception);
                        } catch (LinkageError error) {
                            processingFailure = new IllegalStateException(
                                    "Native H264 processing failed", error);
                            log.error("Native error during H264 processing", error);
                        } finally {
                            fragment.complete(processingFailure, accumulatorBuffer.position());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (running.get()) {
                        log.warn("H264NalUnitReader thread interrupted unexpectedly.", e);
                    }
                } finally {
                    running.set(false);
                    failQueuedFragments();
                    readerThread = null;
                    log.info("H264NalUnitReader thread stopped.");
                }
            }, "H264-NALU-Reader-Thread");
            readerThread.start();
        } else {
            log.warn("H264NalUnitReader is already running.");
        }
    }

    /**
     * 停止读取。
     */
    public synchronized void stopReading() {
        log.info("Stopping H264NalUnitReader...");
        running.set(false);
        failQueuedFragments();
        Thread currentReaderThread = readerThread;
        if (currentReaderThread == null || currentReaderThread == Thread.currentThread()) {
            return;
        }
        currentReaderThread.interrupt();
        try {
            currentReaderThread.join(1000);
            if (currentReaderThread.isAlive()) {
                log.warn("H264NalUnitReader thread did not stop within 1000 ms.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for H264NalUnitReader to stop.", e);
        }
    }

    /** 让仍在等待队列消费的生产者收到 reader 已停止的失败。 */
    private void failQueuedFragments() {
        RuntimeException failure = new IllegalStateException("H264 reader is not running");
        H264DataFragment fragment;
        while ((fragment = fragmentQueue.poll()) != null) {
            fragment.complete(failure, accumulatorBuffer.position());
        }
    }

    /**
     * 停止后台读取线程。
     */
    @Override
    public void close() {
        stopReading();
    }

    /**
     * 获取运行状态
     *
     * @return 运行状态
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 设置 NALU 处理器
     *
     * @param nalUnitProcessor NALU 处理器
     */
    public void setNalUnitProcessor(H264NalUnitProcessor nalUnitProcessor) {
        this.nalUnitProcessor = nalUnitProcessor;
    }

    /**
     * 获取队列中数据片段的数量
     *
     * @return 队列中数据片段的数量
     */
    public int getQueueSize() {
        return fragmentQueue.size();
    }

    /**
     * 追加需要处理的数据到缓冲区中
     *
     * @param data   需要处理的数据
     * @param offset 数据的起始位置
     * @param length 数据的长度
     */
    private void appendToAccumulator(byte[] data, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, data.length);
        long requiredCapacity = (long) accumulatorBuffer.position() + length;
        if (requiredCapacity > MAX_ACCUMULATOR_CAPACITY) {
            accumulatorBuffer.clear();
            throw new IllegalStateException("H264 accumulator exceeds maximum capacity");
        }
        if (accumulatorBuffer.remaining() < length) {
            int doubledCapacity = Math.min(
                    accumulatorBuffer.capacity() * 2,
                    MAX_ACCUMULATOR_CAPACITY);
            int newCapacity = Math.max(doubledCapacity, (int) requiredCapacity);
            accumulatorBuffer = expandBuffer(accumulatorBuffer, newCapacity);
        }
        accumulatorBuffer.put(data, offset, length);
    }

    /**
     * 扩容缓冲区
     *
     * @param oldBuffer   旧缓冲区
     * @param newCapacity 新的容量
     * @return 新的缓冲区
     */
    public static ByteBuffer expandBuffer(ByteBuffer oldBuffer, int newCapacity) {
        if (newCapacity <= oldBuffer.capacity()) {
            return oldBuffer;
        }
        // 分配新的缓冲区
        ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity);
        // 切换成读取模式
        oldBuffer.flip();
        // 拷贝数据
        newBuffer.put(oldBuffer);
        return newBuffer;
    }


    /**
     * 输出一个完整的 NALU 并更新缓冲区状态。
     * 此方法应在持有 reassemblyLock 锁的情况下调用。
     *
     * @param naluLength        NALU 的长度
     * @param currNaluTimestamp 当前 NALU 的时间戳
     */
    private void outputNalUnit(int naluLength, long currNaluTimestamp) {
        if (naluLength <= 0) {
            return;
        }
        // 创建nalu 数据
        byte[] nalData = new byte[naluLength];
        // 拷贝数据
        accumulatorBuffer.get(0, nalData);
        accumulatorBuffer.position(naluLength);
        boolean hasFourByteStartCode = nalData.length > 4
                && nalData[0] == 0x00
                && nalData[1] == 0x00
                && nalData[2] == 0x00
                && nalData[3] == 0x01;
        boolean hasThreeByteStartCode = nalData.length > 3
                && nalData[0] == 0x00
                && nalData[1] == 0x00
                && nalData[2] == 0x01;
        boolean isAvailable = hasFourByteStartCode || hasThreeByteStartCode;
        if (!isAvailable) {
            return;
        }

        // 创建一个 NALU
        H264NalUnit nalUnit = new H264NalUnit(nalData, currNaluTimestamp);
        // 处理 NALU
        H264NalUnitProcessor currentProcessor = this.nalUnitProcessor;
        if (currentProcessor != null) {
            try {
                currentProcessor.process(nalUnit);
            } catch (Exception e) {
                log.error("Error during NALU processing: {}", e.getMessage(), e);
            }
        } else {
            log.warn("No NALU processor is set.");
        }
    }

    /**
     * 累积片段数据，并在其上扫描起始码以进行 NALU 重组。
     * 此方法应在持有 reassemblyLock 锁的情况下调用。
     *
     * @param fragment 输入的数据片段
     */
    private void accumulateAndProcessFragment(H264DataFragment fragment) {
        byte[] data = fragment.getData();
        if (data.length == 0) {
            return;
        }
        int lastEndIndex = 0;
        // 获取当前缓冲区的第一个Nalu包的时间戳
        for (int i = 0; i < data.length - 3; ) {
            // 如果当前下标是 00 00 00 01 或者 00 00 01开头
            // 那么代表这是一个可用的Nalu包
            int naluHeadLength = -1;
            if (data[i] == 0x00
                    && data[i + 1] == 0x00
                    && data[i + 2] == 0x00
                    && data[i + 3] == 0x01) {
                naluHeadLength = 4;
            }
            if (data[i] == 0x00 && data[i + 1] == 0x00 && data[i + 2] == 0x01) {
                naluHeadLength = 3;
            }
            if (naluHeadLength != -1) {
                // 输出上一个Nalu包
                // Math.max(position, lastNaluEndIndex + 1) 上上个Nalu包的开始下标
                // dataInBufferIndex 上一个Nalu包的结束下标
                // 第一次扫描到 NALU 时可能还没有上一个结束位置。
                // 如果出现lastNaluEndIndex为空的情况，那么直接
                if (lastEndIndex != i) {
                    appendToAccumulator(data, lastEndIndex, i);
                    lastEndIndex = i;
                }
                accumulatorBuffer.flip();
                int limit = accumulatorBuffer.limit();
                outputNalUnit(limit, fragment.getTimestamp());
                accumulatorBuffer.compact();
                // 跳过Nalu包的头部
                i += naluHeadLength;
            } else {
                i++;
            }
        }
        appendToAccumulator(data, lastEndIndex, data.length - lastEndIndex);

    }

}
