/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mock;

import io.nop.integration.api.email.EmailMessage;
import io.nop.integration.api.email.IEmailSender;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 容器 E2E 用的捕获型邮件发送器（W15-impl）：邮件验证码发码链路（bindMfa(email) /
 * sendMfaCode email 分支 / 登记通道 email proof）经本 bean "发送"，验证码可从
 * {@link #lastMessage} 读取（{@link CapturingSmsSender} 同目录先例）。
 */
public class CapturingEmailSender implements IEmailSender {

    private static final AtomicReference<EmailMessage> LAST = new AtomicReference<>();

    @Override
    public void sendEmail(EmailMessage mail) {
        LAST.set(mail);
    }

    public static EmailMessage lastMessage() {
        return LAST.get();
    }

    /** 最近一封邮件正文中的 6 位验证码。 */
    public static String lastCode() {
        EmailMessage msg = LAST.get();
        if (msg == null || msg.getText() == null)
            return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{6})\\b").matcher(msg.getText());
        return m.find() ? m.group(1) : null;
    }

    public static void reset() {
        LAST.set(null);
    }
}
