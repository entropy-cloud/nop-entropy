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
     * 消息内容（纯文本视图，向后兼容）。当 {@link #parts} 非空时，{@link #getContent()} 返回 parts 中
     * 文本片段的拼接，本字段不再作为规范载体；{@link #setContent(String)} 仅写本字段，不触及 parts。
     */
    private String content;

    /**
     * 消息附件（如图片、文件等）
     */
    private List<ChatAttachment> attachments;

    /**
     * 多模态内容片段（plan 326）。使多模态成为一等公民（text/image/audio）。
     * 非空时 {@link #getContent()} 委托为 parts 文本拼接；为空时退回 {@link #content}（向后兼容）。
     */
    private List<ContentPart> parts;

    public ChatUserMessage() {
    }

    public ChatUserMessage(String content) {
        this.content = content;
    }

    @Override
    public String getRole() {
        return "user";
    }

    /**
     * 返回纯文本视图。当 {@link #parts} 非空时，返回其中 {@link ContentPart#TYPE_TEXT} 片段文本的拼接；
     * 否则返回旧 {@link #content} 字段（向后兼容，既有用例返回值不变）。
     */
    @Override
    public String getContent() {
        if (parts != null && !parts.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ContentPart part : parts) {
                if (ContentPart.TYPE_TEXT.equals(part.getType()) && part.getText() != null) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(part.getText());
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<ContentPart> getParts() {
        return parts;
    }

    public void setParts(List<ContentPart> parts) {
        this.parts = parts;
    }

    public ChatUserMessage addPart(ContentPart part) {
        if (this.parts == null) {
            this.parts = new ArrayList<>();
        }
        this.parts.add(part);
        return this;
    }

    @Override
    public ChatUserMessage copy() {
        ChatUserMessage copy = new ChatUserMessage();
        copy.setMessageId(this.getMessageId());
        copy.setProviderHints(this.providerHints);
        copy.content = this.content;
        if (this.attachments != null) {
            copy.attachments = new ArrayList<>();
            for (ChatAttachment attachment : this.attachments) {
                copy.attachments.add(attachment.copy());
            }
        }
        if (this.parts != null) {
            copy.parts = new ArrayList<>();
            for (ContentPart part : this.parts) {
                copy.parts.add(part.copy());
            }
        }
        return copy;
    }
}
