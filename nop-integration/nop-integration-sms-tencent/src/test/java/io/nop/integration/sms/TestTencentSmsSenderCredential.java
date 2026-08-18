/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.sms;

import com.github.qcloudsms.SmsSingleSender;
import com.github.qcloudsms.SmsSingleSenderResult;
import io.nop.api.core.exceptions.NopException;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.integration.api.sms.SmsMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_RESOLVE_FAILED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_TYPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * W16-impl：TencentSmsSender credentialId 接线测试——优先级链三态（空=静态值直用/有效=整组覆盖
 * 且同名静态值忽略/失效=fail-closed 不回退）、双构造点（sendMessage + sendMultiMessage）、
 * registerUsage 幂等 + catch-all WARN（配错 credentialId 不阻断 bean 初始化）。
 */
public class TestTencentSmsSenderCredential {

    static final String CONSUMER_REF = "integration:tencent-sms";

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

    /** 记录型 SmsSingleSender stub（成功返回 result=0，捕获构造参数与每次发送的 sign）。 */
    static class RecordingSender extends SmsSingleSender {
        final int appId;
        final String appKey;
        final List<String> signs = new ArrayList<>();

        RecordingSender(int appId, String appKey) {
            super(appId, appKey);
            this.appId = appId;
            this.appKey = appKey;
        }

        private static SmsSingleSenderResult ok() {
            SmsSingleSenderResult result = new SmsSingleSenderResult();
            result.result = 0;
            return result;
        }

        @Override
        public SmsSingleSenderResult send(int type, String mobile, String text, String sign,
                                          String ext, String ext2) {
            signs.add(sign);
            return ok();
        }

        @Override
        public SmsSingleSenderResult sendWithParam(String areaCode, String mobile, int tmplId,
                                                   ArrayList<String> params, String sign,
                                                   String ext, String ext2) {
            signs.add(sign);
            return ok();
        }

        @Override
        public SmsSingleSenderResult sendWithParam(String areaCode, String mobile, int tmplId,
                                                   String[] params, String sign,
                                                   String ext, String ext2) {
            signs.add(sign);
            return ok();
        }
    }

    /** 测试用发送器子类：替换 sender 构造点，捕获解析后的凭证组与构造参数。 */
    static class TestableSender extends TencentSmsSender {
        ResolvedCredential lastCredential;
        RecordingSender lastSender;

        @Override
        protected SmsSingleSender createSender(ResolvedCredential credential) {
            lastCredential = credential;
            lastSender = new RecordingSender(credential.appId, credential.appKey);
            return lastSender;
        }
    }

    private static SmsMessage message() {
        SmsMessage message = new SmsMessage();
        message.setAreaCode("+86");
        message.setMobile("13800000000");
        message.setText("code");
        return message;
    }

    private static CredentialData tencentCredential(Object appId, Object appKey, Object sign) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("appId", appId);
        fields.put("appKey", appKey);
        if (sign != null) {
            fields.put("sign", sign);
        }
        return new CredentialData("tencent-sms", fields);
    }

    // ==================== 优先级链三态：resolveCredential 直测 ====================

    @Test
    public void blankCredentialIdUsesStaticValuesWithoutProviderCall() {
        TestableSender sender = new TestableSender();
        sender.setAppId(111);
        sender.setAppKey("static-key");
        sender.setSign("static-sign");
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED); // 若被调用即失败
        sender.setCredentialProvider(provider);

        sender.setCredentialId(null);
        TencentSmsSender.ResolvedCredential cred = sender.resolveCredential();
        assertEquals(111, cred.appId);
        assertEquals("static-key", cred.appKey);
        assertEquals("static-sign", cred.sign);
        assertEquals(0, provider.getCredentialCalls);

        sender.setCredentialId("   "); // 空白视同缺失
        cred = sender.resolveCredential();
        assertEquals(111, cred.appId);
        assertEquals(0, provider.getCredentialCalls);
    }

    @Test
    public void validCredentialOverridesStaticValuesAsGroup() {
        TestableSender sender = new TestableSender();
        sender.setAppId(111);
        sender.setAppKey("static-key");
        sender.setSign("static-sign");
        FakeProvider provider = new FakeProvider();
        provider.data = tencentCredential("22222", "cred-key", "cred-sign");
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-1");

        TencentSmsSender.ResolvedCredential cred = sender.resolveCredential();
        assertEquals(22222, cred.appId);   // 字符串归一 + Integer 转换
        assertEquals("cred-key", cred.appKey);
        assertEquals("cred-sign", cred.sign);
        assertEquals(1, provider.getCredentialCalls);
    }

    @Test
    public void failedResolutionFailsClosedWithoutFallback() {
        TestableSender sender = new TestableSender();
        sender.setAppId(111);
        sender.setAppKey("static-key");
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED,
                new RuntimeException("provider says credential deleted"));
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-broken");

        NopException ex = assertThrows(NopException.class, sender::resolveCredential);
        assertEquals(ERR_CREDENTIAL_RESOLVE_FAILED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void providerMissingWithCredentialIdFailsClosedAsDeployInconsistency() {
        TestableSender sender = new TestableSender();
        sender.setAppId(111);
        sender.setCredentialId("cred-1"); // provider 未装配（null）

        NopException ex = assertThrows(NopException.class, sender::resolveCredential);
        assertEquals(ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void wrongCredentialTypeRejected() {
        TestableSender sender = new TestableSender();
        FakeProvider provider = new FakeProvider();
        provider.data = new CredentialData("yunpian-sms", Map.of("apiKey", "k"));
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-wrong-type");

        NopException ex = assertThrows(NopException.class, sender::resolveCredential);
        assertEquals(ERR_CREDENTIAL_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    // ==================== 双构造点：sendMessage / sendMultiMessage 均走解析 ====================

    @Test
    public void sendMessageEntryUsesResolvedCredentialGroup() {
        TestableSender sender = new TestableSender();
        sender.setAppId(111);
        sender.setAppKey("static-key");
        sender.setSign("static-sign");
        FakeProvider provider = new FakeProvider();
        provider.data = tencentCredential(22222, "cred-key", "cred-sign");
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-1");

        sender.sendMessage(message());
        assertEquals(22222, sender.lastCredential.appId);
        assertEquals("cred-key", sender.lastCredential.appKey);
        assertEquals(22222, sender.lastSender.appId);
        assertEquals("cred-key", sender.lastSender.appKey);
        assertEquals("cred-sign", sender.lastSender.signs.get(0)); // sign 随凭证组生效
    }

    @Test
    public void sendMultiMessageEntryUsesResolvedCredentialGroup() {
        TestableSender sender = new TestableSender();
        sender.setAppId(111);
        sender.setAppKey("static-key");
        FakeProvider provider = new FakeProvider();
        provider.data = tencentCredential(33333, "cred-key", "cred-sign");
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-1");

        sender.sendMultiMessage(List.of(message(), message()));
        assertEquals(33333, sender.lastCredential.appId);
        assertEquals(2, sender.lastSender.signs.size());
        assertEquals("cred-sign", sender.lastSender.signs.get(1));
    }

    @Test
    public void sendMultiMessageFailClosedWhenResolutionFails() {
        TestableSender sender = new TestableSender();
        sender.setAppId(111);
        sender.setAppKey("static-key");
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
        provider.data = tencentCredential(1, "k", null);
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-1");

        sender.init();
        sender.init(); // 重复初始化幂等（无异常；provider 侧幂等由唯一约束承载）
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
        sender2.setCredentialId("cred-1"); // provider null → 不登记（静态路径合法）
        sender2.init();
    }

    @Test
    public void registerUsageProviderValidationErrorDoesNotBlockInit() {
        // D6-03 后 registerUsage 前置校验凭证存在且未软删（fail-closed 抛错）——
        // 配错 credentialId 的 bean 初始化必须存活（catch-all WARN，不阻断启动）
        TestableSender sender = new TestableSender();
        FakeProvider provider = new FakeProvider();
        provider.registerUsageError = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED);
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-misconfigured");

        sender.init(); // 不抛错
        assertEquals(1, provider.registerUsageCalls);
    }
}
