/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.auth.dao.entity.NopAuthMfaCredential;
import io.nop.auth.dao.entity.NopAuthMfaRecoveryCode;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * W4 regression test: MFA ORM entities round-trip (save/load).
 * <p>
 * Verifies the two new model-first entities persist and reload with all fields intact,
 * including the encrypted {@code secret} column and the seq-generated recovery-code sid.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopAuthMfaEntityRoundTrip extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testMfaSettingRoundTrip() {
        IEntityDao<NopAuthMfaSetting> dao = daoProvider.daoFor(NopAuthMfaSetting.class);
        NopAuthMfaSetting s = dao.newEntity();
        s.setUserId("user-mfa-1");
        s.setMfaType("totp");
        s.setSecret("v1:encrypted-secret-ciphertext");
        s.setStatus("enabled");
        s.setBindToken("bind-token-abc");
        s.setPhone("13800000000");
        s.setLastVerifiedWindow(1234567890L);
        dao.saveEntity(s);

        NopAuthMfaSetting loaded = dao.getEntityById("user-mfa-1");
        assertNotNull(loaded, "MFA setting must be loadable by userId PK");
        assertEquals("totp", loaded.getMfaType());
        assertEquals("v1:encrypted-secret-ciphertext", loaded.getSecret());
        assertEquals("enabled", loaded.getStatus());
        assertEquals("bind-token-abc", loaded.getBindToken());
        assertEquals("13800000000", loaded.getPhone());
        assertEquals(1234567890L, loaded.getLastVerifiedWindow());
    }

    @Test
    public void testRecoveryCodeRoundTrip() {
        IEntityDao<NopAuthMfaRecoveryCode> dao = daoProvider.daoFor(NopAuthMfaRecoveryCode.class);
        NopAuthMfaRecoveryCode c = dao.newEntity();
        c.setUserId("user-mfa-2");
        c.setCodeHash("$2a$10$someBcryptHashValue");
        c.setUsed((byte) 0);
        dao.saveEntity(c);

        NopAuthMfaRecoveryCode example = dao.newEntity();
        example.setUserId("user-mfa-2");
        List<NopAuthMfaRecoveryCode> list = dao.findAllByExample(example, null);
        assertFalse(list.isEmpty(), "recovery code must persist");
        NopAuthMfaRecoveryCode loaded = list.get(0);
        assertEquals("user-mfa-2", loaded.getUserId());
        assertEquals("$2a$10$someBcryptHashValue", loaded.getCodeHash());
        assertEquals(Byte.valueOf((byte) 0), loaded.getUsed());
        assertNotNull(loaded.getSid(), "seq sid must be generated");
    }

    @Test
    public void testMfaSettingNotFound() {
        IEntityDao<NopAuthMfaSetting> dao = daoProvider.daoFor(NopAuthMfaSetting.class);
        assertNull(dao.getEntityById("non-existent-user"));
    }

    /**
     * W14: NopAuthMfaCredential round-trip（webauthn 多 credential 模型，设计 §5.3.2）——
     * seq sid 生成、credentialId 唯一约束落库、COSE publicKey/signCount/transports/status
     * 字段完整回读、按 credentialId 定位。
     */
    @Test
    public void testWebauthnCredentialRoundTrip() {
        IEntityDao<NopAuthMfaCredential> dao = daoProvider.daoFor(NopAuthMfaCredential.class);
        NopAuthMfaCredential c = dao.newEntity();
        c.setUserId("user-webauthn-1");
        c.setCredentialId("cred-base64url-alpha");
        c.setPublicKey("pQECAyYgASFYIHh4eHggaXNfYV9jb3NlX3B1YmxpY19rZXlfZHVtbXlfbWF0ZXJpYWwiJ1");
        c.setSignCount(42L);
        c.setTransports("usb,nfc");
        c.setName("YubiKey 5C");
        c.setStatus("enabled");
        dao.saveEntity(c);
        assertNotNull(c.getSid(), "seq sid must be generated");

        // 按 credentialId 定位（断言验证路径的查找口径）
        NopAuthMfaCredential example = dao.newEntity();
        example.setCredentialId("cred-base64url-alpha");
        List<NopAuthMfaCredential> list = dao.findAllByExample(example, null);
        assertEquals(1, list.size());
        NopAuthMfaCredential loaded = list.get(0);
        assertEquals("user-webauthn-1", loaded.getUserId());
        assertEquals("cred-base64url-alpha", loaded.getCredentialId());
        assertEquals("pQECAyYgASFYIHh4eHggaXNfYV9jb3NlX3B1YmxpY19rZXlfZHVtbXlfbWF0ZXJpYWwiJ1", loaded.getPublicKey());
        assertEquals(42L, loaded.getSignCount());
        assertEquals("usb,nfc", loaded.getTransports());
        assertEquals("YubiKey 5C", loaded.getName());
        assertEquals("enabled", loaded.getStatus());

        // 同用户第二把（1:N 多 credential 模型）
        NopAuthMfaCredential c2 = dao.newEntity();
        c2.setUserId("user-webauthn-1");
        c2.setCredentialId("cred-base64url-beta");
        c2.setPublicKey("second-cose-key-material");
        c2.setStatus("disabled");
        c2.setSignCount(0L);
        dao.saveEntity(c2);
        NopAuthMfaCredential byUser = dao.newEntity();
        byUser.setUserId("user-webauthn-1");
        assertEquals(2, dao.findAllByExample(byUser, null).size());
    }
}
