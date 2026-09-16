/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.time.IClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W15-impl tests for the Local {@link EmailCodeStore} implementation（设计 §5.3.3——
 * 三态/原子消费/失败计数超限作废/TTL/key 通道隔离，对齐 TestLocalSmsCodeStore 家族）。
 */
public class TestLocalEmailCodeStore {

    private IClock originalClock;

    @AfterEach
    public void restoreClock() {
        if (originalClock != null) {
            CoreMetrics.registerClock(originalClock);
        }
    }

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
    public void testExpiredState() {
        originalClock = CoreMetrics.defaultClock();
        MockClock clock = MockClock.now();
        CoreMetrics.registerClock(clock);

        EmailCodeStoreConfig cfg = new EmailCodeStoreConfig();
        cfg.setExpireSeconds(1);
        LocalEmailCodeStore store = new LocalEmailCodeStore(cfg);
        String code = store.send("mfa-email:user-2");

        clock.advanceSeconds(2);
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

    // ======================= 未访问条目的有界性 =======================

    /** 反射读取内部 map 规模：修复前后的行为都可观测（默认 maxEntries=100000）。 */
    private static int internalMapSize(Object store, String fieldName) throws Exception {
        java.lang.reflect.Field f = store.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        java.util.Map<?, ?> map = (java.util.Map<?, ?>) f.get(store);
        return map.size();
    }

    @Test
    public void testUnaccessedEntriesDoNotAccumulateUnboundedly() throws Exception {
        // "发出去但从未回来验证"是常态（用户放弃/短信未达），未访问的过期条目不得永久驻留：
        // 大量互异 key 的 send 必须被容量上限封顶
        LocalEmailCodeStore store = new LocalEmailCodeStore();
        for (int i = 0; i < 100_500; i++) {
            store.send("mfa-email:flood-" + i);
        }
        int size = internalMapSize(store, "codes");
        assertTrue(size <= 100_000,
                "惰性清理之外必须有容量上界（防公网 send 接口刷库 OOM），实际=" + size);
    }

    @Test
    public void testCapacityCapWithSmallLimit_andExpiredSweepPreferred() throws Exception {
        // 小容量上限：清扫优先、逐条上界封顶；功能语义（验证码可验证）不受影响
        EmailCodeStoreConfig cfg = new EmailCodeStoreConfig();
        cfg.setMaxEntries(100);
        LocalEmailCodeStore store = new LocalEmailCodeStore(cfg);
        for (int i = 0; i < 350; i++) {
            store.send("mfa-email:cap-" + i);
        }
        int size = internalMapSize(store, "codes");
        assertTrue(size <= 100, "容量上限必须生效，实际=" + size);

        // 存活条目仍可用
        String code = store.send("mfa-email:alive");
        assertEquals(CodeVerifyResult.VALID, store.verify("mfa-email:alive", code));
    }
}
