package com.ss.redis.operation;

import com.ss.redis.exception.RedisOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RBoundedBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RPriorityBlockingQueue;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"deprecation", "unchecked", "rawtypes"})
class RedissonQueueOperationsTest {

    private RedissonClient client;
    private RBlockingQueue<Object> blockingQueue;
    private RDelayedQueue<Object> delayedQueue;
    private RPriorityBlockingQueue<Object> priorityQueue;
    private RBoundedBlockingQueue<Object> boundedQueue;
    private RedissonQueueOperations operations;

    @BeforeEach
    void setUp() {
        client = mock(RedissonClient.class);
        blockingQueue = mock(RBlockingQueue.class);
        delayedQueue = mock(RDelayedQueue.class);
        priorityQueue = mock(RPriorityBlockingQueue.class);
        boundedQueue = mock(RBoundedBlockingQueue.class);
        operations = new RedissonQueueOperations(client);
    }

    @Test
    void delegatesOrdinaryQueueOperations() {
        when(client.<Object>getBlockingQueue("jobs")).thenReturn(blockingQueue);
        when(blockingQueue.offer("one")).thenReturn(true);
        when(blockingQueue.poll()).thenReturn("one");
        when(blockingQueue.remove("one")).thenReturn(true);
        when(blockingQueue.delete()).thenReturn(true);

        assertThat(operations.offer("jobs", "one")).isTrue();
        assertThat(operations.poll("jobs", String.class)).isEqualTo("one");
        assertThat(operations.remove("jobs", "one")).isTrue();
        assertThat(operations.delete("jobs")).isTrue();
    }

    @Test
    void delegatesDelayedQueueOperationsThroughDestinationQueue() {
        when(client.<Object>getBlockingQueue("jobs")).thenReturn(blockingQueue);
        when(client.getDelayedQueue(blockingQueue)).thenReturn(delayedQueue);
        when(delayedQueue.remove("one")).thenReturn(true);

        operations.offerDelayed("jobs", "one", Duration.ofSeconds(5));

        assertThat(operations.removeDelayed("jobs", "one")).isTrue();
        operations.destroyDelayed("jobs");
        verify(delayedQueue).offer("one", 5_000, TimeUnit.MILLISECONDS);
        verify(delayedQueue).destroy();
    }

    @Test
    void delegatesPriorityQueueOperations() {
        when(client.<Object>getPriorityBlockingQueue("priority-jobs")).thenReturn(priorityQueue);
        when(priorityQueue.offer("urgent")).thenReturn(true);
        when(priorityQueue.poll()).thenReturn("urgent");
        when(priorityQueue.remove("urgent")).thenReturn(true);
        when(priorityQueue.delete()).thenReturn(true);

        assertThat(operations.offerPriority("priority-jobs", "urgent")).isTrue();
        assertThat(operations.pollPriority("priority-jobs", String.class)).isEqualTo("urgent");
        assertThat(operations.removePriority("priority-jobs", "urgent")).isTrue();
        assertThat(operations.deletePriority("priority-jobs")).isTrue();
    }

    @Test
    void delegatesBoundedQueueOperations() {
        when(client.<Object>getBoundedBlockingQueue("limited-jobs")).thenReturn(boundedQueue);
        when(boundedQueue.trySetCapacity(20)).thenReturn(true);
        when(boundedQueue.offer("one")).thenReturn(true);
        when(boundedQueue.poll()).thenReturn("one");
        when(boundedQueue.remove("one")).thenReturn(true);
        when(boundedQueue.delete()).thenReturn(true);

        assertThat(operations.trySetCapacity("limited-jobs", 20)).isTrue();
        assertThat(operations.offerBounded("limited-jobs", "one")).isTrue();
        assertThat(operations.pollBounded("limited-jobs", String.class)).isEqualTo("one");
        assertThat(operations.removeBounded("limited-jobs", "one")).isTrue();
        assertThat(operations.deleteBounded("limited-jobs")).isTrue();
    }

    @Test
    void subscribesWithAsyncFunctionAndUnsubscribesExactlyOnce() {
        when(client.<Object>getBlockingQueue("jobs")).thenReturn(blockingQueue);
        when(blockingQueue.subscribeOnElements(any(Function.class))).thenReturn(23);
        ArgumentCaptor<Function> listenerCaptor = ArgumentCaptor.forClass(Function.class);
        AtomicReference<String> received = new AtomicReference<>();

        RedisQueueSubscription subscription = operations.subscribe("jobs", received::set);
        verify(blockingQueue).subscribeOnElements(listenerCaptor.capture());
        CompletionStage<Void> completion = (CompletionStage<Void>) listenerCaptor.getValue().apply("payload");

        assertThat(completion.toCompletableFuture()).isCompletedWithValue(null);
        assertThat(received).hasValue("payload");
        subscription.close();
        subscription.close();
        verify(blockingQueue, times(1)).unsubscribe(23);
    }

    @Test
    void validatesQueueNamesValuesDelayCapacityAndConsumers() {
        assertThatThrownBy(() -> operations.offer(" ", "value"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operations.offer("jobs", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> operations.offerDelayed("jobs", "value", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operations.trySetCapacity("jobs", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operations.subscribe("jobs", null))
                .isInstanceOf(NullPointerException.class);

        verify(client, never()).getBlockingQueue("jobs");
        verify(client, never()).getBoundedBlockingQueue("jobs");
    }

    @Test
    void wrapsQueueAndUnsubscribeFailuresWithoutSensitiveCauseText() {
        RuntimeException queueFailure = new RuntimeException("password=queue-secret");
        when(client.<Object>getBlockingQueue("jobs")).thenReturn(blockingQueue);
        when(blockingQueue.poll()).thenThrow(queueFailure);

        assertThatThrownBy(() -> operations.poll("jobs", String.class))
                .isInstanceOf(RedisOperationException.class)
                .hasMessageContaining("poll")
                .hasMessageContaining("jobs")
                .hasMessageNotContaining("queue-secret")
                .hasCause(queueFailure);

        when(blockingQueue.subscribeOnElements(any(Function.class))).thenReturn(23);
        doThrow(queueFailure).when(blockingQueue).unsubscribe(23);
        RedisQueueSubscription subscription = operations.subscribe("jobs", ignored -> { });

        assertThatThrownBy(subscription::close)
                .isInstanceOf(RedisOperationException.class)
                .hasMessageContaining("unsubscribe")
                .hasMessageNotContaining("queue-secret")
                .hasCause(queueFailure);
    }
}
