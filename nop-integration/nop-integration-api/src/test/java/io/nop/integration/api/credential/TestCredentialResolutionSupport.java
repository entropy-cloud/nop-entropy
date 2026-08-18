/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.api.credential;

import io.nop.api.core.exceptions.NopException;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_FIELD_CONVERT_FAILED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_FIELD_REQUIRED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_RESOLVE_FAILED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_TYPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W16-impl：共享解析支持（优先级链判定 + fail-closed 语义 + typeName 家族校验 + 值转换）的
 * 单元测试。fake provider 手写实现（无 mockito 依赖）。
 */
public class TestCredentialResolutionSupport {

    static final Set<String> TENCENT_FAMILY = Set.of("tencent-sms");

    private static final ErrorCodeProbe PROBE = new ErrorCodeProbe();

    /** 手写 fake provider：可编程返回值/抛错，并记录 getCredential 调用次数。 */
    static final class FakeProvider implements ICredentialProvider {
        final Deque<Object> responses = new ArrayDeque<>();
        int getCredentialCalls;

        FakeProvider returning(CredentialData data) {
            responses.add(data);
            return this;
        }

        FakeProvider throwing(NopException error) {
            responses.add(error);
            return this;
        }

        @Override
        public CredentialData getCredential(String credentialId) {
            getCredentialCalls++;
            Object next = responses.pop();
            if (next instanceof NopException) {
                throw (NopException) next;
            }
            return (CredentialData) next;
        }

        @Override
        public Object getCredentialData(String credentialId, String field) {
            return getCredential(credentialId).getField(field);
        }

        @Override
        public TestResult testCredential(String credentialId) {
            throw new UnsupportedOperationException("not used in test");
        }

        @Override
        public MaskedCredential mask(String credentialId) {
            throw new UnsupportedOperationException("not used in test");
        }

        @Override
        public void registerUsage(String credentialId, String consumerRef) {
        }

        @Override
        public void unregisterUsage(String credentialId, String consumerRef) {
        }
    }

    private static CredentialData tencentData(Object appId, Object appKey, Object sign) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("appId", appId);
        fields.put("appKey", appKey);
        if (sign != null) {
            fields.put("sign", sign);
        }
        return new CredentialData("tencent-sms", fields);
    }

    private static NopException notFound(String credentialId) {
        return new NopException(PROBE.notFoundCode).param("credentialId", credentialId);
    }

    // ==================== isConfigured：空串/空白视同缺失 ====================

    @Test
    public void blankCredentialIdTreatedAsMissing() {
        assertFalse(CredentialResolutionSupport.isConfigured(null));
        assertFalse(CredentialResolutionSupport.isConfigured(""));
        assertFalse(CredentialResolutionSupport.isConfigured("   "));
        assertTrue(CredentialResolutionSupport.isConfigured("cred-1"));
    }

    // ==================== 部署不一致 fail-closed ====================

    @Test
    public void providerNullWithConfiguredCredentialIdFailsClosed() {
        NopException ex = assertThrows(NopException.class,
                () -> CredentialResolutionSupport.resolveGroup(null, "cred-1", TENCENT_FAMILY));
        assertEquals(ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED.getErrorCode(), ex.getErrorCode());
    }

    // ==================== provider 侧失败 → 包装为 IntegrationErrors 码 ====================

    @Test
    public void providerFailureWrappedWithCausePreserved() {
        NopException cause = notFound("cred-miss");
        FakeProvider provider = new FakeProvider().throwing(cause);
        NopException ex = assertThrows(NopException.class,
                () -> CredentialResolutionSupport.resolveGroup(provider, "cred-miss", TENCENT_FAMILY));
        assertEquals(ERR_CREDENTIAL_RESOLVE_FAILED.getErrorCode(), ex.getErrorCode());
        assertSame(cause, ex.getCause());
        assertEquals(1, provider.getCredentialCalls);
    }

    // ==================== typeName 家族错型校验 ====================

    @Test
    public void typeMismatchFailsClosed() {
        CredentialData yunpianTyped = new CredentialData("yunpian-sms", Map.of("apiKey", "k"));
        FakeProvider provider = new FakeProvider().returning(yunpianTyped);
        NopException ex = assertThrows(NopException.class,
                () -> CredentialResolutionSupport.resolveGroup(provider, "cred-wrong-type", TENCENT_FAMILY));
        assertEquals(ERR_CREDENTIAL_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
        assertEquals("yunpian-sms", ex.getParam("typeName"));
    }

    @Test
    public void nullTypeNameFailsClosed() {
        FakeProvider provider = new FakeProvider().returning(new CredentialData(Map.of("appId", 1)));
        NopException ex = assertThrows(NopException.class,
                () -> CredentialResolutionSupport.resolveGroup(provider, "cred-legacy", TENCENT_FAMILY));
        assertEquals(ERR_CREDENTIAL_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void matchingTypeResolves() {
        CredentialData data = tencentData(12345, "key", "sign");
        FakeProvider provider = new FakeProvider().returning(data);
        CredentialData resolved = CredentialResolutionSupport.resolveGroup(provider, "cred-1", TENCENT_FAMILY);
        assertSame(data, resolved);
        assertEquals("tencent-sms", resolved.getTypeName());
    }

    // ==================== 字段访问：必填/可空/转换 ====================

    @Test
    public void requireStringMissingOrBlankFailsClosed() {
        CredentialData noField = tencentData(1, "k", null);
        NopException ex1 = assertThrows(NopException.class,
                () -> CredentialResolutionSupport.requireString(noField, "cred-1", "appKey2"));
        assertEquals(ERR_CREDENTIAL_FIELD_REQUIRED.getErrorCode(), ex1.getErrorCode());

        CredentialData blank = new CredentialData("tencent-sms", Map.of("appKey", "  "));
        NopException ex2 = assertThrows(NopException.class,
                () -> CredentialResolutionSupport.requireString(blank, "cred-1", "appKey"));
        assertEquals(ERR_CREDENTIAL_FIELD_REQUIRED.getErrorCode(), ex2.getErrorCode());
    }

    @Test
    public void requireStringNormalizesWhitespace() {
        CredentialData data = tencentData(1, "  key  ", null);
        assertEquals("key", CredentialResolutionSupport.requireString(data, "cred-1", "appKey"));
    }

    @Test
    public void optionalStringBlankLegitimateAsNull() {
        CredentialData data = tencentData(1, "k", "  ");
        assertNull(CredentialResolutionSupport.optionalString(data, "sign"));
        assertNull(CredentialResolutionSupport.optionalString(data, "absent"));
        CredentialData withSign = tencentData(1, "k", " sig ");
        assertEquals("sig", CredentialResolutionSupport.optionalString(withSign, "sign"));
    }

    @Test
    public void requireIntegerConvertsFromStringAndNumber() {
        CredentialData strForm = tencentData(" 12345 ", "k", null);
        assertEquals(12345, CredentialResolutionSupport.requireInteger(strForm, "cred-1", "appId"));
        CredentialData numForm = tencentData(67890, "k", null);
        assertEquals(67890, CredentialResolutionSupport.requireInteger(numForm, "cred-1", "appId"));
    }

    @Test
    public void requireIntegerMissingOrGarbageFailsClosed() {
        CredentialData noField = new CredentialData("tencent-sms", Map.of("appKey", "k"));
        NopException ex1 = assertThrows(NopException.class,
                () -> CredentialResolutionSupport.requireInteger(noField, "cred-1", "appId"));
        assertEquals(ERR_CREDENTIAL_FIELD_REQUIRED.getErrorCode(), ex1.getErrorCode());

        CredentialData garbage = tencentData("abc", "k", null);
        NopException ex2 = assertThrows(NopException.class,
                () -> CredentialResolutionSupport.requireInteger(garbage, "cred-1", "appId"));
        assertEquals(ERR_CREDENTIAL_FIELD_CONVERT_FAILED.getErrorCode(), ex2.getErrorCode());
    }

    /** 独立 ErrorCode 定义探针（模拟 provider 侧错误码，不跨模块引用 credential 常量）。 */
    private static final class ErrorCodeProbe {
        final io.nop.api.core.exceptions.ErrorCode notFoundCode =
                io.nop.api.core.exceptions.ErrorCode.define("test.err.credential.not-found", "not found");
    }
}
