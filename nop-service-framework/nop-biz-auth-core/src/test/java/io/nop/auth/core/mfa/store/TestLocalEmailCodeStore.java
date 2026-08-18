/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * W15-impl tests for the Local {@link EmailCodeStore} implementation（设计 §5.3.3——
 * 三态/原子消费/失败计数超限作废/TTL/key 通道隔离，对齐 TestLocalSmsCodeStore 家族）。
 */
public class TestLocalEmailCodeStore {

    @Test
    public void testValidConsumeOnSuccess() {
        LocalEmailCodeStore store = new LocalEmailCodeStore();
        String code = store.send("mfa-email:user-1");

        assertEquals(CodeVerifyResult.VALID, store.verify("mfa-email:user-1", code));
        // 已消费：同码再验返 EXPIRED（key 已被消费删除）
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("mfa-email:user-1", code));
    }

    @Test
    public void testMismatchIncrementsFailCount() {
        EmailCodeStoreConfig cfg = new EmailCodeStoreConfig();
        cfg.setMaxAttempts(3);
        LocalEmailCodeStore store = new LocalEmailCodeStore(cfg);
        String code = store.send("mfa-email:user-1");

        assertEquals(CodeVerifyResult.MISMATCH, store.verify("mfa-email:user-1", "000000"));
        assertEquals(1, store.failCount("mfa-email:user-1"));
        assertEquals(CodeVerifyResult.MISMATCH, store.verify("mfa-email:user-1", "111111"));
        assertEquals(2, store.failCount("mfa-email:user-1"));

        // 第 3 次失败达上限 -> 作废，后续 verify 返 EXPIRED
        assertEquals(CodeVerifyResult.MISMATCH, store.verify("mfa-email:user-1", "222222"));
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("mfa-email:user-1", code),
                "after max-attempts the code is invalidated");
    }

    @Test
    public void testExpiredState() throws InterruptedException {
        EmailCodeStoreConfig cfg = new EmailCodeStoreConfig();
        cfg.setExpireSeconds(1);
        LocalEmailCodeStore store = new LocalEmailCodeStore(cfg);
        String code = store.send("mfa-email:user-2");

        Thread.sleep(1200L);
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("mfa-email:user-2", code));
    }

    @Test
    public void testVerifyAbsentKeyReturnsExpired() {
        LocalEmailCodeStore store = new LocalEmailCodeStore();
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("never-sent", "123456"));
    }

    @Test
    public void testKeyIsolationMfaVsProof() {
        LocalEmailCodeStore store = new LocalEmailCodeStore();
        // 同一用户的 mfa 与 proof 两个 namespace 互不通用（通道隔离纪律）
        String mfaCode = store.send("mfa-email:user-1");
        String proofCode = store.send("proof-email:user-1");

        assertNotEquals(mfaCode, store.rawCode("proof-email:user-1"));
        assertEquals(CodeVerifyResult.MISMATCH, store.verify("proof-email:user-1", mfaCode));
        assertEquals(CodeVerifyResult.MISMATCH, store.verify("mfa-email:user-1", proofCode));
        assertEquals(CodeVerifyResult.VALID, store.verify("mfa-email:user-1", mfaCode));
        assertEquals(CodeVerifyResult.VALID, store.verify("proof-email:user-1", proofCode));
    }

    @Test
    public void testSendOverwritesOldCode() {
        LocalEmailCodeStore store = new LocalEmailCodeStore();
        String code1 = store.send("mfa-email:user-3");
        String code2 = store.send("mfa-email:user-3");
        assertNotEquals(code1, code2);
        assertEquals(CodeVerifyResult.MISMATCH, store.verify("mfa-email:user-3", code1),
                "old code no longer matches after resend (overwritten by new entry)");
        assertEquals(CodeVerifyResult.VALID, store.verify("mfa-email:user-3", code2));
    }

    @Test
    public void testExplicitConsume() {
        LocalEmailCodeStore store = new LocalEmailCodeStore();
        String code = store.send("mfa-email:user-4");
        store.consume("mfa-email:user-4");
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("mfa-email:user-4", code));
    }

    @Test
    public void testFailCountResetsOnResend() {
        EmailCodeStoreConfig cfg = new EmailCodeStoreConfig();
        cfg.setMaxAttempts(2);
        LocalEmailCodeStore store = new LocalEmailCodeStore(cfg);
        store.send("mfa-email:user-5");
        store.verify("mfa-email:user-5", "000000"); // fail 1
        assertEquals(1, store.failCount("mfa-email:user-5"));

        // 重发后是新 entry，failCount 归零
        String code = store.send("mfa-email:user-5");
        assertEquals(0, store.failCount("mfa-email:user-5"));
        assertEquals(CodeVerifyResult.VALID, store.verify("mfa-email:user-5", code));
    }
}
