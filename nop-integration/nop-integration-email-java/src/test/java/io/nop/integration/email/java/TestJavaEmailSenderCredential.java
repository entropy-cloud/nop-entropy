/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.email.java;

import io.nop.api.core.exceptions.NopException;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.integration.api.email.EmailMessage;
import org.junit.jupiter.api.Test;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_FIELD_REQUIRED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_RESOLVE_FAILED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_TYPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * W16-impl-ext：JavaEmailSender/MailConfig credentialId 接线测试——优先级链三态（空=MailConfig
 * 静态 username/password 直用/有效=整组覆盖/失效=fail-closed 不回退且 <b>NopException 穿透
 * withTransport 不被吞</b>）、smtp-email 跨字段约束（整组全空显式拒绝/单字段非空合法）、
 * Transport 构造点接线（解析值真实到达 connect）、registerUsage 幂等 + catch-all WARN。
 */
public class TestJavaEmailSenderCredential {

    static final String CONSUMER_REF = "integration:smtp-email";

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

    /** 记录型 Transport stub：捕获 connect 参数与 sendMessage 调用（不触网络）。 */
    static class RecordingTransport extends Transport {
        String connectHost;
        int connectPort;
        String connectUser;
        String connectPass;
        int sendMessageCount;

        RecordingTransport() {
            super(Session.getInstance(new Properties()), null);
        }

        @Override
        public void connect(String host, int port, String user, String password) {
            this.connectHost = host;
            this.connectPort = port;
            this.connectUser = user;
            this.connectPass = password;
        }

        @Override
        public void sendMessage(Message msg, javax.mail.Address[] addresses) {
            sendMessageCount++;
        }
    }

    /** 测试用发送器子类：替换 Transport 构造点，捕获解析后的凭证组。 */
    static class TestableSender extends JavaEmailSender {
        ResolvedCredential lastCredential;
        RecordingTransport lastTransport;

        @Override
        protected Transport connectTransport(ResolvedCredential credential) {
            lastCredential = credential;
            lastTransport = new RecordingTransport();
            return lastTransport;
        }
    }

    private static MailConfig config(String username, String password) {
        MailConfig config = new MailConfig();
        config.setHost("smtp.example.com");
        config.setPort(587);
        config.setUsername(username);
        config.setPassword(password);
        return config;
    }

    private static EmailMessage mail() {
        EmailMessage mail = new EmailMessage();
        mail.setFrom("noreply@example.com");
        mail.setTo(List.of("user@example.com"));
        mail.setSubject("code");
        mail.setText("Your code");
        return mail;
    }

    private static CredentialData smtpCredential(Object username, Object password) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (username != null) {
            fields.put("username", username);
        }
        if (password != null) {
            fields.put("password", password);
        }
        return new CredentialData("smtp-email", fields);
    }

    // ==================== 优先级链三态：resolveCredential 直测 ====================

    @Test
    public void blankCredentialIdUsesStaticValuesWithoutProviderCall() {
        TestableSender sender = new TestableSender();
        sender.setConfig(config("static-user", "static-pass"));
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED); // 若被调用即失败
        sender.setCredentialProvider(provider);

        sender.getConfig().setCredentialId(null);
        JavaEmailSender.ResolvedCredential cred = sender.resolveCredential();
        assertEquals("static-user", cred.username);
        assertEquals("static-pass", cred.password);
        assertEquals(0, provider.getCredentialCalls);

        sender.getConfig().setCredentialId("   "); // 空白视同缺失
        cred = sender.resolveCredential();
        assertEquals("static-user", cred.username);
        assertEquals(0, provider.getCredentialCalls);
    }

    @Test
    public void validCredentialOverridesStaticValuesAsGroup() {
        TestableSender sender = new TestableSender();
        sender.setConfig(config("static-user", "static-pass"));
        FakeProvider provider = new FakeProvider();
        provider.data = smtpCredential("cred-user", "cred-pass");
        sender.setCredentialProvider(provider);
        sender.getConfig().setCredentialId("cred-1");

        JavaEmailSender.ResolvedCredential cred = sender.resolveCredential();
        assertEquals("cred-user", cred.username);
        assertEquals("cred-pass", cred.password);
        assertEquals(1, provider.getCredentialCalls);
    }

    @Test
    public void emptyPlaceholderStaticValuesPassThroughUnchanged() {
        // 静态路径语义保持：resolveCredential 对 MailConfig 值原样透传（"" 占位串的
        // null 归一发生在 connectTransport，属现状行为不变）
        TestableSender sender = new TestableSender();
        sender.setConfig(config("", ""));
        JavaEmailSender.ResolvedCredential cred = sender.resolveCredential();
        assertEquals("", cred.username);
        assertEquals("", cred.password);
    }

    @Test
    public void failedResolutionFailsClosedWithoutFallback() {
        TestableSender sender = new TestableSender();
        sender.setConfig(config("static-user", "static-pass"));
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED,
                new RuntimeException("provider says credential deleted"));
        sender.setCredentialProvider(provider);
        sender.getConfig().setCredentialId("cred-broken");

        NopException ex = assertThrows(NopException.class, sender::resolveCredential);
        assertEquals(ERR_CREDENTIAL_RESOLVE_FAILED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void providerMissingWithCredentialIdFailsClosedAsDeployInconsistency() {
        TestableSender sender = new TestableSender();
        sender.setConfig(config("static-user", "static-pass"));
        sender.getConfig().setCredentialId("cred-1"); // provider 未装配（null）

        NopException ex = assertThrows(NopException.class, sender::resolveCredential);
        assertEquals(ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void wrongCredentialTypeRejected() {
        TestableSender sender = new TestableSender();
        sender.setConfig(config("static-user", "static-pass"));
        FakeProvider provider = new FakeProvider();
        provider.data = new CredentialData("tencent-email", Map.of("secretId", "x"));
        sender.setCredentialProvider(provider);
        sender.getConfig().setCredentialId("cred-wrong-type");

        NopException ex = assertThrows(NopException.class, sender::resolveCredential);
        assertEquals(ERR_CREDENTIAL_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    // ==================== smtp-email 跨字段约束：整凭证至少一字段非空 ====================

    @Test
    public void entirelyEmptyCredentialRejectedAsCrossFieldViolation() {
        // 类型 schema 仅字段级无法表达"至少一字段非空"——解析侧 fail-closed 兜底（Phase 1 裁定）
        TestableSender sender = new TestableSender();
        sender.setConfig(config("static-user", "static-pass"));
        FakeProvider provider = new FakeProvider();
        provider.data = smtpCredential("  ", null); // 整组全空（空白归一后 null）
        sender.setCredentialProvider(provider);
        sender.getConfig().setCredentialId("cred-empty");

        NopException ex = assertThrows(NopException.class, sender::resolveCredential);
        assertEquals(ERR_CREDENTIAL_FIELD_REQUIRED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void usernameOnlyCredentialAccepted() {
        // 单字段非空合法（password 空 = 无认证语义）
        TestableSender sender = new TestableSender();
        sender.setConfig(config("static-user", "static-pass"));
        FakeProvider provider = new FakeProvider();
        provider.data = smtpCredential("cred-user", null);
        sender.setCredentialProvider(provider);
        sender.getConfig().setCredentialId("cred-useronly");

        JavaEmailSender.ResolvedCredential cred = sender.resolveCredential();
        assertEquals("cred-user", cred.username);
        assertNull(cred.password);
    }

    // ==================== 接线 + fail-closed 穿透：sendEmail 双入口 ====================

    @Test
    public void sendEmailEntryUsesResolvedCredentialAndTransportConsumesIt() {
        TestableSender sender = new TestableSender();
        sender.setConfig(config("static-user", "static-pass"));
        FakeProvider provider = new FakeProvider();
        provider.data = smtpCredential("cred-user", "cred-pass");
        sender.setCredentialProvider(provider);
        sender.getConfig().setCredentialId("cred-1");

        sender.sendEmail(mail());
        assertEquals("cred-user", sender.lastCredential.username);
        assertEquals("cred-pass", sender.lastCredential.password);
        assertEquals(1, sender.lastTransport.sendMessageCount); // 解析后的凭证组真实到达发送
    }

    @Test
    public void sendMultiEmailEntryUsesResolvedCredentialGroup() {
        TestableSender sender = new TestableSender();
        sender.setConfig(config("static-user", "static-pass"));
        FakeProvider provider = new FakeProvider();
        provider.data = smtpCredential("cred-user", "cred-pass");
        sender.setCredentialProvider(provider);
        sender.getConfig().setCredentialId("cred-1");

        sender.sendMultiEmail(List.of(mail(), mail()));
        assertEquals("cred-user", sender.lastCredential.username);
        assertEquals(2, sender.lastTransport.sendMessageCount);
    }

    @Test
    public void resolutionFailurePropagatesOutOfWithTransportNotSwallowedAsLog() {
        // 落点约束：withTransport 捕获 Exception 仅 LOG.error——解析必须在其 try 之前执行，
        // NopException 穿透抛出（MFA 链不得误信已发码）
        TestableSender sender = new TestableSender();
        sender.setConfig(config("static-user", "static-pass"));
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED);
        sender.setCredentialProvider(provider);
        sender.getConfig().setCredentialId("cred-broken");

        NopException ex = assertThrows(NopException.class, () -> sender.sendEmail(mail()));
        assertEquals(ERR_CREDENTIAL_RESOLVE_FAILED.getErrorCode(), ex.getErrorCode());
        assertNull(sender.lastTransport); // 未连接 Transport

        NopException ex2 = assertThrows(NopException.class,
                () -> sender.sendMultiEmail(List.of(mail())));
        assertEquals(ERR_CREDENTIAL_RESOLVE_FAILED.getErrorCode(), ex2.getErrorCode());
    }

    @Test
    public void staticPathSendEmailReachesTransportWithStaticValues() {
        // 静态路径零回归：credentialId 空 = 现状行为（MailConfig 值直用）
        TestableSender sender = new TestableSender();
        sender.setConfig(config("static-user", "static-pass"));
        sender.sendEmail(mail());
        assertEquals("static-user", sender.lastCredential.username);
        assertEquals("static-pass", sender.lastCredential.password);
        assertEquals(1, sender.lastTransport.sendMessageCount);
    }

    // ==================== registerUsage：幂等登记 + catch-all WARN ====================

    @Test
    public void registerUsageOnInitWhenCredentialConfigured() {
        TestableSender sender = new TestableSender();
        sender.setConfig(config("static-user", "static-pass"));
        FakeProvider provider = new FakeProvider();
        sender.setCredentialProvider(provider);
        sender.getConfig().setCredentialId("cred-1");

        sender.init();
        sender.init(); // 重复初始化幂等
        assertEquals(2, provider.registerUsageCalls);
        assertEquals("cred-1", provider.lastRegisterCredentialId);
        assertEquals(CONSUMER_REF, provider.lastRegisterConsumerRef);
    }

    @Test
    public void registerUsageSkippedWhenCredentialIdBlankOrProviderMissing() {
        TestableSender sender = new TestableSender();
        sender.setConfig(config("static-user", "static-pass"));
        FakeProvider provider = new FakeProvider();
        sender.setCredentialProvider(provider);
        sender.init(); // credentialId 空 → 不登记
        assertEquals(0, provider.registerUsageCalls);

        TestableSender sender2 = new TestableSender();
        sender2.setConfig(config("static-user", "static-pass"));
        sender2.getConfig().setCredentialId("cred-1"); // provider null → 不登记（静态路径合法）
        sender2.init();
    }

    @Test
    public void registerUsageProviderValidationErrorDoesNotBlockInit() {
        TestableSender sender = new TestableSender();
        sender.setConfig(config("static-user", "static-pass"));
        FakeProvider provider = new FakeProvider();
        provider.registerUsageError = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED);
        sender.setCredentialProvider(provider);
        sender.getConfig().setCredentialId("cred-misconfigured");

        sender.init(); // 不抛错（catch-all WARN）
        assertEquals(1, provider.registerUsageCalls);
    }

    @Test
    public void registerUsageSkippedWhenConfigMissing() {
        JavaEmailSender sender = new JavaEmailSender(); // config 未设置
        sender.init(); // 不抛错、不登记
    }
}
