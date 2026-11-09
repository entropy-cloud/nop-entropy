/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.commons.concurrent.batch;

import io.nop.commons.concurrent.IBlockingQueue;
import io.nop.commons.concurrent.impl.OverflowBlockingQueue;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2547 R4-13（C3-011）载体钉：消费循环对 Error 的韧性。
 */
public class TestBlockingSourceConsumeTask {

    /**
     * pin0311：消费体抛 `Error` 后循环**仍存活**并继续处理后续条目。
     * 缺陷态：循环顶层无 catch(Throwable) → 线程永久死亡、后续条目永不处理。
     */
    @Test
    public void pin0311_loopSurvivesErrorAndKeepsConsuming() throws Exception {
        IBlockingQueue<Integer> queue = new OverflowBlockingQueue<>(100,
                io.nop.commons.concurrent.QueueOverflowPolicy.ERROR);
        BatchQueueConfig config = new BatchQueueConfig();
        config.setBatchSize(1);
        config.setMinWaitMillis(10);
        config.setMaxWaitMillis(50);

        AtomicInteger consumed = new AtomicInteger();
        CountDownLatch secondConsumed = new CountDownLatch(1);

        BlockingSourceConsumeTask<Integer> task = new BlockingSourceConsumeTask<>(queue, list -> {
            for (Integer v : list) {
                if (v == 1) {
                    consumed.incrementAndGet();
                    throw new AssertionError("canned-Error-on-first-item");
                }
                consumed.incrementAndGet();
                secondConsumed.countDown();
            }
        }, config, null);

        Thread th = new Thread(task);
        th.setDaemon(true);
        th.start();
        try {
            queue.send(1);   // 触发 Error
            Thread.sleep(100);
            queue.send(2);   // 后续条目
            assertTrue(secondConsumed.await(5, TimeUnit.SECONDS),
                    "Error 后循环必须存活并继续消费（缺陷态=线程死亡、条目永不处理）；consumed=" + consumed.get());
        } finally {
            task.cancel();
            th.join(2000);
        }
    }
}
