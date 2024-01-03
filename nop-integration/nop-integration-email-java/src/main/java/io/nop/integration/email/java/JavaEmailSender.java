/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.integration.email.java;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.integration.api.email.EmailMessage;
import io.nop.integration.api.email.IEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

public class JavaEmailSender implements IEmailSender {
    static final Logger LOG = LoggerFactory.getLogger(JavaEmailSender.class);

    public static final String HEADER_MESSAGE_ID = "Message-ID";

    private MailConfig config;

    private Session session;

    public void setConfig(MailConfig config) {
        this.config = config;
    }

    @Override
    public void sendEmail(EmailMessage mail) {
        withTransport(transport -> {
            doSend(transport, mail);
        });
    }

    @Override
    public void sendMultiEmail(List<EmailMessage> mails) {
        withTransport(transport -> {
            for (EmailMessage mail : mails) {
                doSend(transport, mail);
            }
        });
    }

    private void withTransport(Consumer<Transport> task) {
        Transport transport = null;
        try {
            transport = connectTransport();
            task.accept(transport);
        } catch (Exception e) {
            LOG.error("nop.err.send-mail-fail", e);
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

    protected Transport connectTransport() throws MessagingException {
        String username = config.getUsername();
        String password = config.getPassword();
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