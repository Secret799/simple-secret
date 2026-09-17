package com.ss.gb28181;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class CallbackDispatcher<E> implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(CallbackDispatcher.class.getName());
    private final ThreadPoolExecutor executor;
    private final Consumer<E> listener;

    CallbackDispatcher(int capacity, String name, Consumer<E> listener) {
        this.listener = listener;
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), runnable -> {
                    Thread thread = new Thread(runnable, "simple-secret-gb-" + name);
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    boolean dispatch(E event, Runnable released) {
        try {
            executor.execute(new Delivery(event, released));
            return true;
        } catch (RejectedExecutionException closed) {
            return false;
        }
    }

    @Override public void close() {
        executor.shutdownNow();
    }

    private final class Delivery implements Runnable {
        private final E event;
        private final Runnable released;
        private boolean done;

        private Delivery(E event, Runnable released) {
            this.event = event;
            this.released = released;
        }

        @Override public void run() {
            try {
                listener.accept(event);
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "GB subscription listener failed: {0}",
                        failure.getClass().getSimpleName());
            } finally {
                release();
            }
        }

        private synchronized void release() {
            if (!done) {
                done = true;
                released.run();
            }
        }
    }
}
