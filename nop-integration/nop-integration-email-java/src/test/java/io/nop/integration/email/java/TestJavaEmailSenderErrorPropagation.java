/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.email.java;

import io.nop.api.core.exceptions.NopException;
import io.nop.integration.api.email.EmailMessage;
import org.junit.jupiter.api.Test;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 发送失败可见性测试：IEmailSender 契约按"不抛即成功"消费（nop-auth bindEmail 无条件
 * setEmailSent(true)），SMTP 连接/发送异常必须以 NopException 形式到达调用方——
 * 修复前 withTransport catch-all 仅记日志，sendEmail/sendMultiEmail 永不报错（误报成功）。
 */
public class TestJavaEmailSenderErrorPropagation {

    /** 可注入失败点的 Transport stub：记录 sendMessage 次数（不触网络）。 */
    static class FailingTransport extends Transport {
        MessagingException connectError;
        MessagingException sendError;
        int sendMessageCount;

        FailingTransport() {
            super(Session.getInstance(new Properties()), null);
        }

        @Override
        public void connect(String host, int port, String user, String password) throws MessagingException {
            if (connectError != null) {
                throw connectError;
            }
        }

        @Override
        public void sendMessage(Message msg, javax.mail.Address[] addresses) throws MessagingException {
            sendMessageCount++;
            if (sendError != null) {
                throw sendError;
            }
        }
    }

    /** 测试用发送器子类：替换 Transport 构造点，经 stub 执行真实 connect 流程。 */
    static class TestableSender extends JavaEmailSender {
        final FailingTransport transport = new FailingTransport();

        @Override
        protected Transport connectTransport(ResolvedCredential credential) throws MessagingException {
            MailConfig config = getConfig();
            transport.connect(config.getHost(), config.getPort(), credential.username, credential.password);
            return transport;
        }
    }

    private static MailConfig config() {
        MailConfig config = new MailConfig();
        config.setHost("smtp.example.com");
        config.setPort(587);
        config.setUsername("static-user");
        config.setPassword("static-pass");
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

    @Test
    public void sendFailurePropagatesToCaller() {
        // Transport.sendMessage 抛 MessagingException → sendEmail 必须向调用方抛 NopException
        // （修复前仅记日志，下方 assertThrows 因无异常抛出而失败）
        TestableSender sender = new TestableSender();
        sender.setConfig(config());
        MessagingException failure = new MessagingException("SMTP send rejected");
        sender.transport.sendError = failure;

        NopException ex = assertThrows(NopException.class, () -> sender.sendEmail(mail()));

        assertSame(failure, ex.getCause()); // 原始异常保留为 cause，便于排障
        assertEquals(1, sender.transport.sendMessageCount);
    }

    @Test
    public void connectFailurePropagatesToCaller() {
        // SMTP 建连/认证失败同样必须抛出（修复前静默吞掉，调用方误信已发送）
        TestableSender sender = new TestableSender();
        sender.setConfig(config());
        MessagingException failure = new MessagingException("connect timed out");
        sender.transport.connectError = failure;

        NopException ex = assertThrows(NopException.class, () -> sender.sendEmail(mail()));

        assertSame(failure, ex.getCause());
        assertEquals(0, sender.transport.sendMessageCount); // 未到达发送
    }

    @Test
    public void successfulSendStillSilent() {
        // 成功路径零回归：不抛错、真实到达 Transport
        TestableSender sender = new TestableSender();
        sender.setConfig(config());

        sender.sendEmail(mail());

        assertEquals(1, sender.transport.sendMessageCount);
    }

    @Test
    public void sendMultiEmailFailsFastOnFirstFailure() {
        // fail-fast 语义：第一封失败即抛出，剩余邮件不再尝试（调用方感知后可整批重试，
        // 不再静默放弃）。修复前完全不抛、剩余邮件静默跳过。
        TestableSender sender = new TestableSender();
        sender.setConfig(config());
        sender.transport.sendError = new MessagingException("first mail rejected");

        NopException ex = assertThrows(NopException.class,
                () -> sender.sendMultiEmail(List.of(mail(), mail(), mail())));

        assertEquals(1, sender.transport.sendMessageCount); // 第 2/3 封未尝试
        assertEquals("first mail rejected", ex.getCause().getMessage());
    }
}
