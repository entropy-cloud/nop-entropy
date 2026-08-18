/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.MfaChallengeStoreConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.dao.DaoConstants.DEFAULT_QUERY_SPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W8 Phase 2: {@link DbMfaChallengeStore} 行为等价性测试（对照 Local/Redis 语义）。
 * 覆盖 create/peek/incrFailCount/consume/过期清理/一次性消费 + 并发失败计数无丢失。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDbMfaChallengeStore extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJdbcTemplate jdbcTemplate;

    private DbMfaChallengeStore store(int expireSeconds) {
        DbMfaChallengeStore s = new DbMfaChallengeStore();
        s.daoProvider = daoProvider;
        s.jdbcTemplate = jdbcTemplate;
        MfaChallengeStoreConfig cfg = new MfaChallengeStoreConfig();
        cfg.setExpireSeconds(expireSeconds);
        s.setConfig(cfg);
        return s;
    }

    private int dbFailCount(String token) {
        SQL select = SQL.begin().name("assertFailCount").querySpace(DEFAULT_QUERY_SPACE)
                .sql("SELECT FAIL_COUNT FROM " + DbMfaChallengeStore.TABLE + " WHERE CHALLENGE_TOKEN = ?", token).end();
        Integer v = jdbcTemplate.findInt(select, null);
        return v == null ? -1 : v;
    }

    @Test
    public void testCreatePeekConsume() {
        DbMfaChallengeStore s = store(60);
        String token = s.create("user-1", "totp", 1, "t0", null);

        MfaChallenge peeked = s.peek(token);
        assertNotNull(peeked, "peek must hit after create");
        assertEquals("user-1", peeked.getUserId());
        assertEquals("totp", peeked.getMfaType());
        assertEquals(1, peeked.getLoginType());
        assertEquals("t0", peeked.getTenantId());
        assertTrue(peeked.getExpireAt() > System.currentTimeMillis(), "expireAt must be in the future");

        MfaChallenge consumed = s.consume(token);
        assertNotNull(consumed, "consume must return the challenge");
        assertEquals("user-1", consumed.getUserId());

        // one-time: second consume returns null
        assertNull(s.consume(token), "second consume must return null (one-time)");
        assertNull(s.peek(token), "peek after consume returns null");
    }

    @Test
    public void testPeekDoesNotRefreshTtl() {
        DbMfaChallengeStore s = store(60);
        String token = s.create("user-2", "sms", 5, "t1", "13800000000");
        long expire1 = s.peek(token).getExpireAt();
        // repeated peek must not change expireAt
        long expire2 = s.peek(token).getExpireAt();
        long expire3 = s.peek(token).getExpireAt();
        assertEquals(expire1, expire2, "peek must not refresh TTL");
        assertEquals(expire1, expire3, "peek must not refresh TTL");
    }

    @Test
    public void testIncrFailCount() {
        DbMfaChallengeStore s = store(60);
        String token = s.create("user-3", "totp", 1, "t0", null);

        assertEquals(1, s.incrFailCount(token));
        assertEquals(2, s.incrFailCount(token));
        assertEquals(3, s.incrFailCount(token));
        assertEquals(3, dbFailCount(token), "persisted failCount must be 3");
    }

    @Test
    public void testIncrFailCountOnMissingReturnsZero() {
        DbMfaChallengeStore s = store(60);
        assertEquals(0, s.incrFailCount("non-existent-token"), "missing token returns 0 (no silent skip)");
    }

    @Test
    public void testIncrFailCountConcurrentNoLoss() throws Exception {
        DbMfaChallengeStore s = store(120);
        String token = s.create("user-concurrent", "totp", 1, "t0", null);

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger nonZero = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    int val = s.incrFailCount(token);
                    if (val > 0)
                        nonZero.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "all increments must complete");
        pool.shutdownNow();

        // 并发断言：最终值 = 调用次数（SQL 原子递增无丢失）
        assertEquals(threads, dbFailCount(token), "concurrent incrFailCount final value must equal call count (no loss)");
        assertTrue(nonZero.get() > 0, "at least some increments must report a positive count");
    }

    @Test
    public void testTtlExpiry() throws InterruptedException {
        DbMfaChallengeStore s = store(1);
        String token = s.create("user-4", "sms", 5, "t0", "13800000000");
        Thread.sleep(1100L);
        assertNull(s.peek(token), "peek after TTL expiry returns null");
        // 惰性清理：行已删除
        assertEquals(-1, dbFailCount(token), "expired row must be lazily deleted on peek");
    }

    @Test
    public void testConsumeAfterExpiryReturnsNull() throws InterruptedException {
        DbMfaChallengeStore s = store(1);
        String token = s.create("user-5", "totp", 1, "t0", null);
        Thread.sleep(1100L);
        assertNull(s.consume(token), "consume after expiry returns null");
    }

    @Test
    public void testPeekMissingReturnsNull() {
        DbMfaChallengeStore s = store(60);
        assertNull(s.peek("never-created"), "peek on missing token returns null (no exception)");
    }

    // ===================== W12-impl Phase 2：场景化 + markVerified（设计 §3.3） =====================

    private Long dbVerifiedAt(String token) {
        SQL select = SQL.begin().name("assertVerifiedAt").querySpace(DEFAULT_QUERY_SPACE)
                .sql("SELECT VERIFIED_AT FROM " + DbMfaChallengeStore.TABLE + " WHERE CHALLENGE_TOKEN = ?", token).end();
        return jdbcTemplate.findLong(select, null);
    }

    @Test
    public void testSceneCreateOverloadPinsSceneAndPayload() {
        DbMfaChallengeStore s = store(60);
        String payload = "{\"operation\":\"NopAuthUser__resetUserMfa\",\"sessionId\":\"sess-1\"}";
        String token = s.create(MfaChallenge.SCENE_OPERATION, "op-user", "totp", 1, "t0", null, payload);

        MfaChallenge peeked = s.peek(token);
        assertNotNull(peeked, "scene=operation challenge must be creatable and peekable (DB)");
        assertEquals(MfaChallenge.SCENE_OPERATION, peeked.getScene(), "scene must be persisted (SCENE column)");
        assertEquals(payload, peeked.getPayload(), "payload must be persisted (PAYLOAD column)");
        assertNull(peeked.getVerifiedAt(), "freshly created challenge must be unverified");
    }

    @Test
    public void testLegacyFiveArgDelegatesToLoginScene() {
        DbMfaChallengeStore s = store(60);
        String token = s.create("legacy-user", "totp", 1, "t0", "13800000000");

        MfaChallenge peeked = s.peek(token);
        assertNotNull(peeked);
        assertEquals(MfaChallenge.SCENE_LOGIN, peeked.getScene(), "old 5-arg create delegates scene=login (DB)");
        assertNull(peeked.getPayload(), "old 5-arg create delegates payload=null (DB)");
    }

    @Test
    public void testMarkVerifiedExactlyOnceDb() {
        DbMfaChallengeStore s = store(60);
        String token = s.create(MfaChallenge.SCENE_OPERATION, "mv-user", "totp", 1, "t0", null, "{}");

        assertTrue(s.markVerified(token), "first markVerified must succeed (conditional UPDATE)");
        assertNotNull(dbVerifiedAt(token), "VERIFIED_AT column must be set");
        assertNotNull(s.peek(token).getVerifiedAt(), "peek must expose verifiedAt (raw read)");
        assertFalse(s.markVerified(token), "second markVerified must return false (VERIFIED_AT IS NULL fails)");
        assertFalse(s.markVerified("no-such-token"), "missing token returns false");
    }

    @Test
    public void testMarkVerifiedConcurrentExactlyOneWinnerDb() throws Exception {
        DbMfaChallengeStore s = store(120);
        String token = s.create(MfaChallenge.SCENE_OPERATION, "conc-user", "totp", 1, "t0", null, "{}");

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (s.markVerified(token))
                    winners.incrementAndGet();
                return null;
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<?> f : futures)
            f.get();
        pool.shutdown();

        assertEquals(1, winners.get(), "concurrent markVerified must have exactly one winner (affected-row semantics)");
    }

    @Test
    public void testTicketWindowVisibleOnlyWithinOpTicketExpireDb() {
        MfaChallengeStoreConfig cfg = new MfaChallengeStoreConfig();
        cfg.setExpireSeconds(60);
        cfg.setOpTicketExpireSeconds(60);
        DbMfaChallengeStore s = storeWithConfig(cfg);
        String token = s.create(MfaChallenge.SCENE_OPERATION, "win-user", "totp", 1, "t0", null, "{}");

        assertTrue(s.markVerified(token));
        MfaChallenge peeked = s.peek(token);
        assertNotNull(peeked, "within ticket window, peek must see the challenge (DB)");
        assertNotNull(peeked.getVerifiedAt(), "within window, peek().verifiedAt must be non-null (DB invariant)");
    }

    @Test
    public void testTicketWindowExpiryInvalidatesChallengeDb() throws InterruptedException {
        MfaChallengeStoreConfig cfg = new MfaChallengeStoreConfig();
        cfg.setExpireSeconds(60);
        cfg.setOpTicketExpireSeconds(1);
        DbMfaChallengeStore s = storeWithConfig(cfg);
        String token = s.create(MfaChallenge.SCENE_OPERATION, "win2-user", "totp", 1, "t0", null, "{}");

        assertTrue(s.markVerified(token));
        Thread.sleep(1200L);
        assertNull(s.peek(token), "after ticket window, ticket (and challenge) must be invalid (DB, 票不续命)");
        assertEquals(-1, dbFailCount(token), "expired ticket row must be lazily deleted");
    }

    @Test
    public void testConsumeOneTimeNotAffectedByMarkVerifiedDb() {
        DbMfaChallengeStore s = store(60);
        String token = s.create(MfaChallenge.SCENE_OPERATION, "c-user", "totp", 1, "t0", null, "{}");

        assertTrue(s.markVerified(token));
        MfaChallenge consumed = s.consume(token);
        assertNotNull(consumed, "consume after markVerified must still return the challenge (DB one-time unchanged)");
        assertNotNull(consumed.getVerifiedAt(), "consumed challenge carries verifiedAt (DB)");
        assertNull(s.consume(token), "second consume returns null (DB one-time)");
    }

    @Test
    public void testMarkVerifiedOnExpiredChallengeReturnsFalseDb() throws InterruptedException {
        DbMfaChallengeStore s = store(1);
        String token = s.create(MfaChallenge.SCENE_OPERATION, "exp-user", "totp", 1, "t0", null, "{}");
        Thread.sleep(1100L);
        assertFalse(s.markVerified(token), "markVerified on expired challenge must return false (EXPIRE_AT > now fails)");
    }

    private DbMfaChallengeStore storeWithConfig(MfaChallengeStoreConfig cfg) {
        DbMfaChallengeStore s = new DbMfaChallengeStore();
        s.daoProvider = daoProvider;
        s.jdbcTemplate = jdbcTemplate;
        s.setConfig(cfg);
        return s;
    }
}
