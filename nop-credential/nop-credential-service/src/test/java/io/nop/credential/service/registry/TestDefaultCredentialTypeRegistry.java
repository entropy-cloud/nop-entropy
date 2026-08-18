/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service.registry;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.credential.api.registry.CredentialType;
import io.nop.credential.api.registry.ICredentialTypeRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultCredentialTypeRegistry} 的聚焦测试。
 *
 * <p>初始化 Nop VFS 到 REGISTER_COMPONENT 级别（不需要数据库），使 {@code *.credential-type.xml}
 * 实例文件可被 {@code ResourceComponentManager} 发现并加载。然后验证：
 * <ul>
 *   <li>{@code getType("openai-api-key")} 返回正确字段集合（apiKey 敏感、orgId 非敏感）</li>
 *   <li>{@code listTypes()} 至少返回 2 种类型</li>
 *   <li>未知类型 {@code getType} 抛出 {@link NopException}（fail-closed，不返回 null）</li>
 * </ul>
 */
public class TestDefaultCredentialTypeRegistry {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private ICredentialTypeRegistry newRegistry() {
        DefaultCredentialTypeRegistry registry = new DefaultCredentialTypeRegistry();
        registry.init();
        return registry;
    }

    /**
     * 指向测试夹具目录（_vfs/test/credential-types/，含非法类型文件）构造 registry。
     */
    private DefaultCredentialTypeRegistry newFixtureRegistry(String dir) {
        DefaultCredentialTypeRegistry registry = new DefaultCredentialTypeRegistry();
        registry.setTypesDir(dir);
        return registry;
    }

    @Test
    public void getTypeReturnsOpenAiKeyTypeWithExpectedFields() {
        ICredentialTypeRegistry registry = newRegistry();
        CredentialType type = registry.getType("openai-api-key");

        assertNotNull(type);
        assertEquals("openai-api-key", type.getName());
        assertEquals("apiKey", type.getAuthType());

        assertNotNull(type.getFields());
        assertEquals(2, type.getFields().size());

        CredentialType.CredentialField apiKey = type.getFields().stream()
                .filter(f -> "apiKey".equals(f.getName())).findFirst().orElse(null);
        assertNotNull(apiKey);
        assertTrue(apiKey.isSensitive(), "apiKey must be sensitive");
        assertTrue(apiKey.isRequired(), "apiKey must be required");
        assertEquals("password", apiKey.getType());

        CredentialType.CredentialField orgId = type.getFields().stream()
                .filter(f -> "orgId".equals(f.getName())).findFirst().orElse(null);
        assertNotNull(orgId);
        assertFalse(orgId.isSensitive(), "orgId must not be sensitive");
        assertFalse(orgId.isRequired(), "orgId must not be required");
        assertEquals("string", orgId.getType());
    }

    @Test
    public void listTypesReturnsAtLeastTwoTypes() {
        ICredentialTypeRegistry registry = newRegistry();
        List<CredentialType> types = registry.listTypes();

        assertNotNull(types);
        assertTrue(types.size() >= 2, "expected at least 2 types, got " + types.size());

        List<String> names = types.stream().map(CredentialType::getName).collect(Collectors.toList());
        assertTrue(names.contains("openai-api-key"), "must contain openai-api-key");
        assertTrue(names.contains("generic-secret"), "must contain generic-secret");
    }

    @Test
    public void getTypeForUnknownThrowsFailClosed() {
        ICredentialTypeRegistry registry = newRegistry();
        NopException ex = assertThrows(NopException.class,
                () -> registry.getType("non-existent-type-xyz"));
        assertTrue(ex.getMessage().contains("non-existent-type-xyz"),
                "error message should mention the unknown type name");
    }

    @Test
    public void getTypeForNullThrowsFailClosed() {
        ICredentialTypeRegistry registry = newRegistry();
        assertThrows(NopException.class, () -> registry.getType(null));
    }

    @Test
    public void genericSecretTypeHasSensitiveSecretField() {
        ICredentialTypeRegistry registry = newRegistry();
        CredentialType type = registry.getType("generic-secret");

        assertEquals("generic-secret", type.getName());
        assertEquals(1, type.getFields().size());

        CredentialType.CredentialField secret = type.getFields().get(0);
        assertEquals("secret", secret.getName());
        assertTrue(secret.isSensitive());
        assertTrue(secret.isRequired());
    }

    // ==================== W9 Phase 1: oauth2 元数据 + 加载校验（全部 fail-closed） ====================

    @Test
    public void genericOauth2TypeMetadataRoundTrip() {
        ICredentialTypeRegistry registry = newRegistry();
        CredentialType type = registry.getType("generic-oauth2");

        assertNotNull(type);
        assertEquals("generic-oauth2", type.getName());
        assertTrue(type.isOauth2Type(), "generic-oauth2 must be authType=oauth2");

        CredentialType.OAuth2Metadata metadata = type.getOauth2();
        assertNotNull(metadata, "oauth2 type must carry OAuth metadata");
        assertEquals("https://auth.example.com/authorize", metadata.getAuthorizationEndpoint());
        assertEquals("https://auth.example.com/token", metadata.getTokenEndpoint());
        assertEquals("read write", metadata.getScopes());
        assertEquals(Integer.valueOf(300), metadata.getRefreshWindowSeconds());

        // 人工字段只有 clientId/clientSecret；保留字段名不出现在类型 fields
        assertEquals(2, type.getFields().size());
        for (CredentialType.CredentialField field : type.getFields()) {
            assertFalse(CredentialType.OAUTH_RESERVED_FIELD_NAMES.contains(field.getName()),
                    "type fields must not use reserved names: " + field.getName());
        }
    }

    @Test
    public void loadRejectsTypeFileOccupyingReservedFieldName() {
        DefaultCredentialTypeRegistry registry = newFixtureRegistry("/test/credential-types-reserved");
        NopException ex = assertThrows(NopException.class, registry::init,
                "registry must reject type file occupying reserved field names (no silent skip)");
        assertEquals("nop.err.credential.type-reserved-field", ex.getErrorCode(),
                "reserved field name must fail with ERR_CREDENTIAL_TYPE_RESERVED_FIELD, got: " + ex.getMessage());
    }

    @Test
    public void loadRejectsAuthTypeOutsideValueDomain() {
        DefaultCredentialTypeRegistry registry = newFixtureRegistry("/test/credential-types-bad-auth");
        NopException ex = assertThrows(NopException.class, registry::init,
                "registry must reject authType outside none|apiKey|basic|oauth2 (no silent skip)");
        assertNotNull(ex);
    }

    @Test
    public void loadRejectsOauth2TypeMissingEndpoints() {
        DefaultCredentialTypeRegistry registry = newFixtureRegistry("/test/credential-types-no-endpoints");
        NopException ex = assertThrows(NopException.class, registry::init,
                "registry must reject oauth2 type without endpoint metadata (no silent skip)");
        assertEquals("nop.err.credential.type-oauth-metadata-missing", ex.getErrorCode(),
                "oauth2 without endpoints must fail with ERR_CREDENTIAL_TYPE_OAUTH_METADATA_MISSING, got: "
                        + ex.getMessage());
    }

    @Test
    public void loadRejectsNonOauth2TypeWithOAuthMetadata() {
        DefaultCredentialTypeRegistry registry = newFixtureRegistry("/test/credential-types-misplaced");
        NopException ex = assertThrows(NopException.class, registry::init,
                "registry must reject non-oauth2 type declaring <oauth2> metadata (no silent skip)");
        assertEquals("nop.err.credential.type-oauth-metadata-not-allowed", ex.getErrorCode(),
                "non-oauth2 with oauth metadata must fail with ERR_CREDENTIAL_TYPE_OAUTH_METADATA_NOT_ALLOWED, got: "
                        + ex.getMessage());
    }

    // ==================== W16-impl-ext: 五类型实例 typeList 可见性（设计 §4.3 八类型全量） ====================

    @Test
    public void listTypesCoversAllEightW16Types() {
        // W16 首批三类型 + W16-impl-ext 五类型（tencent-email/smtp-email/feishu-app/oss-s3/sftp-ssh）
        // 全量可见——typeList 动态表单 schema 的输入完整性
        ICredentialTypeRegistry registry = newRegistry();
        List<String> names = registry.listTypes().stream()
                .map(CredentialType::getName).collect(Collectors.toList());
        assertTrue(names.contains("tencent-sms"), "first-batch type visible");
        assertTrue(names.contains("yunpian-sms"), "first-batch type visible");
        assertTrue(names.contains("jdbc-datasource"), "first-batch type visible");
        assertTrue(names.contains("tencent-email"), "ext-batch type visible: " + names);
        assertTrue(names.contains("smtp-email"), "ext-batch type visible: " + names);
        assertTrue(names.contains("feishu-app"), "ext-batch type visible: " + names);
        assertTrue(names.contains("oss-s3"), "ext-batch type visible: " + names);
        assertTrue(names.contains("sftp-ssh"), "ext-batch type visible: " + names);
    }

    @Test
    public void tencentEmailTypeSchemaMatchesDesign() {
        ICredentialTypeRegistry registry = newRegistry();
        CredentialType type = registry.getType("tencent-email");
        assertEquals(3, type.getFields().size());

        CredentialType.CredentialField secretId = field(type, "secretId");
        assertFalse(secretId.isSensitive());
        assertTrue(secretId.isRequired());

        CredentialType.CredentialField secretKey = field(type, "secretKey");
        assertTrue(secretKey.isSensitive());
        assertTrue(secretKey.isRequired());
        assertEquals("password", secretKey.getType());

        CredentialType.CredentialField region = field(type, "region");
        assertFalse(region.isSensitive());
        assertTrue(region.isRequired());
    }

    @Test
    public void smtpEmailTypeSchemaMatchesDesign() {
        ICredentialTypeRegistry registry = newRegistry();
        CredentialType type = registry.getType("smtp-email");
        assertEquals(2, type.getFields().size());

        CredentialType.CredentialField username = field(type, "username");
        assertFalse(username.isSensitive());
        assertFalse(username.isRequired());

        CredentialType.CredentialField password = field(type, "password");
        assertTrue(password.isSensitive());
        assertFalse(password.isRequired());
    }

    @Test
    public void feishuAppTypeSchemaMatchesDesign() {
        ICredentialTypeRegistry registry = newRegistry();
        CredentialType type = registry.getType("feishu-app");
        assertEquals(4, type.getFields().size());

        assertTrue(field(type, "appId").isRequired());
        assertFalse(field(type, "appId").isSensitive());

        CredentialType.CredentialField appSecret = field(type, "appSecret");
        assertTrue(appSecret.isRequired());
        assertTrue(appSecret.isSensitive());

        CredentialType.CredentialField verificationToken = field(type, "verificationToken");
        assertFalse(verificationToken.isRequired());
        assertTrue(verificationToken.isSensitive());

        CredentialType.CredentialField encryptKey = field(type, "encryptKey");
        assertFalse(encryptKey.isRequired());
        assertTrue(encryptKey.isSensitive());
    }

    @Test
    public void ossS3TypeSchemaMatchesDesign() {
        ICredentialTypeRegistry registry = newRegistry();
        CredentialType type = registry.getType("oss-s3");
        assertEquals(2, type.getFields().size());

        CredentialType.CredentialField accessKey = field(type, "accessKey");
        assertTrue(accessKey.isRequired());
        assertFalse(accessKey.isSensitive());

        CredentialType.CredentialField secretKey = field(type, "secretKey");
        assertTrue(secretKey.isRequired());
        assertTrue(secretKey.isSensitive());
    }

    @Test
    public void sftpSshTypeSchemaMatchesDesign() {
        ICredentialTypeRegistry registry = newRegistry();
        CredentialType type = registry.getType("sftp-ssh");
        assertEquals(3, type.getFields().size());

        CredentialType.CredentialField username = field(type, "username");
        assertFalse(username.isRequired());
        assertFalse(username.isSensitive());

        CredentialType.CredentialField password = field(type, "password");
        assertFalse(password.isRequired());
        assertTrue(password.isSensitive());

        CredentialType.CredentialField passphrase = field(type, "passphrase");
        assertFalse(passphrase.isRequired());
        assertTrue(passphrase.isSensitive());
    }

    private static CredentialType.CredentialField field(CredentialType type, String name) {
        CredentialType.CredentialField field = type.getFields().stream()
                .filter(f -> name.equals(f.getName())).findFirst().orElse(null);
        assertNotNull(field, "field " + name + " must exist on type " + type.getName());
        return field;
    }
}
