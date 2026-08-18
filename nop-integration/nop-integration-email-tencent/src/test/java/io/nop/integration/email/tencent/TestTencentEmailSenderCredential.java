/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.email.tencent;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.SendEmailResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.integration.api.email.EmailMessage;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_FIELD_REQUIRED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_RESOLVE_FAILED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_TYPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * W16-impl-ext：TencentEmailSender credentialId 接线测试——优先级链三态（空=静态值直用/有效=
 * 整组覆盖且同名静态值忽略/失效=fail-closed 不回退）、client 构造点接线（解析后的凭证组真实到达
 * {@code createClient}→{@code SesClient} 构造与 SendEmail 消费）、registerUsage 幂等 + catch-all
 * WARN（配错 credentialId 不阻断 bean 初始化）。
 */
public class TestTencentEmailSenderCredential {

    static final String CONSUMER_REF = "integration:tencent-email";

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

    /** 记录型 SesClient stub：捕获 SendEmail 请求（构造参数经 TestableSender 断言）。 */
    static class RecordingSesClient extends SesClient {
        SendEmailRequest lastRequest;

        RecordingSesClient() {
            super(new Credential("id", "key"), "ap-hongkong");
        }

        @Override
        public SendEmailResponse SendEmail(SendEmailRequest request) {
            this.lastRequest = request;
            return new SendEmailResponse();
        }
    }

    /** 测试用发送器子类：替换 client 构造点，捕获解析后的凭证组。 */
    static class TestableSender extends TencentEmailSender {
        ResolvedCredential lastCredential;
        RecordingSesClient lastClient;

        @Override
        protected SesClient createClient(ResolvedCredential credential) {
            lastCredential = credential;
            lastClient = new RecordingSesClient();
            return lastClient;
        }
    }

    private static EmailMessage mail() {
        EmailMessage mail = new EmailMessage();
        mail.setFrom("noreply@example.com");
        mail.setTo(List.of("user@example.com"));
        mail.setSubject("code");
        mail.setText("Your code");
        return mail;
    }

    private static CredentialData tencentEmailCredential(Object secretId, Object secretKey, Object region) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("secretId", secretId);
        fields.put("secretKey", secretKey);
        fields.put("region", region);
        return new CredentialData("tencent-email", fields);
    }

    // ==================== 优先级链三态：resolveCredential 直测 ====================

    @Test
    public void blankCredentialIdUsesStaticValuesWithoutProviderCall() {
        TestableSender sender = new TestableSender();
        sender.setSecretId("static-id");
        sender.setSecretKey("static-key");
        sender.setRegion("static-region");
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED); // 若被调用即失败
        sender.setCredentialProvider(provider);

        sender.setCredentialId(null);
        TencentEmailSender.ResolvedCredential cred = sender.resolveCredential();
        assertEquals("static-id", cred.secretId);
        assertEquals("static-key", cred.secretKey);
        assertEquals("static-region", cred.region);
        assertEquals(0, provider.getCredentialCalls);

        sender.setCredentialId("   "); // 空白视同缺失
        cred = sender.resolveCredential();
        assertEquals("static-id", cred.secretId);
        assertEquals(0, provider.getCredentialCalls);
    }

    @Test
    public void validCredentialOverridesStaticValuesAsGroup() {
        TestableSender sender = new TestableSender();
        sender.setSecretId("static-id");
        sender.setSecretKey("static-key");
        sender.setRegion("static-region");
        FakeProvider provider = new FakeProvider();
        provider.data = tencentEmailCredential("cred-id", "cred-key", "cred-region");
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-1");

        TencentEmailSender.ResolvedCredential cred = sender.resolveCredential();
        assertEquals("cred-id", cred.secretId);
        assertEquals("cred-key", cred.secretKey);
        assertEquals("cred-region", cred.region);
        assertEquals(1, provider.getCredentialCalls);
    }

    @Test
    public void failedResolutionFailsClosedWithoutFallback() {
        TestableSender sender = new TestableSender();
        sender.setSecretId("static-id");
        sender.setSecretKey("static-key");
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
        sender.setSecretId("static-id");
        sender.setCredentialId("cred-1"); // provider 未装配（null）

        NopException ex = assertThrows(NopException.class, sender::resolveCredential);
        assertEquals(ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void wrongCredentialTypeRejected() {
        TestableSender sender = new TestableSender();
        FakeProvider provider = new FakeProvider();
        provider.data = new CredentialData("smtp-email", Map.of("username", "u"));
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-wrong-type");

        NopException ex = assertThrows(NopException.class, sender::resolveCredential);
        assertEquals(ERR_CREDENTIAL_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void requiredFieldMissingFailsClosed() {
        TestableSender sender = new TestableSender();
        sender.setSecretId("static-id");
        FakeProvider provider = new FakeProvider();
        provider.data = tencentEmailCredential("cred-id", "  ", "cred-region"); // secretKey 必填空白
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-1");

        NopException ex = assertThrows(NopException.class, sender::resolveCredential);
        assertEquals(ERR_CREDENTIAL_FIELD_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals("secretKey", ex.getParam("fieldName"));
    }

    // ==================== 接线：解析值真实到达 client 构造与 SendEmail ====================

    @Test
    public void sendEmailEntryUsesResolvedCredentialGroup() {
        TestableSender sender = new TestableSender();
        sender.setSecretId("static-id");
        sender.setSecretKey("static-key");
        sender.setRegion("static-region");
        FakeProvider provider = new FakeProvider();
        provider.data = tencentEmailCredential("cred-id", "cred-key", "cred-region");
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-1");

        sender.sendEmail(mail());
        assertEquals("cred-id", sender.lastCredential.secretId);
        assertEquals("cred-key", sender.lastCredential.secretKey);
        assertEquals("cred-region", sender.lastCredential.region);
        assertNotNull(sender.lastClient.lastRequest, "SendEmail must be invoked on the client built from the resolved group");
    }

    @Test
    public void sendEmailFailClosedWhenResolutionFails() {
        TestableSender sender = new TestableSender();
        sender.setSecretId("static-id");
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED);
        sender.setCredentialProvider(provider);
        sender.setCredentialId("cred-broken");

        NopException ex = assertThrows(NopException.class, () -> sender.sendEmail(mail()));
        assertEquals(ERR_CREDENTIAL_RESOLVE_FAILED.getErrorCode(), ex.getErrorCode());
        assertNull(sender.lastClient); // 未构造 client（fail-closed 于构造点之前）
    }

    // ==================== registerUsage：幂等登记 + catch-all WARN ====================

    @Test
    public void registerUsageOnInitWhenCredentialConfigured() {
        TestableSender sender = new TestableSender();
        FakeProvider provider = new FakeProvider();
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
