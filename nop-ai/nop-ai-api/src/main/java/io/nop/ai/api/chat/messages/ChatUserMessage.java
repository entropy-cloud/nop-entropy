/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户消息，用于表示用户发送的消息
 */
@DataBean
public class ChatUserMessage extends ChatMessage {

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息附件（如图片、文件等）
     */
    private List<ChatAttachment> attachments;

    public ChatUserMessage() {
    }

    public ChatUserMessage(String content) {
        this.content = content;
    }

    @Override
    public String getRole() {
        return "user";
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public void setContent(String content) {
        this.content = content;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<ChatAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ChatAttachment> attachments) {
        this.attachments = attachments;
    }

    public ChatUserMessage addAttachment(ChatAttachment attachment) {
        if (this.attachments == null) {
            this.attachments = new ArrayList<>();
        }
        this.attachments.add(attachment);
        return this;
    }

    @Override
    public ChatUserMessage copy() {
        ChatUserMessage copy = new ChatUserMessage();
        copy.setMessageId(this.getMessageId());
        copy.content = this.content;
        if (this.attachments != null) {
            copy.attachments = new ArrayList<>();
            for (ChatAttachment attachment : this.attachments) {
                copy.attachments.add(attachment.copy());
            }
        }
        return copy;
    }
}
