/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * W4 Phase 3 tests for the Local {@link SmsCodeStore} implementation:
 * three-state (VALID/EXPIRED/MISMATCH), atomic consume on success, fail-count invalidation,
 * and key isolation between login and mfa namespaces.
 */
public class TestLocalSmsCodeStore {

    @Test
    public void testValidConsumeOnSuccess() {
        LocalSmsCodeStore store = new LocalSmsCodeStore();
        String code = store.send("login:13800000000");

        assertEquals(CodeVerifyResult.VALID, store.verify("login:13800000000", code));
        // 已消费：同码再验返 EXPIRED（key 已被消费删除）
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("login:13800000000", code));
    }

    @Test
    public void testMismatchIncrementsFailCount() {
        SmsCodeStoreConfig cfg = new SmsCodeStoreConfig();
        cfg.setMaxAttempts(3);
        LocalSmsCodeStore store = new LocalSmsCodeStore(cfg);
        String code = store.send("mfa:user-1");

        assertEquals(CodeVerifyResult.MISMATCH, store.verify("mfa:user-1", "000000"));
        assertEquals(1, store.failCount("mfa:user-1"));
        assertEquals(CodeVerifyResult.MISMATCH, store.verify("mfa:user-1", "111111"));
        assertEquals(2, store.failCount("mfa:user-1"));

        // 第 3 次失败达上限 -> 作废，后续 verify 返 EXPIRED
        assertEquals(CodeVerifyResult.MISMATCH, store.verify("mfa:user-1", "222222"));
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("mfa:user-1", code),
                "after max-attempts the code is invalidated");
    }

    @Test
    public void testExpiredState() throws InterruptedException {
        SmsCodeStoreConfig cfg = new SmsCodeStoreConfig();
        cfg.setExpireSeconds(1);
        LocalSmsCodeStore store = new LocalSmsCodeStore(cfg);
        String code = store.send("login:13900000000");

        Thread.sleep(1200L);
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("login:13900000000", code));
    }

    @Test
    public void testVerifyAbsentKeyReturnsExpired() {
        LocalSmsCodeStore store = new LocalSmsCodeStore();
        // 未发送 / 已消费 / 已过期的 key 一律返回 EXPIRED
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("never-sent", "123456"));
    }

    @Test
    public void testKeyIsolationLoginVsMfa() {
        LocalSmsCodeStore store = new LocalSmsCodeStore();
        // 同一手机号 / 同一用户的两个 namespace 互不通用
        String loginCode = store.send("login:13800000000");
        String mfaCode = store.send("mfa:user-1");

        assertNotEquals(loginCode, store.rawCode("mfa:user-1"));
        // login 的码不能在 mfa 的 key 上验证
        assertEquals(CodeVerifyResult.MISMATCH, store.verify("mfa:user-1", loginCode));
        // mfa 的码不能在 login 的 key 上验证
        assertEquals(CodeVerifyResult.MISMATCH, store.verify("login:13800000000", mfaCode));

        // 各自的码在各自 key 上 VALID
        assertEquals(CodeVerifyResult.VALID, store.verify("login:13800000000", loginCode));
        assertEquals(CodeVerifyResult.VALID, store.verify("mfa:user-1", mfaCode));
    }

    @Test
    public void testSendOverwritesOldCode() {
        LocalSmsCodeStore store = new LocalSmsCodeStore();
        String code1 = store.send("login:13800000000");
        String code2 = store.send("login:13800000000");
        assertNotEquals(code1, code2);
        // 重发覆盖写：旧码不再匹配新 entry（MISMATCH），新码 VALID
        assertEquals(CodeVerifyResult.MISMATCH, store.verify("login:13800000000", code1),
                "old code no longer matches after resend (overwritten by new entry)");
        assertEquals(CodeVerifyResult.VALID, store.verify("login:13800000000", code2));
    }

    @Test
    public void testExplicitConsume() {
        LocalSmsCodeStore store = new LocalSmsCodeStore();
        String code = store.send("login:13800000000");
        store.consume("login:13800000000");
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("login:13800000000", code));
    }

    @Test
    public void testFailCountResetsOnResend() {
        SmsCodeStoreConfig cfg = new SmsCodeStoreConfig();
        cfg.setMaxAttempts(2);
        LocalSmsCodeStore store = new LocalSmsCodeStore(cfg);
        store.send("login:13800000000");
        store.verify("login:13800000000", "000000"); // fail 1
        assertEquals(1, store.failCount("login:13800000000"));

        // 重发后是新 entry，failCount 归零
        String code = store.send("login:13800000000");
        assertEquals(0, store.failCount("login:13800000000"));
        assertEquals(CodeVerifyResult.VALID, store.verify("login:13800000000", code));
    }
}
