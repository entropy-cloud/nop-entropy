package io.nop.datav.service.mock;

import io.nop.integration.api.email.EmailMessage;
import io.nop.integration.api.email.IEmailSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 测试用 mock {@link IEmailSender}（D5-1）。
 *
 * <p>记录所有 sendEmail 调用供测试断言（{@link #getSentMails()}、{@link #getSendCount()}），
 * 不实际发送邮件。通过 test-mock.beans.xml 注册为 primary=true 覆盖生产实现。</p>
 */
public class MockEmailSender implements IEmailSender {

    private final List<EmailMessage> sentMails = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void sendEmail(EmailMessage mail) {
        sentMails.add(mail);
    }

    public List<EmailMessage> getSentMails() {
        synchronized (sentMails) {
            return new ArrayList<>(sentMails);
        }
    }

    public int getSendCount() {
        return sentMails.size();
    }

    public void reset() {
        sentMails.clear();
    }
}
