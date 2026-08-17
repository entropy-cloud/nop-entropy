/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.auth.core.mfa.store.CodeVerifyResult;
import io.nop.auth.core.mfa.store.EmailCodeStoreConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W15-impl：Redis {@link RedisEmailCodeStore} 全生命周期（FakeNosqlService 内存替身——
 * 接线验证同型先例 {@code TestRedisStoreWiring}：send→putExAsync、verify→get（不刷新 TTL）、
 * VALID→removeIfMatch 原子消费、MISMATCH→counter.increment、超限作废、TTL 到期）。
 */
public class TestRedisEmailCodeStore {

    @Test
    public void testSendUsesPutExAndValidConsumesAtomically() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisEmailCodeStore store = new RedisEmailCodeStore(nosql, cfg(60, 5));
        String code = store.send("mfa-email:user-1");
        assertTrue(nosql.callCount("putExAsync") >= 1, "send must invoke putExAsync");

        assertEquals(CodeVerifyResult.VALID, store.verify("mfa-email:user-1", code));
        assertTrue(nosql.callCount("removeIfMatch") >= 1, "VALID verify must invoke removeIfMatch");
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("mfa-email:user-1", code),
                "consumed code must be EXPIRED afterwards");
    }

    @Test
    public void testMismatchIncrementsCounterAndInvalidates() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisEmailCodeStore store = new RedisEmailCodeStore(nosql, cfg(60, 2));
        String code = store.send("mfa-email:user-2");

        int beforeIncr = nosql.callCount("counter.increment");
        assertEquals(CodeVerifyResult.MISMATCH, store.verify("mfa-email:user-2", "000000"));
        assertTrue(nosql.callCount("counter.increment") > beforeIncr, "MISMATCH must increment counter");
        assertEquals(CodeVerifyResult.MISMATCH, store.verify("mfa-email:user-2", "111111"));
        // 第三次（已达上限 2）→ 作废
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("mfa-email:user-2", code),
                "after max-attempts the code is invalidated");
    }

    @Test
    public void testAbsentReturnsExpired() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisEmailCodeStore store = new RedisEmailCodeStore(nosql, cfg(60, 5));
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("never-sent", "123456"));
    }

    @Test
    public void testVerifyDoesNotRefreshTtl() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisEmailCodeStore store = new RedisEmailCodeStore(nosql, cfg(60, 5));
        store.send("mfa-email:user-3");
        store.verify("mfa-email:user-3", "000000"); // mismatch, reads entry
        assertEquals(0, nosql.callCount("getExAsync"), "verify must NOT invoke getExAsync");
        assertTrue(nosql.callCount("get") >= 1, "verify must invoke get");
    }

    @Test
    public void testRedisTtlExpiry() throws InterruptedException {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisEmailCodeStore store = new RedisEmailCodeStore(nosql, cfg(1, 5));
        String code = store.send("proof-email:user-4");
        Thread.sleep(1100L);
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("proof-email:user-4", code));
    }

    @Test
    public void testExplicitConsume() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisEmailCodeStore store = new RedisEmailCodeStore(nosql, cfg(60, 5));
        String code = store.send("proof-email:user-5");
        store.consume("proof-email:user-5");
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("proof-email:user-5", code));
    }

    private static EmailCodeStoreConfig cfg(int expireSeconds, int maxAttempts) {
        EmailCodeStoreConfig c = new EmailCodeStoreConfig();
        c.setExpireSeconds(expireSeconds);
        c.setMaxAttempts(maxAttempts);
        return c;
    }
}
