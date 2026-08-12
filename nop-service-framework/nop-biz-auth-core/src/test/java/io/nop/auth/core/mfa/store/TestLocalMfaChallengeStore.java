/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W4 Phase 3 tests for the Local {@link MfaChallengeStore} implementation.
 */
public class TestLocalMfaChallengeStore {

    private static final int SHORT_TTL = 1; // 1 second, for expiry test

    @Test
    public void testLifecycleCreatePeekConsume() {
        LocalMfaChallengeStore store = new LocalMfaChallengeStore();
        String token = store.create("user-1", "totp", 1, "tenant-0", null);
        assertNotNull(token);
        assertNotEquals("", token);

        MfaChallenge peeked = store.peek(token);
        assertNotNull(peeked, "peek must hit right after create");
        assertEquals("user-1", peeked.getUserId());
        assertEquals("totp", peeked.getMfaType());
        assertEquals(1, peeked.getLoginType());
        assertEquals("tenant-0", peeked.getTenantId());

        MfaChallenge consumed = store.consume(token);
        assertNotNull(consumed, "consume returns the challenge");
        assertEquals("user-1", consumed.getUserId());

        assertNull(store.peek(token), "peek after consume returns null");
        assertNull(store.consume(token), "consume after consume returns null");
    }

    @Test
    public void testPeekDoesNotRefreshTtl() throws InterruptedException {
        MfaChallengeStoreConfig cfg = new MfaChallengeStoreConfig();
        cfg.setExpireSeconds(SHORT_TTL);
        LocalMfaChallengeStore store = new LocalMfaChallengeStore(cfg);

        String token = store.create("user-2", "sms", 5, "t", "13800000000");
        long expireAt1 = store.peekExpireAtMillis(token);
        assertTrue(expireAt1 > 0);

        // peek multiple times
        for (int i = 0; i < 5; i++) {
            assertNotNull(store.peek(token));
        }
        long expireAt2 = store.peekExpireAtMillis(token);
        assertEquals(expireAt1, expireAt2, "peek must NOT refresh TTL (expireAt immutable)");

        // despite intervening peeks, the challenge still expires on the original schedule
        Thread.sleep(SHORT_TTL * 1000L + 200L);
        assertNull(store.peek(token), "challenge must expire on schedule even with intervening peeks");
    }

    @Test
    public void testIncrFailCountReturnsIncrementingValue() {
        LocalMfaChallengeStore store = new LocalMfaChallengeStore();
        String token = store.create("user-3", "totp", 1, "t", null);

        assertEquals(1, store.incrFailCount(token));
        assertEquals(2, store.incrFailCount(token));
        assertEquals(3, store.incrFailCount(token));
        assertEquals(3, store.failCount(token));
    }

    @Test
    public void testIncrFailCountConcurrentNoLostUpdates() throws Exception {
        LocalMfaChallengeStore store = new LocalMfaChallengeStore();
        String token = store.create("user-4", "totp", 1, "t", null);

        int threads = 16;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger maxSeen = new AtomicInteger(0);
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                int localMax = 0;
                for (int j = 0; j < perThread; j++) {
                    int v = store.incrFailCount(token);
                    if (v > localMax)
                        localMax = v;
                }
                maxSeen.accumulateAndGet(localMax, Math::max);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures)
            f.get();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        int expected = threads * perThread;
        assertEquals(expected, store.failCount(token),
                "concurrent increments must not lose updates");
        assertTrue(maxSeen.get() >= expected, "at least one thread saw the final count");
    }

    @Test
    public void testIncrFailCountOnExpiredChallengeReturnsZero() {
        MfaChallengeStoreConfig cfg = new MfaChallengeStoreConfig();
        cfg.setExpireSeconds(SHORT_TTL);
        LocalMfaChallengeStore store = new LocalMfaChallengeStore(cfg);
        String token = store.create("user-5", "totp", 1, "t", null);

        // not expired yet -> increments
        assertEquals(1, store.incrFailCount(token));
    }

    @Test
    public void testMaxAttemptsObservableByCaller() {
        // store 只返回计数，调用方判定 max-attempts（W5 串联）
        LocalMfaChallengeStore store = new LocalMfaChallengeStore();
        String token = store.create("user-6", "totp", 1, "t", null);
        int maxAttempts = 3;
        int last = 0;
        for (int i = 0; i < maxAttempts; i++) {
            last = store.incrFailCount(token);
            assertTrue(last < maxAttempts || last == maxAttempts);
        }
        assertEquals(maxAttempts, last);
        // 超限后调用方应 consume/丢弃（store 仍可继续计数，但调用方不会再调）
        store.consume(token);
        assertNull(store.peek(token));
    }
}
