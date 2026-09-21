/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.login;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.context.ContextProvider;
import io.nop.auth.core.password.IPasswordEncoder;
import io.nop.auth.dao.entity.NopAuthMfaRecoveryCode;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.dao.DaoConstants.DEFAULT_QUERY_SPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2-followup-1 Phase 2 / D3-F2：恢复码 used 置位条件写的并发双花防御——
 * <ul>
 *   <li>顺序语义：VALID → USED（第二次同码不再通过）；错码 INVALID。</li>
 *   <li><b>并发双 verify 同码恰一次成功</b>（复刻 {@code TestRedisCodeStoreCasRace} 的对抗断言
 *       目标，改用真实多线程 + H2）：N 线程同时 verify 同一恢复码，恰一个 VALID，其余 USED。</li>
 *   <li>条件 UPDATE 语义钉定：同一 SID 两次执行 affected = 1, 0（被删行/已用行同走 affected=0
 *       → USED 分支，regenerate 竞态随之闭合）。</li>
 * </ul>
 * 已用码复活路径（改 used 回 0 的通用 CRUD）已被 Phase 1 收紧后不可达
 * （{@code TestMfaCrudLockdownE2E} 钉定）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestRecoveryCodeConditionalWrite extends JunitBaseTestCase {

    private static final String TENANT_ID = "0";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    IJdbcTemplate jdbcTemplate;

    @Inject
    LoginServiceImpl loginService;

    @Inject
    IPasswordEncoder passwordEncoder;

    @Test
    public void testSequentialVerifySemantics() {
        String userId = "d3f2-seq-user";
        saveUser(userId);
        String plain = insertRecoveryCode(userId, "d3f2-seq-1");

        assertEquals(io.nop.auth.service.mfa.LoginMfaFlow.RecoveryVerifyResult.VALID,
                ormTemplate.runInSession(s -> loginService.mfaFlow().verifyRecoveryCode(userId, plain)),
                "first verify of a fresh code must be VALID");
        assertEquals(io.nop.auth.service.mfa.LoginMfaFlow.RecoveryVerifyResult.USED,
                ormTemplate.runInSession(s -> loginService.mfaFlow().verifyRecoveryCode(userId, plain)),
                "second verify of the same code must be USED");
        assertEquals(io.nop.auth.service.mfa.LoginMfaFlow.RecoveryVerifyResult.INVALID,
                ormTemplate.runInSession(s -> loginService.mfaFlow().verifyRecoveryCode(userId, "0000000000")),
                "wrong code must be INVALID");
    }

    @Test
    public void testConcurrentDoubleVerifyExactlyOneSucceeds() throws Exception {
        String userId = "d3f2-race-user";
        saveUser(userId);
        String plain = insertRecoveryCode(userId, "d3f2-race-1");

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger validCount = new AtomicInteger();
        AtomicInteger usedCount = new AtomicInteger();
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    io.nop.auth.service.mfa.LoginMfaFlow.RecoveryVerifyResult r =
                            ormTemplate.runInSession(s -> loginService.mfaFlow().verifyRecoveryCode(userId, plain));
                    if (r == io.nop.auth.service.mfa.LoginMfaFlow.RecoveryVerifyResult.VALID) {
                        validCount.incrementAndGet();
                    } else if (r == io.nop.auth.service.mfa.LoginMfaFlow.RecoveryVerifyResult.USED) {
                        usedCount.incrementAndGet();
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "all racers must be ready");
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, validCount.get(), "concurrent double-spend: exactly one VALID winner");
        assertEquals(threads - 1, usedCount.get(),
                "losers must be adjudicated USED (affected=0 conditional write), not VALID");
        // 落库终态：USED=1 且 USED_AT 已置
        Byte used = ContextProvider.runWithTenant(TENANT_ID, () -> {
            NopAuthMfaRecoveryCode row = daoProvider.daoFor(NopAuthMfaRecoveryCode.class).getEntityById(
                    sidOf(userId, "d3f2-race-1"));
            return row == null ? null : row.getUsed();
        });
        assertNotNull(used);
        assertEquals(1, used.byteValue(), "row must be marked used after the race");
    }

    /** 条件 UPDATE 原子性钉定：同一 SID 两次执行 affected = 1, 0。 */
    @Test
    public void testConditionalUpdateIsOneShot() {
        String userId = "d3f2-cas-user";
        saveUser(userId);
        String plain = insertRecoveryCode(userId, "d3f2-cas-1");
        String sid = sidOf(userId, "d3f2-cas-1");

        assertEquals(1, execMarkUsed(sid), "first conditional write must affect the row");
        assertEquals(0, execMarkUsed(sid), "second conditional write must affect nothing (one-shot)");
    }

    private long execMarkUsed(String sid) {
        SQL upd = SQL.begin().name("testRecoveryMarkUsed").querySpace(DEFAULT_QUERY_SPACE)
                .sql("UPDATE nop_auth_mfa_recovery_code SET USED = 1, USED_AT = CURRENT_TIMESTAMP "
                        + "WHERE SID = ? AND USED = 0", sid)
                .end();
        return jdbcTemplate.executeUpdate(upd);
    }

    // ===================== Helpers =====================

    /** 插入一行已知明文的恢复码（BCrypt salt:hash，与 regenerateRecoveryCodes 同格式）。 */
    private String insertRecoveryCode(String userId, String marker) {
        String plain = io.nop.commons.util.StringHelper.generateUUID().replace("-", "").substring(0, 10);
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaRecoveryCode> dao = daoProvider.daoFor(NopAuthMfaRecoveryCode.class);
            NopAuthMfaRecoveryCode rc = dao.newEntity();
            rc.setSid(marker + "-" + userId);
            rc.setUserId(userId);
            String salt = passwordEncoder.generateSalt();
            rc.setCodeHash(salt + ":" + passwordEncoder.encodePassword(salt, plain));
            rc.setUsed((byte) 0);
            dao.saveEntity(rc);
            return null;
        });
        return plain;
    }

    private String sidOf(String userId, String marker) {
        return marker + "-" + userId;
    }

    private void saveUser(String userId) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
            if (dao.getEntityById(userId) == null) {
                NopAuthUser user = dao.newEntity();
                user.setUserId(userId);
                user.setUserName(userId);
                user.setNickName(userId);
                String salt = passwordEncoder.generateSalt();
                user.setPassword(passwordEncoder.encodePassword(salt, "123"));
                user.setSalt(salt);
                user.setOpenId(userId);
                user.setUserType(1);
                user.setStatus(1);
                user.setGender(1);
                user.setTenantId(TENANT_ID);
                dao.saveEntity(user);
            }
            return null;
        });
    }
}
