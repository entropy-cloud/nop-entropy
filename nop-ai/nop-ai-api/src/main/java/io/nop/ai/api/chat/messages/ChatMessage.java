/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

/**
 * 聊天消息基类。Plan 329：序列化判别字段从 {@code role} 改为 {@code type}
 * （最终单一形态）。{@link #getType()} 恒定输出判别字段 {@code type}（确保 List
 * 运行时类型序列化时判别字段仍存在，同时兼容 Nop JsonTool 按 getter 名写出），
 * {@link JsonTypeInfo} 在反序列化时按 {@code type} 分派子类型。
 * {@link #getRole()} 保留为语义访问器，标记 {@link JsonIgnore} 不重复输出。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChatUserMessage.class, name = "user"),
        @JsonSubTypes.Type(value = ChatAssistantMessage.class, name = "assistant"),
        @JsonSubTypes.Type(value = ChatSystemMessage.class, name = "system"),
        @JsonSubTypes.Type(value = ChatToolCallMessage.class, name = "tool_call"),
        @JsonSubTypes.Type(value = ChatToolResponseMessage.class, name = "tool_output"),
        @JsonSubTypes.Type(value = ChatReasoningMessage.class, name = "reasoning"),
})
@DataBean
public abstract class ChatMessage {

    private String messageId;

    /**
     * Provider-specific hints, e.g. Anthropic cache_control: {"cache_control": {"type": "ephemeral"}}
     */
    protected Map<String, Object> providerHints;

    /**
     * 消息角色（user/assistant/system/tool_call/tool_output/reasoning），语义访问器。
     */
    @JsonIgnore
    public abstract String getRole();

    /**
     * 序列化判别字段 {@code type}（值同 {@link #getRole()}）。作为普通 getter 恒定输出，
     * 保证 List 运行时类型序列化时判别字段存在（{@link JsonTypeInfo} 对运行时类型可能不写出）。
     */
    public final String getType() {
        return getRole();
    }

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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> getProviderHints() {
        return providerHints;
    }

    public void setProviderHints(Map<String, Object> providerHints) {
        this.providerHints = providerHints;
    }

    /**
     * 创建消息的深拷贝
     */
    public abstract ChatMessage copy();
}
