/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestApiMessageHeaders {

    static class Message extends ApiMessage {
        @Override
        public Object getData() {
            return null;
        }

        @Override
        public ApiMessage cloneInstance() {
            return this;
        }

        @Override
        public ApiMessage cloneInstance(boolean includeHeaders) {
            return this;
        }
    }

    /**
     * 回归防护：getHeaders的懒初始化在并发首次调用时不能丢失写入、不能返回损坏的Map。
     * 修复采用volatile+双重检查锁，保证发布安全与初始化唯一性。
     * （时序竞态无法确定性红验证，此处为压力回归网）
     */
    @Test
    public void testConcurrentFirstGetHeaders() throws Exception {
        int rounds = 200;
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < rounds; round++) {
                Message msg = new Message();
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    final int idx = i;
                    futures.add(pool.submit(() -> {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        msg.setHeader("h" + idx, idx);
                    }));
                }
                start.countDown();
                for (Future<?> f : futures) {
                    f.get(10, TimeUnit.SECONDS);
                }
                // 所有线程的首写必须都可见
                assertEquals(threads, msg.getHeaders().size(),
                        "all first-write headers must be visible, round=" + round);
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
