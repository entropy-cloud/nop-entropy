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

    /**
     * 可选回调：在 sendEmail 入口处同步调用（在记录/抛错之前）。供测试在 SMTP 调用时机观察外部状态
     * （例如读取交付记录的已提交状态，断言 SMTP 发生于 SUCCEEDED 提交之后——Dim14-02 接线验证）。
     */
    private Runnable onSend;

    /** 可选：非 null 时 sendEmail 抛出此异常（模拟 SMTP 失败，供回补 FAILED 测试）。 */
    private RuntimeException failOnSend;

    @Override
    public void sendEmail(EmailMessage mail) {
        if (onSend != null) {
            onSend.run();
        }
        if (failOnSend != null) {
            throw failOnSend;
        }
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

    public void setOnSend(Runnable onSend) {
        this.onSend = onSend;
    }

    public void setFailOnSend(RuntimeException failOnSend) {
        this.failOnSend = failOnSend;
    }

    public void reset() {
        sentMails.clear();
        onSend = null;
        failOnSend = null;
    }
}
