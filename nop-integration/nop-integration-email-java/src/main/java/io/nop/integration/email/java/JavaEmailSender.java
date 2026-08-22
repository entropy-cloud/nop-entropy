/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.email.java;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.integration.api.credential.CredentialResolutionSupport;
import io.nop.integration.api.email.EmailMessage;
import io.nop.integration.api.email.IEmailSender;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import static io.nop.integration.api.IntegrationErrors.ARG_CREDENTIAL_ID;
import static io.nop.integration.api.IntegrationErrors.ARG_FIELD_NAME;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_FIELD_REQUIRED;

public class JavaEmailSender implements IEmailSender {
    static final Logger LOG = LoggerFactory.getLogger(JavaEmailSender.class);

    public static final String HEADER_MESSAGE_ID = "Message-ID";

    /** 本渠道的凭证类型（家族允许集单元素；W16-impl-ext 设计 §4.3 类型清单）。 */
    static final String CREDENTIAL_TYPE_SMTP_EMAIL = "smtp-email";

    /** consumerRef 引用计数键（设计 §6.5：部署级单渠道，每渠道类型一条稳定引用）。 */
    static final String CONSUMER_REF = "integration:smtp-email";

    private MailConfig config;

    private Session session;

    /**
     * 凭证消费 SPI（可选装配，{@code @Nullable} → NopIoC optional）：部署不含 nop-credential 时
     * 为 null——credentialId 非空时经共享解析支持 fail-closed（部署不一致），静态路径不受影响。
     */
    protected ICredentialProvider credentialProvider;

    public void setConfig(MailConfig config) {
        this.config = config;
    }

    public MailConfig getConfig() {
        return config;
    }

    @Inject
    public void setCredentialProvider(@Nullable ICredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    /**
     * bean 初始化幂等登记引用计数（config.credentialId 非空且 provider 装配时）。<b>catch-all WARN
     * 不阻断启动</b>——含 provider 前置校验抛错（credentialId 配错/凭证软删）：登记是治理辅助而非
     * 安全边界（安全边界 = 发送期解析 fail-closed）。
     */
    @PostConstruct
    public void init() {
        registerUsageQuietly();
    }

    void registerUsageQuietly() {
        String credentialId = config != null ? config.getCredentialId() : null;
        if (!CredentialResolutionSupport.isConfigured(credentialId) || credentialProvider == null) {
            return;
        }
        try {
            credentialProvider.registerUsage(credentialId, CONSUMER_REF);
        } catch (Exception e) {
            LOG.warn("nop.credential-register-usage-failed:credentialId={},consumerRef={} (non-blocking)",
                    credentialId, CONSUMER_REF, e);
        }
    }

    /**
     * 单次发送的凭证组（逐次发送建 Transport 期解析——凭证轮换/禁用下次发送即生效）。
     * username/password 均可空（空 = 无认证语义），但 credentialId 路径下整组全空被显式拒绝。
     */
    static final class ResolvedCredential {
        final String username;
        final String password;

        ResolvedCredential(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    /**
     * 优先级链判定（共享解析支持单点语义）：config.credentialId 空/空白 → MailConfig 静态
     * username/password；非空 → 解析 {@code smtp-email} 字段集整组覆盖（同名静态值忽略），解析失败
     * fail-closed 抛错（不回退静态值）。包可见以便单元测试三态断言。
     *
     * <p>跨字段约束（设计 §4.3 smtp-email "整凭证至少一字段非空"）：类型 schema 仅字段级无法表达且
     * 本批次零凭证库侧改动——实现通道为解析侧 fail-closed 兜底：整组全空（username 与 password
     * 均缺失/空白）显式拒绝（W16-impl-ext plan Phase 1 裁定）。
     */
    ResolvedCredential resolveCredential() {
        String credentialId = config != null ? config.getCredentialId() : null;
        if (!CredentialResolutionSupport.isConfigured(credentialId)) {
            return new ResolvedCredential(config.getUsername(), config.getPassword());
        }
        CredentialData data = CredentialResolutionSupport.resolveGroup(credentialProvider, credentialId,
                Set.of(CREDENTIAL_TYPE_SMTP_EMAIL));
        String username = CredentialResolutionSupport.optionalString(data, "username");
        String password = CredentialResolutionSupport.optionalString(data, "password");
        if (username == null && password == null) {
            throw new NopException(ERR_CREDENTIAL_FIELD_REQUIRED)
                    .param(ARG_CREDENTIAL_ID, credentialId)
                    .param(ARG_FIELD_NAME, "username/password");
        }
        return new ResolvedCredential(username, password);
    }

    @Override
    public void sendEmail(EmailMessage mail) {
        withTransport(transport -> {
            doSend(transport, mail);
        });
    }

    /**
     * 逐封经同一 Transport 发送（一次建连）。<b>fail-fast 语义</b>：任一封失败即以 NopException
     * 抛出，剩余邮件不再尝试——与逐封调用 sendEmail 的失败可见性一致（不静默放弃），调用方
     * 感知失败后可整批重试。
     */
    @Override
    public void sendMultiEmail(List<EmailMessage> mails) {
        withTransport(transport -> {
            for (EmailMessage mail : mails) {
                doSend(transport, mail);
            }
        });
    }

    private void withTransport(Consumer<Transport> task) {
        // 解析在 try 之前执行：fail-closed 的 NopException 必须穿透抛出，不得被下方 catch 吞成
        // 日志静默失败（否则 MFA 发码链会误信已发码）——W16-impl-ext plan Phase 1 落点约束。
        ResolvedCredential credential = resolveCredential();
        Transport transport = null;
        try {
            transport = connectTransport(credential);
            task.accept(transport);
        } catch (Exception e) {
            // 记录日志后必须重抛：IEmailSender 按"不抛即成功"消费（nop-auth bindEmail 无条件
            // setEmailSent(true)），吞掉发送/连接异常会让 MFA 发码链误信已发送。
            LOG.error("nop.err.send-mail-fail", e);
            throw NopException.adapt(e);
        } finally {
            if (transport != null) {
                try {
                    transport.close();
                } catch (Exception e) { //NOPMD
                    LOG.warn("nop.err.close-transport-fail", e);
                }
            }
        }
    }

    private void doSend(Transport transport, EmailMessage mail) {
        try {
            MimeMessage message = createMimeMessage(mail);
            if (message.getSentDate() == null) {
                message.setSentDate(new Date());
            }
            String messageId = message.getMessageID();
            message.saveChanges();
            if (messageId != null) {
                // Preserve explicitly specified message id...
                message.setHeader(HEADER_MESSAGE_ID, messageId);
            }
            Address[] addresses = message.getAllRecipients();
            transport.sendMessage(message, addresses);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public synchronized Session getSession() {
        if (this.session == null) {
            Properties props = new Properties();
            if (config.getProperties() != null) {
                props.putAll(config.getProperties());
            }
            this.session = Session.getInstance(props);
        }
        return this.session;
    }

    /**
     * Transport 构造/连接点（protected seam 供测试替换）：逐次发送建 Transport 期以解析后的凭证组
     * 连接（username/password 空 = 无认证语义保持；同首批 {@code createSender} 先例）。
     */
    protected Transport connectTransport(ResolvedCredential credential) throws MessagingException {
        String username = credential.username;
        String password = credential.password;
        if ("".equals(username)) {  // probably from a placeholder
            username = null;
            if ("".equals(password)) {  // in conjunction with "" username, this means no password to use
                password = null;
            }
        }

        Transport transport = getSession().getTransport(config.getProtocol());
        transport.connect(config.getHost(), config.getPort(), username, password);
        return transport;
    }

    private MimeMessage createMimeMessage(EmailMessage mail) throws Exception {
        MimeMessage message = new MimeMessage(getSession());
        if (!ApiStringHelper.isEmpty(mail.getPersonalName())) {
            message.setFrom(new InternetAddress(mail.getFrom(), mail.getPersonalName()));
        } else {
            message.setFrom(mail.getFrom());
        }

        message.setRecipients(Message.RecipientType.TO, parse(mail.getTo()));

        if (mail.getCc() != null) {
            message.setRecipients(Message.RecipientType.CC, parse(mail.getCc()));
        }

        message.setSubject(mail.getSubject());

        if (mail.isHtml()) {
            message.setContent(mail.getText(), "text/html;charset=" + config.getDefaultEncoding());
        } else {
            message.setText(mail.getText());
        }
        return message;
    }

    private InternetAddress[] parse(List<String> addrs) throws Exception {
        InternetAddress[] ret = new InternetAddress[addrs.size()];

        int index = 0;
        for (String addr : addrs) {
            InternetAddress mailAddr = InternetAddress.parse(addr)[0];
            ret[index++] = mailAddr;
        }
        return ret;
    }
}
