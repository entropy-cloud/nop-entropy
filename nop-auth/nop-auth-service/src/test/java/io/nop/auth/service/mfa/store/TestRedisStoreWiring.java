/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.auth.core.mfa.store.CodeVerifyResult;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.MfaChallengeStoreConfig;
import io.nop.auth.core.mfa.store.SmsCodeStoreConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W4 Phase 3: drives the Redis store classes through their full lifecycle against an
 * in-memory {@link FakeNosqlService}. This provides the "接线验证" evidence (Plan Phase 3
 * Exit Criteria) that the Redis stores delegate to the correct nop-nosql primitives:
 * create→putExAsync, peek→get (NOT getExAsync), consume→removeIfMatch,
 * incrFailCount→counter.increment.
 * <p>
 * Real Redis connectivity is covered by nop-nosql's own tests; here we verify the store
 * control flow, key construction, TTL handling, CAS consume, and primitive selection.
 */
public class TestRedisStoreWiring {

    @Test
    public void testChallengeCreatePeekConsumeAndPrimitiveSelection() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisMfaChallengeStore store = new RedisMfaChallengeStore(nosql, cfg(60));

        String token = store.create("user-1", "totp", 1, "t", null);
        assertTrue(nosql.callCount("putExAsync") >= 1, "create must invoke putExAsync");

        MfaChallenge peeked = store.peek(token);
        assertNotNull(peeked, "peek must hit after create");
        assertEquals("user-1", peeked.getUserId());

        // 接线断言：peek 用 get，不用 getExAsync（避免 GETEX 滑动刷新 TTL）
        assertTrue(nosql.callCount("get") >= 1, "peek must invoke get");
        assertEquals(0, nosql.callCount("getExAsync"), "peek must NOT invoke getExAsync");

        MfaChallenge consumed = store.consume(token);
        assertNotNull(consumed);
        assertTrue(nosql.callCount("removeIfMatch") >= 1, "consume must invoke removeIfMatch");
        assertNull(store.peek(token), "peek after consume returns null");
    }

    @Test
    public void testChallengeIncrFailCountUsesCounterIncrement() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisMfaChallengeStore store = new RedisMfaChallengeStore(nosql, cfg(60));
        String token = store.create("user-2", "totp", 1, "t", null);

        int beforeCounter = nosql.callCount("counter");
        int beforeIncr = nosql.callCount("counter.increment");
        assertEquals(1, store.incrFailCount(token));
        assertEquals(2, store.incrFailCount(token));
        assertTrue(nosql.callCount("counter") > beforeCounter, "incrFailCount must obtain a counter");
        assertTrue(nosql.callCount("counter.increment") >= beforeIncr + 2,
                "incrFailCount must invoke counter.increment per call");
        // 首次递增设定 TTL（setTimeoutAsync 调用一次）
        assertTrue(nosql.callCount("setTimeoutAsync") >= 1, "first increment sets counter TTL via setTimeoutAsync");
    }

    @Test
    public void testChallengeRedisTtlExpiry() throws InterruptedException {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisMfaChallengeStore store = new RedisMfaChallengeStore(nosql, cfg(1));
        String token = store.create("user-3", "sms", 5, "t", "13800000000");

        Thread.sleep(1100L);
        assertNull(store.peek(token), "challenge must be gone after Redis TTL expiry");
    }

    @Test
    public void testSmsCodeThreeStatesAndConsume() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisSmsCodeStore store = new RedisSmsCodeStore(nosql, smsCfg(60, 3));
        String code = store.send("login:13800000000");
        assertTrue(nosql.callCount("putExAsync") >= 1, "send must invoke putExAsync");

        // VALID: matched → atomic consume (removeIfMatch)
        assertEquals(CodeVerifyResult.VALID, store.verify("login:13800000000", code));
        assertTrue(nosql.callCount("removeIfMatch") >= 1, "VALID verify must invoke removeIfMatch");
        // consumed → EXPIRED
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("login:13800000000", code));
    }

    @Test
    public void testSmsCodeMismatchIncrementsCounterAndInvalidates() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisSmsCodeStore store = new RedisSmsCodeStore(nosql, smsCfg(60, 2));
        String code = store.send("mfa:user-1");

        int beforeIncr = nosql.callCount("counter.increment");
        assertEquals(CodeVerifyResult.MISMATCH, store.verify("mfa:user-1", "000000"));
        assertTrue(nosql.callCount("counter.increment") > beforeIncr, "MISMATCH must increment counter");
        assertEquals(CodeVerifyResult.MISMATCH, store.verify("mfa:user-1", "111111"));
        // 第三次（已达上限 2）→ 作废
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("mfa:user-1", code),
                "after max-attempts the code is invalidated");
    }

    @Test
    public void testSmsCodeAbsentReturnsExpired() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisSmsCodeStore store = new RedisSmsCodeStore(nosql, smsCfg(60, 3));
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("never-sent", "123456"));
    }

    @Test
    public void testSmsCodeRedisTtlExpiry() throws InterruptedException {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisSmsCodeStore store = new RedisSmsCodeStore(nosql, smsCfg(1, 3));
        String code = store.send("login:13900000000");
        Thread.sleep(1100L);
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("login:13900000000", code));
    }

    @Test
    public void testSmsCodePeekDoesNotRefreshTtl() {
        // verify 路径用 get（不刷新 TTL），非 getExAsync
        FakeNosqlService nosql = new FakeNosqlService();
        RedisSmsCodeStore store = new RedisSmsCodeStore(nosql, smsCfg(60, 3));
        store.send("login:13800000000");
        store.verify("login:13800000000", "000000"); // mismatch, reads entry
        assertEquals(0, nosql.callCount("getExAsync"), "verify must NOT invoke getExAsync");
        assertTrue(nosql.callCount("get") >= 1, "verify must invoke get");
    }

    private static MfaChallengeStoreConfig cfg(int expireSeconds) {
        MfaChallengeStoreConfig c = new MfaChallengeStoreConfig();
        c.setExpireSeconds(expireSeconds);
        return c;
    }

    private static SmsCodeStoreConfig smsCfg(int expireSeconds, int maxAttempts) {
        SmsCodeStoreConfig c = new SmsCodeStoreConfig();
        c.setExpireSeconds(expireSeconds);
        c.setMaxAttempts(maxAttempts);
        return c;
    }
}
