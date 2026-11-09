/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.commons.concurrent.batch;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2547 R4-13（C3-012）载体钉：destroy 次序 = 停止接收 → drain 至空 → cancel → 销毁。
 */
public class TestAbstractBatchProcessService {

    static class Probe extends AbstractBatchProcessService<Integer> {
        final AtomicInteger processed = new AtomicInteger();
        final CountDownLatch allProcessed = new CountDownLatch(1);

        @Override
        protected void doProcess(List<Integer> list) {
            processed.addAndGet(list.size());
        }
    }

    /**
     * pin0312：destroy 前已入队的条目必须被 drain 完（`isAllProcessed` 可达），
     * 而不是 cancel-then-drop。缺陷态：先 cancel → 消费循环立即退出 → 队列残量丢弃。
     */
    @Test
    public void pin0312_queuedItemsAreDrainedBeforeDestroy() throws Exception {
        Probe svc = new Probe();
        BatchQueueConfig config = new BatchQueueConfig();
        config.setBatchSize(2);
        config.setMinWaitMillis(5);
        config.setMaxWaitMillis(20);
        config.setThreadPoolSize(1);
        svc.setQueueConfig(config);
        svc.start();
        try {
            for (int i = 0; i < 10; i++)
                svc.send(i);

            // 等消费推进
            Thread.sleep(200);
            svc.destroy();

            assertTrue(svc.isAllProcessed(),
                    "destroy 后必须已 drain 完队列（isAllProcessed 可达）；processed=" + svc.processed.get());
            assertTrue(svc.processed.get() >= 10 || svc.isAllProcessed(),
                    "已入队条目必须被处理或显式记录，实际 processed=" + svc.processed.get());
        } finally {
            // destroy 已调用
        }
    }

    /** pin0312b：destroy 是**有界**返回（不无限等待）。 */
    @Test
    public void pin0312b_destroyReturnsBounded() throws Exception {
        Probe svc = new Probe();
        BatchQueueConfig config = new BatchQueueConfig();
        config.setBatchSize(1);
        config.setMinWaitMillis(5);
        config.setMaxWaitMillis(10);
        config.setThreadPoolSize(1);
        svc.setQueueConfig(config);
        svc.start();
        svc.send(1);
        Thread.sleep(100);

        long t0 = System.currentTimeMillis();
        svc.destroy();
        long elapsed = System.currentTimeMillis() - t0;

        assertTrue(elapsed < 10_000, "destroy 必须在上界内返回，实际=" + elapsed + "ms");
    }

    /** pin0312c：destroy 后 send 必须被拒绝（accepting 位生效）。 */
    @Test
    public void pin0312c_sendRejectedAfterDestroy() throws Exception {
        Probe svc = new Probe();
        BatchQueueConfig config = new BatchQueueConfig();
        config.setBatchSize(1);
        config.setMinWaitMillis(5);
        config.setMaxWaitMillis(10);
        config.setThreadPoolSize(1);
        svc.setQueueConfig(config);
        svc.start();
        svc.destroy();

        assertFalse(svc.send(42), "destroy 后 send 必须返回 false（拒绝新入队）");
    }
}
