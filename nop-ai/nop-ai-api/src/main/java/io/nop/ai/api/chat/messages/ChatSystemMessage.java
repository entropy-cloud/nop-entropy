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

/**
 * 系统消息，用于设置AI助手的行为和上下文
 */
@DataBean
public class ChatSystemMessage extends ChatMessage {

    /**
     * 消息内容（系统提示词）
     */
    private String content;

    /**
     * 系统消息名称（可选，用于标识不同的系统提示）
     */
    private String name;

    public ChatSystemMessage() {
    }

    public ChatSystemMessage(String content) {
        this.content = content;
    }

    @Override
    public String getRole() {
        return "system";
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public void setContent(String content) {
        this.content = content;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public ChatSystemMessage copy() {
        ChatSystemMessage copy = new ChatSystemMessage();
        copy.setMessageId(this.getMessageId());
        copy.content = this.content;
        copy.name = this.name;
        return copy;
    }
}
