/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.messages;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.nop.api.core.annotations.data.DataBean;

/**
 * 聊天消息基类，支持角色区分
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "role")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChatUserMessage.class, name = "user"),
        @JsonSubTypes.Type(value = ChatAssistantMessage.class, name = "assistant"),
        @JsonSubTypes.Type(value = ChatSystemMessage.class, name = "system"),
        @JsonSubTypes.Type(value = ChatToolResponseMessage.class, name = "tool"),
        @JsonSubTypes.Type(value = ChatCustomMessage.class, name = "custom"),
})
@DataBean
public abstract class ChatMessage {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息角色（user/assistant/system）
     */
    public abstract String getRole();

    /**
     * 消息内容
     */
    public abstract String getContent();

    /**
     * 设置消息内容
     */
    public abstract void setContent(String content);

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /**
     * 创建消息的深拷贝
     */
    public abstract ChatMessage copy();
}
