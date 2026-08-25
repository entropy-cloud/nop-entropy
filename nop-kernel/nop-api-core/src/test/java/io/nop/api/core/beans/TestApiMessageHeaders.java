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

        @Override
        public String toString() {
            // 与ApiRequest/ApiResponse的toString一致：经appendHeaders统一输出headers
            StringBuilder sb = new StringBuilder("Message[");
            appendHeaders(sb);
            sb.append(']');
            return sb.toString();
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

    /**
     * 回归防护：removeHeader必须与setHeader共用同一把锁。
     * 无锁remove与持锁put并发时会损坏TreeMap结构（CME或死循环）。
     * （数据竞态无法确定性红验证，此处为压力回归网）
     */
    @Test
    public void testConcurrentSetAndRemoveHeader() throws Exception {
        int threadsPerOp = 4;
        int opsPerThread = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(threadsPerOp * 2);
        try {
            Message msg = new Message();
            for (int i = 0; i < 10; i++) {
                msg.setHeader("seed" + i, i);
            }
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadsPerOp; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int n = 0; n < opsPerThread; n++) {
                        msg.setHeader("h" + idx, n);
                    }
                    return null;
                }));
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int n = 0; n < opsPerThread; n++) {
                        msg.removeHeader("h" + idx);
                        msg.removeHeader("seed" + (n % 10));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
            // 最终读取不抛异常且Map可用
            msg.setHeader("final", "ok");
            assertEquals("ok", msg.getHeader("final"));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    /**
     * 回归：toString输出的headers中，Authorization/AccessToken/Cookie必须脱敏，令牌不能进日志。
     */
    @Test
    public void testToStringMasksSensitiveHeaders() {
        Message msg = new Message();
        msg.setBearerToken("secret-token-12345678");
        msg.setHeader("x-access-token", "access-secret-value-1234");
        msg.setHeader("cookie", "session=cookie-secret-value");
        msg.setHeader("x-trace-id", "trace-1");

        String str = msg.toString();
        assertTrue(str.contains("***"), "sensitive headers must be masked in toString");

        // 凭据明文不允许出现
        assertTrue(!str.contains("secret-token-12345678"), "bearer token must not leak");
        assertTrue(!str.contains("access-secret-value-1234"), "access token must not leak");
        assertTrue(!str.contains("cookie-secret-value"), "cookie must not leak");

        // 非敏感头保持可见，原始headers不受脱敏影响
        assertTrue(str.contains("trace-1"));
        assertEquals("Bearer secret-token-12345678", msg.getHeader("authorization"));
    }
}
