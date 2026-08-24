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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // ===================== W12-impl Phase 2：场景化 + markVerified（设计 §3.3） =====================

    @Test
    public void testSceneCreateOverloadPinsSceneAndPayload() {
        LocalMfaChallengeStore store = new LocalMfaChallengeStore();
        String payload = "{\"operation\":\"NopAuthUser__resetUserMfa\",\"sessionId\":\"sess-1\"}";
        String token = store.create(MfaChallenge.SCENE_OPERATION, "op-user", "totp", 1, "t0", null, payload);

        MfaChallenge peeked = store.peek(token);
        assertNotNull(peeked, "scene=operation challenge must be creatable and peekable");
        assertEquals(MfaChallenge.SCENE_OPERATION, peeked.getScene(), "scene must be pinned by overload");
        assertEquals(payload, peeked.getPayload(), "payload must be pinned by overload");
        assertNull(peeked.getVerifiedAt(), "freshly created challenge must be unverified");
    }

    @Test
    public void testLegacyFiveArgDelegatesToLoginScene() {
        LocalMfaChallengeStore store = new LocalMfaChallengeStore();
        String token = store.create("legacy-user", "totp", 1, "t0", "13800000000");

        MfaChallenge peeked = store.peek(token);
        assertNotNull(peeked);
        assertEquals(MfaChallenge.SCENE_LOGIN, peeked.getScene(), "old 5-arg create delegates scene=login");
        assertNull(peeked.getPayload(), "old 5-arg create delegates payload=null");
    }

    @Test
    public void testMarkVerifiedExactlyOnce() {
        LocalMfaChallengeStore store = new LocalMfaChallengeStore();
        String token = store.create(MfaChallenge.SCENE_OPERATION, "mv-user", "totp", 1, "t0", null, "{}");

        assertTrue(store.markVerified(token), "first markVerified must succeed");
        assertNotNull(store.peek(token).getVerifiedAt(), "peek must expose verifiedAt after markVerified");
        assertFalse(store.markVerified(token), "second markVerified must return false (ticket not renewed)");
        assertFalse(store.markVerified(token), "repeated markVerified keeps returning false");
        assertFalse(store.markVerified("no-such-token"), "missing token returns false");
    }

    @Test
    public void testMarkVerifiedConcurrentExactlyOneWinner() throws Exception {
        LocalMfaChallengeStore store = new LocalMfaChallengeStore();
        String token = store.create(MfaChallenge.SCENE_OPERATION, "conc-user", "totp", 1, "t0", null, "{}");

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (store.markVerified(token))
                    winners.incrementAndGet();
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures)
            f.get();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, winners.get(), "concurrent markVerified must have exactly one winner (JVM atomic compute)");
    }

    @Test
    public void testTicketWindowVisibleOnlyWithinOpTicketExpire() {
        MfaChallengeStoreConfig cfg = new MfaChallengeStoreConfig();
        cfg.setExpireSeconds(60); // challenge 自身 TTL 足够长
        cfg.setOpTicketExpireSeconds(SHORT_TTL); // 票窗口 1s
        LocalMfaChallengeStore store = new LocalMfaChallengeStore(cfg);
        String token = store.create(MfaChallenge.SCENE_OPERATION, "win-user", "totp", 1, "t0", null, "{}");

        assertTrue(store.markVerified(token));
        assertNotNull(store.peek(token), "within ticket window, peek must see the challenge");
        assertNotNull(store.peek(token).getVerifiedAt(),
                "within ticket window, peek().verifiedAt must be non-null (invariant: non-null ⇒ in window)");
    }

    @Test
    public void testTicketWindowExpiryInvalidatesChallenge() throws InterruptedException {
        MfaChallengeStoreConfig cfg = new MfaChallengeStoreConfig();
        cfg.setExpireSeconds(60);
        cfg.setOpTicketExpireSeconds(SHORT_TTL);
        LocalMfaChallengeStore store = new LocalMfaChallengeStore(cfg);
        String token = store.create(MfaChallenge.SCENE_OPERATION, "win2-user", "totp", 1, "t0", null, "{}");

        assertTrue(store.markVerified(token));
        Thread.sleep(SHORT_TTL * 1000L + 200L);
        assertNull(store.peek(token), "after ticket window, ticket (and challenge) must be invalid (票不续命)");
        assertFalse(store.markVerified(token), "markVerified after ticket expiry returns false");
    }

    @Test
    public void testConsumeOneTimeNotAffectedByMarkVerified() {
        LocalMfaChallengeStore store = new LocalMfaChallengeStore();
        String token = store.create(MfaChallenge.SCENE_OPERATION, "c-user", "totp", 1, "t0", null, "{}");

        assertTrue(store.markVerified(token), "markVerified must succeed");
        MfaChallenge consumed = store.consume(token);
        assertNotNull(consumed, "consume after markVerified must still return the challenge (one-time unchanged)");
        assertNotNull(consumed.getVerifiedAt(), "consumed challenge carries verifiedAt");
        assertNull(store.consume(token), "second consume returns null (one-time)");
        assertNull(store.peek(token), "peek after consume returns null");
    }

    @Test
    public void testMarkVerifiedOnExpiredChallengeReturnsFalse() throws InterruptedException {
        MfaChallengeStoreConfig cfg = new MfaChallengeStoreConfig();
        cfg.setExpireSeconds(SHORT_TTL);
        LocalMfaChallengeStore store = new LocalMfaChallengeStore(cfg);
        String token = store.create(MfaChallenge.SCENE_OPERATION, "exp-user", "totp", 1, "t0", null, "{}");
        Thread.sleep(SHORT_TTL * 1000L + 200L);
        assertFalse(store.markVerified(token), "markVerified on expired challenge must return false");
    }

    // ======================= 未访问条目的有界性 =======================

    /** 反射读取内部 map 规模：修复前后的行为都可观测（默认 maxEntries=100000）。 */
    private static int internalMapSize(Object store, String fieldName) throws Exception {
        java.lang.reflect.Field f = store.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        java.util.Map<?, ?> map = (java.util.Map<?, ?>) f.get(store);
        return map.size();
    }

    @Test
    public void testUnaccessedChallengesDoNotAccumulateUnboundedly() throws Exception {
        // "发起但从未回来验证"的 challenge 不得永久驻留：大量互异 token 的 create 必须被容量上限封顶
        LocalMfaChallengeStore store = new LocalMfaChallengeStore();
        for (int i = 0; i < 100_500; i++) {
            store.create(MfaChallenge.SCENE_LOGIN, "flood-" + i, "totp", 1, "t0", null, "{}");
        }
        int size = internalMapSize(store, "challenges");
        assertTrue(size <= 100_000,
                "惰性清理之外必须有容量上界（防公网 create 接口刷库 OOM），实际=" + size);
    }

    @Test
    public void testCapacityCapWithSmallLimit() throws Exception {
        MfaChallengeStoreConfig cfg = new MfaChallengeStoreConfig();
        cfg.setMaxEntries(100);
        LocalMfaChallengeStore store = new LocalMfaChallengeStore(cfg);
        for (int i = 0; i < 350; i++) {
            store.create(MfaChallenge.SCENE_LOGIN, "cap-" + i, "totp", 1, "t0", null, "{}");
        }
        int size = internalMapSize(store, "challenges");
        assertTrue(size <= 100, "容量上限必须生效，实际=" + size);

        // 存活 challenge 仍可正常 peek/consume
        String token = store.create(MfaChallenge.SCENE_LOGIN, "alive-user", "totp", 1, "t0", null, "{}");
        assertNotNull(store.peek(token));
        assertNotNull(store.consume(token));
    }
}
