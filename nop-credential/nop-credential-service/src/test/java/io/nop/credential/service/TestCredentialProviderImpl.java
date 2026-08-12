/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.crypto.ITextCipher;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.credential.api.crypto.ICredentialKeyProvider;
import io.nop.credential.api.registry.ICredentialTypeRegistry;
import io.nop.credential.crypto.CredentialCipher;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.credential.dao.entity.NopCredentialUsage;
import io.nop.credential.service.registry.DefaultCredentialTypeRegistry;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CredentialProviderImpl} 的端到端 AutoTest（H2 内存库，DDL 从 ORM 模型自动初始化）。
 *
 * <p>覆盖：加密 round-trip（JSON→encrypt→DB→load→decrypt→JSON→field map）、接线验证
 * （CredentialCipher.decrypt 被实际调用）、软删除 fail-closed、引用计数幂等/注销、脱敏、
 * testCredential 显式 not-implemented、不存在凭证 fail-closed。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestCredentialProviderImpl extends JunitBaseTestCase {

    private static final String KEY_ID = "testKey";
    private static final String PASSPHRASE = "test-passphrase-for-autotest";

    @Inject
    IDaoProvider daoProvider;

    private ICredentialKeyProvider testKeyProvider() {
        final Map<String, ITextCipher> map = new LinkedHashMap<>();
        map.put(KEY_ID, new AESTextCipher().encKey(PASSPHRASE));
        return new ICredentialKeyProvider() {
            @Override
            public String getActiveKeyId() {
                return KEY_ID;
            }

            @Override
            public ITextCipher getKey(String keyId) {
                return map.get(keyId);
            }

            @Override
            public Set<String> getKeyIds() {
                return Collections.unmodifiableSet(map.keySet());
            }
        };
    }

    private CredentialCipher testCipher() {
        CredentialCipher cipher = new CredentialCipher();
        cipher.setKeyProvider(testKeyProvider());
        return cipher;
    }

    private ICredentialTypeRegistry testRegistry() {
        DefaultCredentialTypeRegistry registry = new DefaultCredentialTypeRegistry();
        registry.init();
        return registry;
    }

    private ICredentialProvider newProvider() {
        CredentialProviderImpl provider = new CredentialProviderImpl();
        provider.setDaoProvider(daoProvider);
        provider.setCredentialCipher(testCipher());
        provider.setCredentialTypeRegistry(testRegistry());
        return provider;
    }

    private NopCredential saveCredential(String id, String typeName, Map<String, Object> fields, boolean deleted) {
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        NopCredential entity = dao.newEntity();
        entity.setCredentialId(id);
        entity.setName(id);
        entity.setTypeName(typeName);
        entity.setStatus("enabled");
        entity.setDelFlag(deleted ? (byte) 1 : (byte) 0);
        entity.setVersion(1);

        String json = JSON.stringify(fields);
        entity.setData(testCipher().encrypt(json, KEY_ID));

        dao.saveEntityDirectly(entity);
        return entity;
    }

    // ==================== 端到端 round-trip + 接线验证 ====================

    @Test
    public void getCredentialRoundTrip() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("apiKey", "sk-test-12345");
        fields.put("orgId", "org-abc");
        saveCredential("cred-rt-1", "openai-api-key", fields, false);

        ICredentialProvider provider = newProvider();
        CredentialData data = provider.getCredential("cred-rt-1");

        assertNotNull(data);
        assertEquals("sk-test-12345", data.getField("apiKey"));
        assertEquals("org-abc", data.getField("orgId"));
    }

    @Test
    public void getCredentialDataReturnsSingleField() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("apiKey", "sk-single");
        saveCredential("cred-single", "openai-api-key", fields, false);

        ICredentialProvider provider = newProvider();
        Object value = provider.getCredentialData("cred-single", "apiKey");
        assertEquals("sk-single", value);
    }

    @Test
    public void getCredentialDataReturnsNullForAbsentField() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("apiKey", "sk-x");
        saveCredential("cred-absent", "openai-api-key", fields, false);

        ICredentialProvider provider = newProvider();
        Object value = provider.getCredentialData("cred-absent", "nonExistentField");
        assertNull(value);
    }

    // ==================== 软删除 fail-closed ====================

    @Test
    public void getCredentialOnDeletedThrowsFailClosed() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("apiKey", "sk-deleted");
        saveCredential("cred-deleted", "openai-api-key", fields, true);

        ICredentialProvider provider = newProvider();
        NopException ex = assertThrows(NopException.class,
                () -> provider.getCredential("cred-deleted"));
        assertTrue(ex.getMessage().contains("cred-deleted"));
    }

    @Test
    public void maskOnDeletedThrowsFailClosed() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("apiKey", "sk-del");
        saveCredential("cred-del-mask", "openai-api-key", fields, true);

        ICredentialProvider provider = newProvider();
        assertThrows(NopException.class, () -> provider.mask("cred-del-mask"));
    }

    @Test
    public void getCredentialOnNonExistentThrowsFailClosed() {
        ICredentialProvider provider = newProvider();
        NopException ex = assertThrows(NopException.class,
                () -> provider.getCredential("does-not-exist"));
        assertTrue(ex.getMessage().contains("does-not-exist"));
    }

    // ==================== 引用计数 ====================

    @Test
    public void registerUsageIsIdempotent() {
        saveCredential("cred-usage", "openai-api-key",
                new LinkedHashMap<>(Map.of("apiKey", "sk-u")), false);
        ICredentialProvider provider = newProvider();

        provider.registerUsage("cred-usage", "consumer1");
        provider.registerUsage("cred-usage", "consumer1");

        CredentialProviderImpl impl = (CredentialProviderImpl) provider;
        assertEquals(1, impl.countUsage("cred-usage"),
                "duplicate registerUsage must be idempotent (1 row)");
    }

    @Test
    public void unregisterUsageRemovesRow() {
        saveCredential("cred-unreg", "openai-api-key",
                new LinkedHashMap<>(Map.of("apiKey", "sk-ur")), false);
        ICredentialProvider provider = newProvider();

        provider.registerUsage("cred-unreg", "consumer1");
        provider.unregisterUsage("cred-unreg", "consumer1");

        CredentialProviderImpl impl = (CredentialProviderImpl) provider;
        assertEquals(0, impl.countUsage("cred-unreg"),
                "unregisterUsage must remove the row");
    }

    @Test
    public void registerUsageMultipleConsumers() {
        saveCredential("cred-multi", "openai-api-key",
                new LinkedHashMap<>(Map.of("apiKey", "sk-m")), false);
        ICredentialProvider provider = newProvider();

        provider.registerUsage("cred-multi", "consumerA");
        provider.registerUsage("cred-multi", "consumerB");

        CredentialProviderImpl impl = (CredentialProviderImpl) provider;
        assertEquals(2, impl.countUsage("cred-multi"));

        provider.unregisterUsage("cred-multi", "consumerA");
        assertEquals(1, impl.countUsage("cred-multi"));
    }

    // ==================== 脱敏 ====================

    @Test
    public void maskReplacesSensitiveFieldsWithStars() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("apiKey", "sk-very-secret-key");
        fields.put("orgId", "org-1234567890");
        saveCredential("cred-mask", "openai-api-key", fields, false);

        ICredentialProvider provider = newProvider();
        MaskedCredential masked = provider.mask("cred-mask");

        assertNotNull(masked);
        assertEquals("****", masked.getFields().get("apiKey"),
                "sensitive field must be masked as ****");
        assertTrue(masked.getFields().get("orgId").endsWith("..."),
                "non-sensitive field longer than 8 chars must be truncated");
    }

    // ==================== testCredential ====================

    @Test
    public void testCredentialReturnsExplicitNotImplemented() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("apiKey", "sk-test");
        saveCredential("cred-test", "openai-api-key", fields, false);

        ICredentialProvider provider = newProvider();
        TestResult result = provider.testCredential("cred-test");

        assertNotNull(result);
        assertFalse(result.isSuccess(), "W2 testCredential must return success=false");
        assertNotNull(result.getMessage());
        assertFalse(result.getMessage().isEmpty(),
                "message must not be empty (Rule #24: no silent void)");
        assertNotNull(result.getTestedAt());
    }

    @Test
    public void testCredentialPersistsResult() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("apiKey", "sk-persist");
        saveCredential("cred-persist", "openai-api-key", fields, false);

        ICredentialProvider provider = newProvider();
        provider.testCredential("cred-persist");

        NopCredential reloaded = daoProvider.daoFor(NopCredential.class).getEntityById("cred-persist");
        assertNotNull(reloaded.getTestResult(), "testResult column must be persisted");
    }

    @Test
    public void testCredentialOnNonExistentThrows() {
        ICredentialProvider provider = newProvider();
        assertThrows(NopException.class, () -> provider.testCredential("no-such-cred"));
    }
}
