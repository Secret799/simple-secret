package com.ss.easymedia.h264.parser;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class H264NalUnitReaderTest {

    @Test
    void shouldRejectNonPositiveFragmentCapacity() {
        assertThatThrownBy(() -> new H264NalUnitReader(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fragmentCapacity");
        assertThatThrownBy(() -> new H264NalUnitReader(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fragmentCapacity");
    }

    @Test
    void defaultReaderShouldUseBoundedFragmentQueue() throws Exception {
        H264NalUnitReader reader = new H264NalUnitReader();
        Field queueField = H264NalUnitReader.class.getDeclaredField("fragmentQueue");
        queueField.setAccessible(true);
        BlockingQueue<?> queue = (BlockingQueue<?>) queueField.get(reader);

        assertThat(queue.remainingCapacity()).isLessThan(Integer.MAX_VALUE);
    }

    @Test
    void shouldRejectAccumulatorGrowthBeyondMaximumNalUnitSize() throws Exception {
        H264NalUnitReader reader = new H264NalUnitReader(1);
        Method append = H264NalUnitReader.class.getDeclaredMethod(
                "appendToAccumulator", byte[].class, int.class, int.class);
        append.setAccessible(true);
        byte[] oversizedFragment = new byte[16 * 1024 * 1024 + 1];

        assertThatThrownBy(() -> invokeAppend(append, reader, oversizedFragment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accumulator");
    }

    @Test
    void accumulatorOverflowShouldDiscardIncompleteNalUnit() throws Throwable {
        H264NalUnitReader reader = new H264NalUnitReader(1);
        Method append = H264NalUnitReader.class.getDeclaredMethod(
                "appendToAccumulator", byte[].class, int.class, int.class);
        append.setAccessible(true);
        invokeAppend(append, reader, new byte[16 * 1024 * 1024]);

        assertThatThrownBy(() -> invokeAppend(append, reader, new byte[1]))
                .isInstanceOf(IllegalStateException.class);

        Field bufferField = H264NalUnitReader.class.getDeclaredField("accumulatorBuffer");
        bufferField.setAccessible(true);
        ByteBuffer buffer = (ByteBuffer) bufferField.get(reader);
        assertThat(buffer.position()).isZero();
    }

    @Test
    void shouldStopReaderBlockedOnEmptyQueue() {
        H264NalUnitReader reader = new H264NalUnitReader();
        reader.startReading();
        Thread readerThread = awaitReaderThread();

        reader.stopReading();

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> readerThread.join());
        assertThat(readerThread.isAlive()).isFalse();
        assertThat(reader.isRunning()).isFalse();
    }

    @Test
    void shouldFailFastWhenWritingAfterReaderStops() {
        H264NalUnitReader reader = new H264NalUnitReader(1);
        reader.startReading();
        reader.stopReading();

        assertThatThrownBy(() -> reader.writeFragment(new byte[]{1}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not running");
    }

    @Test
    void readerThreadShouldRecoverAfterAccumulatorOverflow() throws Exception {
        H264NalUnitReader reader = new H264NalUnitReader(2);
        CountDownLatch processed = new CountDownLatch(1);
        reader.setNalUnitProcessor(nalUnit -> processed.countDown());
        reader.startReading();

        reader.writeFragment(new byte[16 * 1024 * 1024 + 1]);
        reader.writeFragment(new byte[]{0, 0, 0, 1, 0x65, 0, 0, 0, 1, 0x41});

        assertThat(processed.await(2, TimeUnit.SECONDS)).isTrue();
        reader.close();
    }

    @Test
    void blockedWriterShouldExitWhenReaderStops() throws Exception {
        H264NalUnitReader reader = new H264NalUnitReader(1);
        CountDownLatch processorEntered = new CountDownLatch(1);
        CountDownLatch allowProcessorToFinish = new CountDownLatch(1);
        CountDownLatch writerFinished = new CountDownLatch(1);
        AtomicReference<Throwable> writerFailure = new AtomicReference<>();
        reader.setNalUnitProcessor(nalUnit -> {
            processorEntered.countDown();
            while (allowProcessorToFinish.getCount() > 0) {
                try {
                    allowProcessorToFinish.await(20, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    // 测试中保持处理器占用，直到显式放行。
                }
            }
        });
        reader.startReading();
        reader.writeFragment(new byte[]{0, 0, 0, 1, 0x65, 0, 0, 0, 1, 0x41});
        assertThat(processorEntered.await(1, TimeUnit.SECONDS)).isTrue();
        reader.writeFragment(new byte[]{1});
        Thread writer = new Thread(() -> {
            try {
                reader.writeFragment(new byte[]{2});
            } catch (Throwable throwable) {
                writerFailure.set(throwable);
            } finally {
                writerFinished.countDown();
            }
        });
        writer.start();

        Thread stopper = new Thread(reader::stopReading);
        stopper.start();

        assertThat(writerFinished.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(writerFailure.get()).isInstanceOf(IllegalStateException.class);
        allowProcessorToFinish.countDown();
        stopper.join(1_000);
        assertThat(stopper.isAlive()).isFalse();
    }

    private Thread awaitReaderThread() {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (System.nanoTime() < deadline) {
            Thread readerThread = Thread.getAllStackTraces().keySet().stream()
                    .filter(thread -> thread.getName().equals("H264-NALU-Reader-Thread"))
                    .filter(Thread::isAlive)
                    .findFirst()
                    .orElse(null);
            if (readerThread != null) {
                return readerThread;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("H264 reader thread did not start");
    }

    private static void invokeAppend(Method append, H264NalUnitReader reader, byte[] data) throws Throwable {
        try {
            append.invoke(reader, data, 0, data.length);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
