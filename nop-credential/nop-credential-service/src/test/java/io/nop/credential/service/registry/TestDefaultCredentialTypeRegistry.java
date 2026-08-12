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
}
