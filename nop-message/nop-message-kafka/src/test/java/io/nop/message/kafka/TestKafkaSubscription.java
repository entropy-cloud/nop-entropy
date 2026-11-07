/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.kafka;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Component-level test for {@link KafkaMessageService.KafkaMessageSubscription} lifecycle:
 * suspend/resume record the desired state (applied by the consume thread — WI20/F-26), and
 * cancel is atomic-idempotent (WI20/F-25) with the consumer closed strictly after the
 * consume thread terminated (WI20/F-26 close-after-termination).
 */
class TestKafkaSubscription {

    private KafkaMessageService service;
    private Consumer<String, String> consumer;
    private ExecutorService executor;
    private KafkaConsumeTask task;

    @BeforeEach
    void setUp() {
        service = new KafkaMessageService();
        consumer = mock(Consumer.class);
        executor = Executors.newSingleThreadExecutor();
        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t", msgConsumer, options);
        task = mock(KafkaConsumeTask.class);
    }

    private KafkaMessageService.KafkaMessageSubscription newSubscription() {
        return service.new KafkaMessageSubscription(consumer, executor, task);
    }

    @Test
    void suspendRecordsDesiredStateWithoutTouchingConsumer() {
        KafkaMessageService.KafkaMessageSubscription sub = newSubscription();
        assertFalse(sub.isSuspended());
        sub.suspend();
        assertTrue(sub.isSuspended());
        // F-26: the consumer is NOT paused from the caller's thread — the consume thread
        // applies the state at the next loop round (verified by the task-level tests).
        verify(consumer, times(0)).pause(any());
        verify(task).setSuspended(true);
    }

    @Test
    void resumeClearsFlagAndDesiredState() {
        KafkaMessageService.KafkaMessageSubscription sub = newSubscription();
        sub.suspend();
        assertTrue(sub.isSuspended());
        sub.resume();
        assertFalse(sub.isSuspended());
        verify(consumer, times(0)).resume(any());
        verify(task).setSuspended(false);
    }

    @Test
    void cancelStopsTaskAndClosesConsumer() throws Exception {
        doAnswer(inv -> null).when(task).stop();
        doAnswer(inv -> null).when(consumer).close();

        KafkaMessageService.KafkaMessageSubscription sub = newSubscription();
        assertFalse(sub.isCancelled());
        sub.cancel();
        assertTrue(sub.isCancelled());

        verify(task).stop();
        verify(consumer).close();
    }

    @Test
    void cancelIsIdempotent() {
        doAnswer(inv -> null).when(task).stop();
        doAnswer(inv -> null).when(consumer).close();

        KafkaMessageService.KafkaMessageSubscription sub = newSubscription();
        sub.cancel();
        sub.cancel();
        // task.stop invoked once on first cancel (WI20: AtomicBoolean guard)
        verify(task).stop();
        verify(consumer, times(1)).close();
    }

    /**
     * WI20/F-25: concurrent cancels — only one thread runs the cleanup body (task.stop and
     * consumer.close exactly once across 4 racing threads).
     */
    @Test
    void concurrentCancelsRunCleanupBodyExactlyOnce() throws Exception {
        doAnswer(inv -> {
            // widen the check-then-act window（latch.await 定界，可中断、时长确定）
            new java.util.concurrent.CountDownLatch(1)
                    .await(50, java.util.concurrent.TimeUnit.MILLISECONDS);
            return null;
        }).when(task).stop();
        doAnswer(inv -> null).when(consumer).close();

        KafkaMessageService.KafkaMessageSubscription sub = newSubscription();
        int threads = 4;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    barrier.await();
                    sub.cancel();
                } catch (Exception e) {
                    // barrier awaits cannot realistically fail here; assertion below is the arbiter
                } finally {
                    done.countDown();
                }
            }).start();
        }
        done.await();

        verify(task, times(1)).stop();
        verify(consumer, times(1)).close();
        assertTrue(sub.isCancelled());
    }

    /**
     * WI20/F-26 close-after-termination: with a REAL consume task blocked in poll, cancel
     * must close the consumer only after the consume thread has TERMINATED. The poll
     * answer captures the consuming thread reference; the close answer asserts its state.
     */
    @Test
    void cancelClosesConsumerOnlyAfterConsumeThreadTerminates() throws Exception {
        ExecutorService realExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test-kafka-consume-close-order");
            t.setDaemon(true);
            return t;
        });
        Consumer<String, String> realBehaviorConsumer = mock(Consumer.class);
        AtomicReference<Thread> consumeThread = new AtomicReference<>();
        AtomicReference<Thread.State> consumeThreadStateAtClose = new AtomicReference<>();
        CountDownLatch pollStarted = new CountDownLatch(1);
        when(realBehaviorConsumer.poll(any(Duration.class))).thenAnswer(inv -> {
            consumeThread.set(Thread.currentThread());
            pollStarted.countDown();
            // bounded idle poll round（可中断定界等待，cancel 的 shutdownNow 以中断结束）
            new java.util.concurrent.CountDownLatch(1)
                    .await(300, java.util.concurrent.TimeUnit.MILLISECONDS);
            return ConsumerRecords.EMPTY;
        });
        when(realBehaviorConsumer.assignment()).thenReturn(Collections.emptySet());
        doAnswer(inv -> {
            Thread t = consumeThread.get();
            consumeThreadStateAtClose.set(t == null ? null : t.getState());
            return null;
        }).when(realBehaviorConsumer).close();

        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        MessageSubscriptionConfig config =
                new MessageSubscriptionConfig("t", msgConsumer, new MessageSubscribeOptions());
        KafkaConsumeTask realTask = new KafkaConsumeTask(service, realBehaviorConsumer,
                config, KafkaConsumeTask.DEFAULT_CONSUME_ERROR_BACKOFF_MS);
        realTask.bindExecutor(realExecutor, 100);
        realTask.start();
        // Make sure the consume thread is inside poll before cancelling: otherwise the
        // loop could exit before the first poll and the thread reference would stay null.
        pollStarted.await(2, java.util.concurrent.TimeUnit.SECONDS);

        KafkaMessageService.KafkaMessageSubscription sub =
                service.new KafkaMessageSubscription(realBehaviorConsumer, realExecutor, realTask);
        sub.cancel();

        assertEquals(Thread.State.TERMINATED, consumeThreadStateAtClose.get(),
                "consumer.close must run after the consume thread terminated, saw state: "
                        + consumeThreadStateAtClose.get());
        assertTrue(sub.isCancelled());
    }

    /**
     * P1-4.4-M02（plan 2395→2396 承接）：cancel 必须**先**等消费线程退出**再** close
     * consumer——此前 close 先于线程退出，消费线程在 poll/commitSync 中触发 CME 被
     * 吞掉，consumer 泄漏且 cancel 线程阻塞。
     */
    @Test
    void cancelClosesConsumerOnlyAfterWorkerExits() throws Exception {
        java.util.concurrent.CountDownLatch workerStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseWorker = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean closedWhileWorkerRunning =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        // 模拟消费线程正在 poll（被 latch 阻塞）
        java.util.concurrent.Future<?> worker = executor.submit(() -> {
            workerStarted.countDown();
            try {
                releaseWorker.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });
        workerStarted.await();

        org.mockito.Mockito.doAnswer(inv -> {
            closedWhileWorkerRunning.set(releaseWorker.getCount() > 0);
            return null;
        }).when(consumer).close();

        KafkaMessageService.KafkaMessageSubscription sub = service.new KafkaMessageSubscription(consumer, executor, task);
        Thread cancelThread = new Thread(sub::cancel);
        cancelThread.start();
        // 重新求值证据：等待 cancel 进入 executor.awaitTermination（TIMED_WAITING）——
        // 若 close 先于 worker 退出被错误触发，此刻 closedWhileWorkerRunning 必已为 true
        org.junit.jupiter.api.Assertions.assertTrue(
                io.nop.api.core.util.FutureHelper.waitUntil(() -> cancelThread.getState() == Thread.State.TIMED_WAITING
                        || !cancelThread.isAlive(), 10_000L),
                "cancel thread must be awaiting executor termination while worker is blocked");
        boolean closeDuringRun = closedWhileWorkerRunning.get();
        releaseWorker.countDown();
        cancelThread.join(10_000);

        assertFalse(closeDuringRun,
                "consumer.close must happen only after the consume worker exited (P1-4.4-M02)");
        assertTrue(executor.isShutdown() || executor.isTerminated(), "executor shut down during cancel");
        worker.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }
}
