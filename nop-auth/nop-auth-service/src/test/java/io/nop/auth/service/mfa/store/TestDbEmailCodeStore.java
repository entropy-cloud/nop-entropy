/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.auth.core.mfa.store.CodeVerifyResult;
import io.nop.auth.core.mfa.store.EmailCodeStoreConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.nop.dao.DaoConstants.DEFAULT_QUERY_SPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W15-impl: {@link DbEmailCodeStore} 三态校验 + 消费 + 失败计数测试（对照
 * {@code TestDbSmsCodeStore} 语义——设计 §5.3.3 表结构对齐 nop_auth_sms_code）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDbEmailCodeStore extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    private DbEmailCodeStore store(int expireSeconds, int maxAttempts) {
        DbEmailCodeStore s = new DbEmailCodeStore();
        s.daoProvider = daoProvider;
        s.ormTemplate = ormTemplate;
        EmailCodeStoreConfig cfg = new EmailCodeStoreConfig();
        cfg.setExpireSeconds(expireSeconds);
        cfg.setMaxAttempts(maxAttempts);
        s.setConfig(cfg);
        return s;
    }

    private int dbFailCount(String key) {
        SQL select = SQL.begin().name("assertEmailFailCount").querySpace(DEFAULT_QUERY_SPACE)
                .sql("select o.failCount from NopAuthEmailCode o where o.codeKey = ?", key).end();
        Integer v = ormTemplate.findInt(select, null);
        return v == null ? -1 : v;
    }

    @Test
    public void testSendProducesSixDigitCode() {
        DbEmailCodeStore s = store(60, 5);
        for (int i = 0; i < 10; i++) {
            String code = s.send("mfa-email:user-" + i);
            assertEquals(6, code.length(), "code must be 6 digits");
            assertTrue(code.matches("\\d{6}"), "code must be numeric");
        }
    }

    @Test
    public void testValidConsumeAndThenExpired() {
        DbEmailCodeStore s = store(60, 5);
        String key = "mfa-email:consume-user";
        String code = s.send(key);

        assertEquals(CodeVerifyResult.VALID, s.verify(key, code));
        assertEquals(CodeVerifyResult.EXPIRED, s.verify(key, code));
        assertEquals(-1, dbFailCount(key), "row must be deleted after VALID consume");
    }

    @Test
    public void testMismatchIncrementsFailCount() {
        DbEmailCodeStore s = store(60, 5);
        String key = "mfa-email:mismatch-user";
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
        DbEmailCodeStore s = store(60, 2);
        String key = "mfa-email:limit-user";
        String code = s.send(key);

        assertEquals(CodeVerifyResult.MISMATCH, s.verify(key, "000000"));
        assertEquals(CodeVerifyResult.MISMATCH, s.verify(key, "111111"));
        assertEquals(CodeVerifyResult.EXPIRED, s.verify(key, code),
                "after max-attempts the code is invalidated, even with correct code");
    }

    @Test
    public void testAbsentReturnsExpired() {
        DbEmailCodeStore s = store(60, 5);
        assertEquals(CodeVerifyResult.EXPIRED, s.verify("never-sent-email", "123456"));
    }

    @Test
    public void testEmptyCodeReturnsMismatch() {
        DbEmailCodeStore s = store(60, 5);
        String key = "proof-email:empty-user";
        s.send(key);
        assertEquals(CodeVerifyResult.MISMATCH, s.verify(key, ""));
        assertEquals(CodeVerifyResult.MISMATCH, s.verify(key, null));
    }

    @Test
    public void testExplicitConsume() {
        DbEmailCodeStore s = store(60, 5);
        String key = "proof-email:consume-user";
        String code = s.send(key);
        s.consume(key);
        assertEquals(CodeVerifyResult.EXPIRED, s.verify(key, code));
    }
}
