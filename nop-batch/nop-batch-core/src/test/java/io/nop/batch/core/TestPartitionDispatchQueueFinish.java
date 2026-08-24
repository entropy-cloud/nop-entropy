/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.batch.core.loader.PartitionDispatchQueue;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务结束（finish）后，阻塞在容量许可上的addBatch必须能够退出，
 * 否则fetch线程与底层JDBC连接会永久泄漏
 */
public class TestPartitionDispatchQueueFinish {

    @Test
    public void testAddBatchReturnsAfterFinish() throws InterruptedException {
        PartitionDispatchQueue<Integer> queue = new PartitionDispatchQueue<>(2, k -> 0);

        // 占满容量许可
        queue.addBatch(Arrays.asList(1, 2));

        // 模拟任务失败/结束后finish()被调用
        queue.finish();
        assertTrue(queue.isFinished());

        AtomicBoolean done = new AtomicBoolean(false);
        Thread t = new Thread(() -> {
            // 队列已满，此处会等待容量许可；finish后必须退出而不是永久阻塞
            queue.addBatch(Arrays.asList(3, 4));
            done.set(true);
        });
        t.setDaemon(true);
        t.start();

        t.join(5000);
        assertTrue(done.get(), "addBatch must return after finish instead of blocking forever");
        assertFalse(t.isAlive());
    }
}
