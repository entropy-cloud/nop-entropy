/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.integration.api.email;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.resource.IResourceReference;
import io.nop.api.core.util.ApiStringHelper;

import java.util.List;

@DataBean
public class EmailMessage {
    private String subject;
    private String personalName;
    private String from;
    private String reply;
    private List<String> to;
    private List<String> cc;
    private String text;
    private boolean html;

    private List<IResourceReference> attachments;

    public String getPersonalName() {
        return personalName;
    }

    public void setPersonalName(String personalName) {
        this.personalName = personalName;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    @JsonIgnore
    public String getFullFrom() {
        if (ApiStringHelper.isEmpty(personalName))
            return getFrom();
        return personalName + " <" + from + ">";
    }

    public boolean isHtml() {
        return html;
    }

    public void setHtml(boolean html) {
        this.html = html;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }


    public List<String> getTo() {
        return to;
    }

    public void setTo(List<String> to) {
        this.to = to;
    }

    public List<String> getCc() {
        return cc;
    }

    public void setCc(List<String> cc) {
        this.cc = cc;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<IResourceReference> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<IResourceReference> attachments) {
        this.attachments = attachments;
    }
}
