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
import io.nop.auth.core.mfa.store.CodeVerifyResult;
import io.nop.auth.core.mfa.store.SmsCodeStoreConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.nop.dao.DaoConstants.DEFAULT_QUERY_SPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W8 Phase 2: {@link DbSmsCodeStore} 三态校验 + 消费 + 失败计数测试（对照 Local/Redis 语义）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDbSmsCodeStore extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    private DbSmsCodeStore store(int expireSeconds, int maxAttempts) {
        DbSmsCodeStore s = new DbSmsCodeStore();
        s.daoProvider = daoProvider;
        s.ormTemplate = ormTemplate;
        SmsCodeStoreConfig cfg = new SmsCodeStoreConfig();
        cfg.setExpireSeconds(expireSeconds);
        cfg.setMaxAttempts(maxAttempts);
        s.setConfig(cfg);
        return s;
    }

    private int dbFailCount(String key) {
        SQL select = SQL.begin().name("assertSmsFailCount").querySpace(DEFAULT_QUERY_SPACE)
                .sql("select o.failCount from NopAuthSmsCode o where o.codeKey = ?", key).end();
        Integer v = ormTemplate.findInt(select, null);
        return v == null ? -1 : v;
    }

    @Test
    public void testSendProducesSixDigitCode() {
        DbSmsCodeStore s = store(60, 5);
        for (int i = 0; i < 20; i++) {
            String code = s.send("login:1380000000" + (i % 10));
            assertEquals(6, code.length(), "code must be 6 digits");
            assertTrue(code.matches("\\d{6}"), "code must be numeric");
        }
    }

    @Test
    public void testValidConsumeAndThenExpired() {
        DbSmsCodeStore s = store(60, 5);
        String key = "login:13800000000";
        String code = s.send(key);

        // VALID: matched → atomic consume
        assertEquals(CodeVerifyResult.VALID, s.verify(key, code));
        // consumed → EXPIRED on next verify
        assertEquals(CodeVerifyResult.EXPIRED, s.verify(key, code));
        assertEquals(-1, dbFailCount(key), "row must be deleted after VALID consume");
    }

    @Test
    public void testMismatchIncrementsFailCount() {
        DbSmsCodeStore s = store(60, 5);
        String key = "mfa:user-1";
        String code = s.send(key);

        assertEquals(CodeVerifyResult.MISMATCH, s.verify(key, "000000"));
        assertEquals(1, dbFailCount(key), "first mismatch increments failCount to 1");
        assertEquals(CodeVerifyResult.MISMATCH, s.verify(key, "111111"));
        assertEquals(2, dbFailCount(key), "second mismatch increments failCount to 2");
        // correct code still works (hasn't hit max-attempts)
        assertEquals(CodeVerifyResult.VALID, s.verify(key, code));
    }

    @Test
    public void testMismatchAtMaxAttemptsInvalidates() {
        DbSmsCodeStore s = store(60, 2);
        String key = "mfa:user-2";
        String code = s.send(key);

        assertEquals(CodeVerifyResult.MISMATCH, s.verify(key, "000000"));
        assertEquals(CodeVerifyResult.MISMATCH, s.verify(key, "111111"));
        // failCount reached maxAttempts=2 → code invalidated
        assertEquals(CodeVerifyResult.EXPIRED, s.verify(key, code),
                "after max-attempts the code is invalidated, even with correct code");
    }

    @Test
    public void testAbsentReturnsExpired() {
        DbSmsCodeStore s = store(60, 5);
        assertEquals(CodeVerifyResult.EXPIRED, s.verify("never-sent", "123456"));
    }

    @Test
    public void testEmptyCodeReturnsMismatch() {
        DbSmsCodeStore s = store(60, 5);
        String key = "login:13900000000";
        s.send(key);
        assertEquals(CodeVerifyResult.MISMATCH, s.verify(key, ""));
        assertEquals(CodeVerifyResult.MISMATCH, s.verify(key, null));
    }

    @Test
    public void testTtlExpiry() throws InterruptedException {
        DbSmsCodeStore s = store(1, 5);
        String key = "login:13700000000";
        String code = s.send(key);
        Thread.sleep(1100L);
        assertEquals(CodeVerifyResult.EXPIRED, s.verify(key, code), "expired code returns EXPIRED");
    }

    @Test
    public void testResendInvalidatesOldCode() {
        DbSmsCodeStore s = store(60, 5);
        String key = "login:13600000000";
        String code1 = s.send(key);
        String code2 = s.send(key);
        assertNotEquals(code1, code2, "resend must produce a new code");
        // old code no longer valid (overwritten)
        assertEquals(CodeVerifyResult.MISMATCH, s.verify(key, code1), "old code must be invalidated by resend");
        // new code works
        assertEquals(CodeVerifyResult.VALID, s.verify(key, code2));
    }

    @Test
    public void testExplicitConsume() {
        DbSmsCodeStore s = store(60, 5);
        String key = "login:13500000000";
        String code = s.send(key);
        s.consume(key);
        assertEquals(CodeVerifyResult.EXPIRED, s.verify(key, code), "explicit consume deletes the code");
    }
}
