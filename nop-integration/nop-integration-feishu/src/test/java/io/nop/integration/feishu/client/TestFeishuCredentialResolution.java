/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.feishu.client;

import io.nop.api.core.exceptions.NopException;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_FIELD_REQUIRED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_RESOLVE_FAILED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_TYPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W16-impl-ext：FeishuClient start 期 credentialId 解析测试——start 期解析副本整组覆盖（四字段
 * 逐一断言）、静态路径零回归（credentialId 空 = 四键直用）、fail-closed 矩阵、token 刷新与重连
 * 使用已捕获值（设计 §七#1 维持 deferred 的 watch 锚点）、start 期登记（W16-impl-ext 登记载体
 * 裁定：bean 初始化期不可达 → 随 start 执行，真实可达 + usage 断言）。
 */
class TestFeishuCredentialResolution {

    static final String CONSUMER_REF = "integration:feishu-app";

    /** 手写 fake provider：返回预设数据/抛错，记录 getCredential 与 registerUsage 调用。 */
    static class FakeProvider implements ICredentialProvider {
        CredentialData data;
        NopException error;
        NopException registerUsageError;
        int getCredentialCalls;
        int registerUsageCalls;
        String lastRegisterCredentialId;
        String lastRegisterConsumerRef;

        @Override
        public CredentialData getCredential(String credentialId) {
            getCredentialCalls++;
            if (error != null) {
                throw error;
            }
            return data;
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
            registerUsageCalls++;
            lastRegisterCredentialId = credentialId;
            lastRegisterConsumerRef = consumerRef;
            if (registerUsageError != null) {
                throw registerUsageError;
            }
        }

        @Override
        public void unregisterUsage(String credentialId, String consumerRef) {
        }
    }

    private TestFeishuClient.FakeStreamTransport transport;
    private TestFeishuClient.FakeFeishuHttpApi httpApi;
    private TestFeishuClient.NoopScheduler scheduler;
    private FakeProvider provider;
    private FeishuClient client;
    private TestFeishuClient.RecordingHandler handler;

    @BeforeEach
    void setUp() {
        transport = new TestFeishuClient.FakeStreamTransport();
        httpApi = new TestFeishuClient.FakeFeishuHttpApi();
        scheduler = new TestFeishuClient.NoopScheduler();
        provider = new FakeProvider();
        client = new FeishuClient(transport, httpApi, scheduler);
        client.setCredentialProvider(provider);
        handler = new TestFeishuClient.RecordingHandler();
    }

    private static FeishuCredentials staticCreds() {
        FeishuCredentials creds = new FeishuCredentials();
        creds.setAppId("static-app");
        creds.setAppSecret("static-secret");
        creds.setVerificationToken("static-vt");
        creds.setEncryptKey("static-ek");
        return creds;
    }

    private static FeishuCredentials credRef(String credentialId) {
        FeishuCredentials creds = staticCreds();
        creds.setCredentialId(credentialId);
        return creds;
    }

    private static CredentialData feishuCredential(String appId, String appSecret, String vt, String ek) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("appId", appId);
        fields.put("appSecret", appSecret);
        if (vt != null) {
            fields.put("verificationToken", vt);
        }
        if (ek != null) {
            fields.put("encryptKey", ek);
        }
        return new CredentialData("feishu-app", fields);
    }

    // ==================== 优先级链：resolveEffectiveCredentials 直测 ====================

    @Test
    void blankCredentialIdUsesStaticFourKeysWithoutProviderCall() {
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED); // 若被调用即失败
        FeishuCredentials in = staticCreds();
        in.setCredentialId(null);
        FeishuCredentials out = client.resolveEffectiveCredentials(in);
        assertEquals(in, out, "blank credentialId must pass the original object through (DataBean form unchanged)");
        in.setCredentialId("   "); // 空白视同缺失
        out = client.resolveEffectiveCredentials(in);
        assertEquals(in, out);
        assertEquals(0, provider.getCredentialCalls);
    }

    @Test
    void validCredentialBuildsResolvedCopyCoveringAllFourFields() {
        provider.data = feishuCredential("cred-app", "cred-secret", "cred-vt", "cred-ek");
        FeishuCredentials in = credRef("cred-1");

        FeishuCredentials out = client.resolveEffectiveCredentials(in);
        assertNotEquals(in, out, "start-period resolution must produce a resolved copy, not mutate the DataBean");
        assertEquals("cred-1", out.getCredentialId());
        assertEquals("cred-app", out.getAppId());
        assertEquals("cred-secret", out.getAppSecret());
        assertEquals("cred-vt", out.getVerificationToken());
        assertEquals("cred-ek", out.getEncryptKey());
        assertEquals(1, provider.getCredentialCalls);
    }

    @Test
    void optionalFieldsResolveToNullWhenAbsent() {
        provider.data = feishuCredential("cred-app", "cred-secret", null, null);
        FeishuCredentials out = client.resolveEffectiveCredentials(credRef("cred-1"));
        assertEquals("cred-app", out.getAppId());
        assertNull(out.getVerificationToken(), "verificationToken is optional (reserved field)");
        assertNull(out.getEncryptKey(), "encryptKey is optional (reserved field)");
    }

    @Test
    void providerMissingWithCredentialIdFailsClosedAsDeployInconsistency() {
        client.setCredentialProvider(null);
        NopException ex = assertThrows(NopException.class,
                () -> client.resolveEffectiveCredentials(credRef("cred-1")));
        assertEquals(ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void failedResolutionFailsClosedWithoutFallback() {
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED,
                new RuntimeException("provider says credential deleted"));
        NopException ex = assertThrows(NopException.class,
                () -> client.resolveEffectiveCredentials(credRef("cred-broken")));
        assertEquals(ERR_CREDENTIAL_RESOLVE_FAILED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void wrongCredentialTypeRejected() {
        provider.data = new CredentialData("oss-s3", Map.of("accessKey", "x"));
        NopException ex = assertThrows(NopException.class,
                () -> client.resolveEffectiveCredentials(credRef("cred-wrong-type")));
        assertEquals(ERR_CREDENTIAL_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void requiredFieldMissingFailsClosed() {
        provider.data = feishuCredential("  ", "cred-secret", null, null); // appId 必填空白
        NopException ex = assertThrows(NopException.class,
                () -> client.resolveEffectiveCredentials(credRef("cred-1")));
        assertEquals(ERR_CREDENTIAL_FIELD_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals("appId", ex.getParam("fieldName"));
    }

    // ==================== 接线：start 期解析值真实到达消费点 ====================

    @Test
    void startPassesResolvedGroupToStreamEndpointAndHandshake() {
        provider.data = feishuCredential("cred-app", "cred-secret", null, null);
        client.start(credRef("cred-1"), handler);

        // 消费点 1：getStreamEndpoint 收到解析后的 appId/appSecret（非静态值）
        assertEquals("cred-app", httpApi.endpointAppIds.get(0));
        assertEquals("cred-secret", httpApi.endpointAppSecrets.get(0));

        // 消费点 2：onOpen 握手帧携带解析后的 app_id
        io.nop.integration.feishu.codec.FeishuStreamFrame handshake =
                io.nop.integration.feishu.codec.FeishuPbCodec.decode(transport.sentFrames.get(0));
        assertTrue(new String(handshake.getPayload(), java.nio.charset.StandardCharsets.UTF_8)
                .contains("\"app_id\":\"cred-app\""));
    }

    @Test
    void startWithBlankCredentialIdUsesStaticValuesAtConsumptionPoints() {
        // 静态路径零回归：credentialId 空 = 四键直用（现状行为）
        FeishuCredentials creds = staticCreds();
        creds.setCredentialId("  ");
        client.start(creds, handler);
        assertEquals("static-app", httpApi.endpointAppIds.get(0));
        assertEquals("static-secret", httpApi.endpointAppSecrets.get(0));
        assertEquals(0, provider.getCredentialCalls);
    }

    @Test
    void tokenRefreshUsesCapturedResolvedValues() {
        // 设计 §七#1 维持 deferred 的 watch 锚点：token 惰性刷新继续使用 start 期已捕获值
        provider.data = feishuCredential("cred-app", "cred-secret", null, null);
        client.start(credRef("cred-1"), handler);
        client.sendMessage("open_id", "ou_x", "text", "{}");
        assertEquals("cred-app", httpApi.tokenAppIds.get(0));
        assertEquals("cred-secret", httpApi.tokenAppSecrets.get(0));
    }

    @Test
    void reconnectUsesCapturedResolvedValues() {
        // 设计 §七#1 watch 锚点：重连复用已捕获值（不经 provider 再解析）
        provider.data = feishuCredential("cred-app", "cred-secret", null, null);
        client.start(credRef("cred-1"), handler);
        assertEquals(1, provider.getCredentialCalls);
        assertEquals(1, httpApi.endpointCount);

        transport.fireClose(); // onClose → scheduleReconnect → NoopScheduler 捕获
        assertEquals(1, scheduler.scheduledCommands.size(), "reconnect must be scheduled");
        scheduler.scheduledCommands.get(0).run(); // 手动执行重连

        assertEquals(2, httpApi.endpointCount);
        assertEquals("cred-app", httpApi.endpointAppIds.get(1));
        assertEquals("cred-secret", httpApi.endpointAppSecrets.get(1));
        assertEquals(1, provider.getCredentialCalls, "reconnect must NOT re-resolve (deferred §7#1 watch anchor)");
    }

    // ==================== 登记载体：start 期执行，真实可达 + usage 断言 ====================

    @Test
    void registerUsageAtStartWhenCredentialConfigured() {
        provider.data = feishuCredential("cred-app", "cred-secret", null, null);
        client.start(credRef("cred-1"), handler);
        assertEquals(1, provider.registerUsageCalls);
        assertEquals("cred-1", provider.lastRegisterCredentialId);
        assertEquals(CONSUMER_REF, provider.lastRegisterConsumerRef);

        client.stop();
        provider.data = feishuCredential("cred-app", "cred-secret", null, null);
        client.start(credRef("cred-1"), handler); // 重启后再登记（幂等由 provider 唯一约束承载）
        assertEquals(2, provider.registerUsageCalls);
    }

    @Test
    void registerUsageSkippedWhenCredentialIdBlank() {
        client.start(staticCreds(), handler); // credentialId 空 → 不登记
        assertEquals(0, provider.registerUsageCalls);
    }

    @Test
    void providerMissingWithCredentialIdFailsClosedBeforeRegistration() {
        // provider null + credentialId 非空：start 期解析 fail-closed（先于登记——无登记发生）
        FeishuClient client2 = new FeishuClient(transport, httpApi, scheduler);
        client2.setCredentialProvider(null);
        NopException ex = assertThrows(NopException.class, () -> client2.start(credRef("cred-1"), handler));
        assertEquals(ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED.getErrorCode(), ex.getErrorCode());
        assertEquals(0, provider.registerUsageCalls, "no registration when resolution fails closed");
    }

    @Test
    void registerUsageProviderValidationErrorDoesNotBlockStart() {
        // 配错 credentialId：登记 catch-all WARN，start 仍完成（解析 fail-closed 才是安全边界）
        provider.data = feishuCredential("cred-app", "cred-secret", null, null);
        provider.registerUsageError = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED);
        client.start(credRef("cred-misconfigured"), handler);
        assertEquals(1, provider.registerUsageCalls);
        assertEquals("cred-app", httpApi.endpointAppIds.get(0), "start must proceed past registration failure");
    }

    @Test
    void startFailClosedWhenResolutionFails() {
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED);
        assertThrows(NopException.class, () -> client.start(credRef("cred-broken"), handler));
        assertEquals(0, httpApi.endpointCount, "no endpoint fetch after fail-closed resolution");
        assertEquals(0, transport.connectCount, "no transport connect after fail-closed resolution");
    }

    @Test
    void doubleStartIdempotentNoDuplicateRegistration() {
        provider.data = feishuCredential("cred-app", "cred-secret", null, null);
        client.start(credRef("cred-1"), handler);
        client.start(credRef("cred-1"), handler); // 已 started → no-op
        assertEquals(1, provider.registerUsageCalls);
        assertEquals(1, transport.connectCount);
    }
}
