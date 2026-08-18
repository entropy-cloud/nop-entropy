/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.sms.yunpian;

import com.yunpian.sdk.YunpianClient;
import com.yunpian.sdk.api.SmsApi;
import com.yunpian.sdk.model.Result;
import com.yunpian.sdk.model.SmsSingleSend;
import io.nop.api.core.exceptions.NopException;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.integration.api.sms.SmsMessage;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_RESOLVE_FAILED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_TYPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W16-impl：YunpianSmsSender credentialId 接线测试——优先级链三态（空=静态值直用/有效=整组
 * 覆盖/失效=fail-closed 不回退）、双构造点（sendMessage + sendMultiMessage）、registerUsage
 * 幂等 + catch-all WARN。client 构造点经 protected seam 替换（mockito stub client/sms API）。
 */
public class TestYunpianSmsSenderCredential {

    static final String CONSUMER_REF = "integration:yunpian-sms";

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

    /** 测试用发送器子类：替换 client 构造点，捕获解析后的 apiKey 并返回 stub client。 */
    static class TestableSender extends YunpianSmsSender {
        String lastApiKey;
        YunpianClient stubClient;

        @Override
        protected YunpianClient createClient(String apiKey) {
            lastApiKey = apiKey;
            return stubClient;
        }
    }

    /** stub client：sms() 返回 single_send 成功（code=0）的 SmsApi，newParam 返回可写 map。 */
    private static YunpianClient newStubClient() {
        YunpianClient client = mock(YunpianClient.class);
        SmsApi smsApi = mock(SmsApi.class);
        Result<SmsSingleSend> result = new Result<>();
        result.setCode(0);
        when(client.newParam(anyInt())).thenReturn(new HashMap<>());
        when(client.sms()).thenReturn(smsApi);
        when(smsApi.single_send(anyMap())).thenReturn(result);
        return client;
    }

    private static SmsMessage message() {
        SmsMessage message = new SmsMessage();
        message.setAreaCode("+86");
        message.setMobile("13800000000");
        message.setText("code");
        return message;
    }

    private static CredentialData yunpianCredential(String apiKey) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("apiKey", apiKey);
        return new CredentialData("yunpian-sms", fields);
    }

    // ==================== 优先级链三态：resolveApiKey 直测 ====================

    @Test
    public void blankCredentialIdUsesStaticApiKeyWithoutProviderCall() {
        TestableSender sender = new TestableSender();
        sender.setApiKey("static-key");
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED); // 若被调用即失败
        sender.setCredentialProvider(provider);

        sender.setCredentialId(null);
        assertEquals("static-key", sender.resolveApiKey());
        sender.setCredentialId("  "); // 空白视同缺失
        assertEquals("static-key", sender.resolveApiKey());
        assertEquals(0, provider.getCredentialCalls);
    }

    @Test
    public void validCredentialOverridesStaticApiKey() {
        TestableSender sender = new TestableSender();
        sender.setApiKey("static-key");
        FakeProvider provider = new FakeProvider();
        provider.data = yunpianCredential("cred-key");
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-1");

        assertEquals("cred-key", sender.resolveApiKey()); // 同名静态值忽略
        assertEquals(1, provider.getCredentialCalls);
    }

    @Test
    public void failedResolutionFailsClosedWithoutFallback() {
        TestableSender sender = new TestableSender();
        sender.setApiKey("static-key");
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED,
                new RuntimeException("provider says credential deleted"));
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-broken");

        NopException ex = assertThrows(NopException.class, sender::resolveApiKey);
        assertEquals(ERR_CREDENTIAL_RESOLVE_FAILED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void providerMissingWithCredentialIdFailsClosedAsDeployInconsistency() {
        TestableSender sender = new TestableSender();
        sender.setApiKey("static-key");
        sender.setCredentialId("cred-1"); // provider 未装配（null）

        NopException ex = assertThrows(NopException.class, sender::resolveApiKey);
        assertEquals(ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void wrongCredentialTypeRejected() {
        TestableSender sender = new TestableSender();
        FakeProvider provider = new FakeProvider();
        provider.data = new CredentialData("tencent-sms", Map.of("appId", 1, "appKey", "k"));
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-wrong-type");

        NopException ex = assertThrows(NopException.class, sender::resolveApiKey);
        assertEquals(ERR_CREDENTIAL_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void requiredFieldMissingFailsClosed() {
        TestableSender sender = new TestableSender();
        FakeProvider provider = new FakeProvider();
        provider.data = new CredentialData("yunpian-sms", Map.of("other", "x")); // apiKey 缺失
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-no-field");

        NopException ex = assertThrows(NopException.class, sender::resolveApiKey);
        assertEquals("nop.err.integration.credential-field-required", ex.getErrorCode());
    }

    // ==================== 双构造点：sendMessage / sendMultiMessage 均走解析 ====================

    @Test
    public void sendMessageEntryUsesResolvedApiKey() {
        TestableSender sender = new TestableSender();
        sender.setApiKey("static-key");
        sender.stubClient = newStubClient();
        FakeProvider provider = new FakeProvider();
        provider.data = yunpianCredential("cred-key");
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-1");

        sender.sendMessage(message());
        assertEquals("cred-key", sender.lastApiKey);
    }

    @Test
    public void sendMultiMessageEntryUsesResolvedApiKey() {
        TestableSender sender = new TestableSender();
        sender.setApiKey("static-key");
        sender.stubClient = newStubClient();
        FakeProvider provider = new FakeProvider();
        provider.data = yunpianCredential("cred-key");
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-1");

        sender.sendMultiMessage(List.of(message(), message()));
        assertEquals("cred-key", sender.lastApiKey);
    }

    @Test
    public void sendMultiMessageFailClosedWhenResolutionFails() {
        TestableSender sender = new TestableSender();
        sender.setApiKey("static-key");
        sender.stubClient = newStubClient();
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED);
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-broken");

        NopException ex = assertThrows(NopException.class,
                () -> sender.sendMultiMessage(List.of(message())));
        assertEquals(ERR_CREDENTIAL_RESOLVE_FAILED.getErrorCode(), ex.getErrorCode());
    }

    // ==================== registerUsage：幂等登记 + catch-all WARN ====================

    @Test
    public void registerUsageOnInitWhenCredentialConfigured() {
        TestableSender sender = new TestableSender();
        FakeProvider provider = new FakeProvider();
        provider.data = yunpianCredential("k");
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-1");

        sender.init();
        sender.init(); // 重复初始化幂等
        assertEquals(2, provider.registerUsageCalls);
        assertEquals("cred-1", provider.lastRegisterCredentialId);
        assertEquals(CONSUMER_REF, provider.lastRegisterConsumerRef);
    }

    @Test
    public void registerUsageSkippedWhenCredentialIdBlankOrProviderMissing() {
        TestableSender sender = new TestableSender();
        FakeProvider provider = new FakeProvider();
        sender.setCredentialProvider(provider);
        sender.init(); // credentialId 空 → 不登记
        assertEquals(0, provider.registerUsageCalls);

        TestableSender sender2 = new TestableSender();
        sender2.setCredentialId("cred-1"); // provider null → 不登记
        sender2.init();
    }

    @Test
    public void registerUsageProviderValidationErrorDoesNotBlockInit() {
        // D6-03 后 registerUsage 前置校验抛错——配错 credentialId 的 bean 初始化必须存活
        TestableSender sender = new TestableSender();
        FakeProvider provider = new FakeProvider();
        provider.registerUsageError = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED);
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-misconfigured");

        sender.init(); // 不抛错
        assertEquals(1, provider.registerUsageCalls);
    }
}
