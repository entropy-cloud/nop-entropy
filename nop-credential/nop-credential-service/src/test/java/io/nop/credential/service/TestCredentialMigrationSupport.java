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
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.CredentialLookup;
import io.nop.credential.api.ICredentialMigrationSupport;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.crypto.CredentialCipher;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.credential.dao.entity.NopCredentialUsage;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W16-impl SPI 增量的端到端 AutoTest（H2 内存库 + 容器装配真实 beans）：
 * <ul>
 *   <li>裁定 1——{@code CredentialData.typeName} 填充 round-trip（getCredential 返回凭实行 typeName）</li>
 *   <li>裁定 2——迁移支持 SPI（{@link ICredentialMigrationSupport}）：createCredential 复用
 *       saveCredential 语义（cv1: 加密 + scope=system）、按名反查（活跃优先/确定性）、按
 *       consumerRef 反查（软删命中 deleted=true 供人工处置）</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestCredentialMigrationSupport extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    ICredentialProvider credentialProvider;

    @Inject
    ICredentialMigrationSupport migrationSupport;

    @Inject
    CredentialCipher credentialCipher;

    @Inject
    IOrmTemplate ormTemplate;

    private void saveRawCredential(String id, String typeName, String name, boolean deleted) {
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        NopCredential entity = dao.newEntity();
        entity.setCredentialId(id);
        entity.setName(name);
        entity.setTypeName(typeName);
        entity.setStatus("enabled");
        entity.setDelFlag(deleted ? (byte) 1 : (byte) 0);
        entity.setVersion(1);
        entity.setScope("system");
        entity.setData(credentialCipher.encrypt("{\"apiKey\":\"raw\"}"));
        dao.saveEntityDirectly(entity);
    }

    private void saveUsage(String credentialId, String consumerRef) {
        IEntityDao<NopCredentialUsage> dao = daoProvider.daoFor(NopCredentialUsage.class);
        NopCredentialUsage usage = dao.newEntity();
        usage.setUsageId(java.util.UUID.randomUUID().toString());
        usage.setCredentialId(credentialId);
        usage.setConsumerRef(consumerRef);
        usage.setCreateTime(new Timestamp(System.currentTimeMillis()));
        dao.saveEntityDirectly(usage);
    }

    // ==================== 裁定 1：CredentialData.typeName 填充 round-trip ====================

    @Test
    public void getCredentialFillsTypeName() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("apiKey", "sk-typename");
        // saveCredential 内部 requireSession（evict 防御）——测试直调需等价开 session
        // （生产路径由 GraphQL mutation 引擎开 session/事务）
        String id = ormTemplate.runInSession(session ->
                migrationSupport.createCredential("openai-api-key", "typename-roundtrip", fields));

        CredentialData data = credentialProvider.getCredential(id);
        assertNotNull(data);
        assertEquals("openai-api-key", data.getTypeName(), "CredentialData must carry typeName (SPI increment 1)");
        assertEquals("sk-typename", data.getField("apiKey"));
    }

    // ==================== 裁定 2：createCredential（saveCredential 语义） ====================

    @Test
    public void createCredentialEncryptsAndScopesSystem() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("appId", 123);
        fields.put("appKey", "secret-pw");

        String id = ormTemplate.runInSession(session ->
                migrationSupport.createCredential("tencent-sms", "tencent-sms:qs/ds1", fields));
        assertNotNull(id);

        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        NopCredential entity = dao.getEntityById(id);
        assertNotNull(entity);
        assertEquals("tencent-sms", entity.getTypeName());
        assertEquals("tencent-sms:qs/ds1", entity.getName());
        assertEquals("system", entity.getScope(), "migration createCredential must fix scope=system");
        assertTrue(entity.getData().startsWith("cv1:"), "data must be cv1: ciphertext (saveCredential semantics)");
        assertEquals("enabled", entity.getStatus());

        // 明文 round-trip 可读回（getCredentialData）
        assertEquals(123, credentialProvider.getCredentialData(id, "appId"));
        assertEquals("secret-pw", credentialProvider.getCredentialData(id, "appKey"));
    }

    @Test
    public void createCredentialRejectsUnknownType() {
        NopException ex = assertThrows(NopException.class,
                () -> migrationSupport.createCredential("no-such-type", "n", Map.of("a", "b")));
        assertEquals("nop.err.credential.unknown-type", ex.getErrorCode());
    }

    @Test
    public void createCredentialRejectsEmptyFields() {
        NopException ex = assertThrows(NopException.class,
                () -> migrationSupport.createCredential("tencent-sms", "n", Map.of()));
        assertEquals("nop.err.credential.fields-required", ex.getErrorCode());
    }

    // ==================== 裁定 2：findCredentialByName（活跃优先，名称辅助） ====================

    @Test
    public void findCredentialByNameReturnsActiveRow() {
        saveRawCredential("name-a", "tencent-sms", "tencent-sms:qs/shared", false);
        CredentialLookup lookup = migrationSupport.findCredentialByName("tencent-sms", "tencent-sms:qs/shared");
        assertNotNull(lookup);
        assertEquals("name-a", lookup.getCredentialId());
        assertEquals("tencent-sms", lookup.getTypeName());
        assertFalse(lookup.isDeleted());
    }

    @Test
    public void findCredentialByNameSkipsSoftDeletedRows() {
        saveRawCredential("name-del", "tencent-sms", "tencent-sms:qs/tombstone", true);
        CredentialLookup lookup = migrationSupport.findCredentialByName("tencent-sms", "tencent-sms:qs/tombstone");
        assertNull(lookup, "name lookup must skip soft-deleted tombstones (identity carried by consumerRef)");
    }

    @Test
    public void findCredentialByNameMissReturnsNull() {
        assertNull(migrationSupport.findCredentialByName("tencent-sms", "no-such-name"));
    }

    // ==================== 裁定 2：findCredentialIdByConsumerRef（迁移反查主源） ====================

    @Test
    public void findCredentialIdByConsumerRefActiveHit() {
        saveRawCredential("ref-active", "tencent-sms", "ref-active-name", false);
        saveUsage("ref-active", "metadata:NopMetaDataSource:ds-1");

        CredentialLookup lookup = migrationSupport.findCredentialIdByConsumerRef("metadata:NopMetaDataSource:ds-1");
        assertNotNull(lookup);
        assertEquals("ref-active", lookup.getCredentialId());
        assertFalse(lookup.isDeleted());
    }

    @Test
    public void findCredentialIdByConsumerRefSoftDeletedHitFlaggedForManualHandling() {
        saveRawCredential("ref-deleted", "tencent-sms", "ref-deleted-name", true);
        saveUsage("ref-deleted", "metadata:NopMetaDataSource:ds-2");

        CredentialLookup lookup = migrationSupport.findCredentialIdByConsumerRef("metadata:NopMetaDataSource:ds-2");
        assertNotNull(lookup, "soft-deleted hit must be returned (not null → caller counts row as failure)");
        assertEquals("ref-deleted", lookup.getCredentialId());
        assertTrue(lookup.isDeleted(), "deleted=true → caller must list row for manual handling, no re-create");
    }

    @Test
    public void findCredentialIdByConsumerRefMissReturnsNull() {
        assertNull(migrationSupport.findCredentialIdByConsumerRef("metadata:NopMetaDataSource:none"));
    }
}
