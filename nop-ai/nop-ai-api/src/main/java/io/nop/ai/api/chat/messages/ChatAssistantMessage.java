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
import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.List;

/**
 * 助手消息，用于表示AI助手返回的消息
 */
@DataBean
public class ChatAssistantMessage extends ChatMessage {

    /**
     * 消息内容
     */
    private String content;

    /**
     * 思考过程（用于支持推理模型）
     */
    private String think;

    /**
     * 工具调用列表
     */
    private List<ChatToolCall> toolCalls;

    public ChatAssistantMessage() {
    }

    public ChatAssistantMessage(String content) {
        this.content = content;
    }

    @Override
    public String getRole() {
        return "assistant";
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
    public String getThink() {
        return think;
    }

    public void setThink(String think) {
        this.think = think;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<ChatToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ChatToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    /**
     * 检查是否包含工具调用
     */
    @JsonIgnore
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * 获取第一个工具调用（通常只有一个）
     */
    @JsonIgnore
    public ChatToolCall getFirstToolCall() {
        if (hasToolCalls()) {
            return toolCalls.get(0);
        }
        return null;
    }

    @Override
    public ChatAssistantMessage copy() {
        ChatAssistantMessage copy = new ChatAssistantMessage();
        copy.setMessageId(this.getMessageId());
        copy.content = this.content;
        copy.think = this.think;
        if (this.toolCalls != null) {
            copy.toolCalls = new ArrayList<>();
            for (ChatToolCall toolCall : this.toolCalls) {
                copy.toolCalls.add(toolCall.copy());
            }
        }
        return copy;
    }
}
