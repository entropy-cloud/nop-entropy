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
}
