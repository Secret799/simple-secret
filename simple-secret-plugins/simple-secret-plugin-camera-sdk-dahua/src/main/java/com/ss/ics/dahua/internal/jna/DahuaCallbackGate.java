package com.ss.ics.dahua.internal.jna;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 在 SDK 注册移除期间保护 native 回调消费者。 */
final class DahuaCallbackGate {
    private final Object monitor = new Object();
    private boolean accepting = true;
    private int inFlight;
    private Object callbackReference;

    void retain(Object callbackReference) {
        synchronized (monitor) {
            this.callbackReference = callbackReference;
        }
    }

    boolean enter() {
        synchronized (monitor) {
            if (!accepting || inFlight > 0) {
                return false;
            }
            inFlight++;
            return true;
        }
    }

    void exit() {
        synchronized (monitor) {
            inFlight--;
            if (inFlight == 0) {
                monitor.notifyAll();
            }
        }
    }

    boolean disableAndAwait(Duration timeout) {
        long remainingNanos = timeoutNanos(timeout);
        synchronized (monitor) {
            accepting = false;
            while (inFlight > 0 && remainingNanos > 0) {
                long started = System.nanoTime();
                try {
                    TimeUnit.NANOSECONDS.timedWait(monitor, remainingNanos);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                remainingNanos -= Math.max(0, System.nanoTime() - started);
            }
            return inFlight == 0;
        }
    }

    void release() {
        synchronized (monitor) {
            callbackReference = null;
        }
    }

    private static long timeoutNanos(Duration timeout) {
        try {
            return timeout.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}

/** 管理同一个 native 句柄可能关联的多个回调注册。 */
final class DahuaCallbackGroup {
    private final List<DahuaCallbackGate> gates = new ArrayList<>();

    synchronized void add(DahuaCallbackGate gate) {
        gates.add(gate);
    }

    synchronized boolean disableAndAwait(Duration timeout) {
        for (DahuaCallbackGate gate : gates) {
            if (!gate.disableAndAwait(timeout)) {
                return false;
            }
        }
        return true;
    }

    synchronized void release() {
        for (DahuaCallbackGate gate : gates) {
            gate.release();
        }
        gates.clear();
    }
}
