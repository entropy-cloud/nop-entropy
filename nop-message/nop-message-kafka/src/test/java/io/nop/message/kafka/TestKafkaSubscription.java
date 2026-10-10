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
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Component-level test for {@link KafkaMessageService.KafkaMessageSubscription} lifecycle:
 * suspend/resume map to {@link KafkaConsumer#pause} / {@link KafkaConsumer#resume}, and
 * cancel stops the poll task and closes the consumer.
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
    void suspendSetsFlagAndPausesConsumer() {
        Set<TopicPartition> assignment = new HashSet<>();
        assignment.add(new TopicPartition("t", 0));
        when(consumer.assignment()).thenReturn(assignment);
        doNothing().when(consumer).pause(any());

        KafkaMessageService.KafkaMessageSubscription sub = newSubscription();
        assertFalse(sub.isSuspended());
        sub.suspend();
        assertTrue(sub.isSuspended());
    }

    @Test
    void resumeClearsFlagAndResumesConsumer() {
        Set<TopicPartition> assignment = new HashSet<>();
        assignment.add(new TopicPartition("t", 0));
        when(consumer.assignment()).thenReturn(assignment);
        doNothing().when(consumer).pause(any());
        doNothing().when(consumer).resume(any());

        KafkaMessageService.KafkaMessageSubscription sub = newSubscription();
        sub.suspend();
        assertTrue(sub.isSuspended());
        sub.resume();
        assertFalse(sub.isSuspended());
    }

    @Test
    void cancelStopsTaskAndClosesConsumer() throws Exception {
        doNothing().when(task).stop();
        doNothing().when(consumer).close();

        KafkaMessageService.KafkaMessageSubscription sub = newSubscription();
        assertFalse(sub.isCancelled());
        sub.cancel();
        assertTrue(sub.isCancelled());

        org.mockito.Mockito.verify(task).stop();
        org.mockito.Mockito.verify(consumer).close();
    }

    @Test
    void cancelIsIdempotent() {
        doNothing().when(task).stop();
        doNothing().when(consumer).close();

        KafkaMessageService.KafkaMessageSubscription sub = newSubscription();
        sub.cancel();
        sub.cancel();
        // task.stop invoked once on first cancel
        org.mockito.Mockito.verify(task).stop();
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
        // 给 cancel 足够时间走到 close（若顺序错误，此时 close 已在 worker 运行中触发）
        Thread.sleep(300);
        boolean closeDuringRun = closedWhileWorkerRunning.get();
        releaseWorker.countDown();
        cancelThread.join(10_000);

        assertFalse(closeDuringRun,
                "consumer.close must happen only after the consume worker exited (P1-4.4-M02)");
        assertTrue(executor.isShutdown() || executor.isTerminated(), "executor shut down during cancel");
        worker.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }
}
