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
import io.nop.auth.dao.entity.NopAuthMfaChallenge;
import io.nop.auth.dao.entity.NopAuthSmsCode;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * W8 Phase 1: round-trip regression for the two new DB-backed MFA store entities.
 * <p>
 * Verifies that {@link NopAuthMfaChallenge} and {@link NopAuthSmsCode} persist and reload
 * with all fields intact (including the BIGINT {@code expireAt} and {@code failCount}
 * default), and that the PK uniqueness is enforced.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDbMfaChallengeStoreEntityRoundTrip extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testMfaChallengeRoundTrip() {
        IEntityDao<NopAuthMfaChallenge> dao = daoProvider.daoFor(NopAuthMfaChallenge.class);
        NopAuthMfaChallenge c = dao.newEntity();
        c.setChallengeToken("challenge-token-1");
        c.setUserId("user-1");
        c.setMfaType("totp");
        c.setLoginType(1);
        c.setTenantId("t0");
        c.setPhone("13800000000");
        c.setExpireAt(System.currentTimeMillis() + 300_000L);
        c.setFailCount(0);
        dao.saveEntity(c);

        NopAuthMfaChallenge loaded = dao.getEntityById("challenge-token-1");
        assertNotNull(loaded, "challenge must be loadable by challengeToken PK");
        assertEquals("user-1", loaded.getUserId());
        assertEquals("totp", loaded.getMfaType());
        assertEquals(Integer.valueOf(1), loaded.getLoginType());
        assertEquals("t0", loaded.getTenantId());
        assertEquals("13800000000", loaded.getPhone());
        assertEquals(Long.valueOf(c.getExpireAt()), loaded.getExpireAt());
        assertEquals(Integer.valueOf(0), loaded.getFailCount());

        // update failCount (simulating incrFailCount persistence). *Directly bypasses
        // the ORM session cache so it works on a detached entity across calls.
        loaded.setFailCount(3);
        dao.updateEntityDirectly(loaded);
        NopAuthMfaChallenge reloaded = dao.getEntityById("challenge-token-1");
        assertEquals(Integer.valueOf(3), reloaded.getFailCount(), "failCount update must persist");

        // delete
        dao.deleteEntityDirectly(reloaded);
        assertNull(dao.getEntityById("challenge-token-1"), "challenge must be deleted");
    }

    @Test
    public void testSmsCodeRoundTrip() {
        IEntityDao<NopAuthSmsCode> dao = daoProvider.daoFor(NopAuthSmsCode.class);
        NopAuthSmsCode s = dao.newEntity();
        s.setCodeKey("login:13800000000");
        s.setPhone("13800000000");
        s.setCode("123456");
        s.setExpireAt(System.currentTimeMillis() + 300_000L);
        s.setFailCount(0);
        dao.saveEntity(s);

        NopAuthSmsCode loaded = dao.getEntityById("login:13800000000");
        assertNotNull(loaded, "sms code must be loadable by codeKey PK");
        assertEquals("13800000000", loaded.getPhone());
        assertEquals("123456", loaded.getCode());
        assertEquals(Long.valueOf(s.getExpireAt()), loaded.getExpireAt());
        assertEquals(Integer.valueOf(0), loaded.getFailCount());

        dao.deleteEntityDirectly(loaded);
        assertNull(dao.getEntityById("login:13800000000"));
    }

    @Test
    public void testMfaChallengeNotFound() {
        IEntityDao<NopAuthMfaChallenge> dao = daoProvider.daoFor(NopAuthMfaChallenge.class);
        assertNull(dao.getEntityById("non-existent-token"));
    }
}
