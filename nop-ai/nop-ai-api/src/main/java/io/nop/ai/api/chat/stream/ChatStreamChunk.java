/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.api.core.annotations.data.DataBean;

/**
 * 流式响应数据块
 * 
 * 表示AI流式响应中的一个增量片段，可能是：
 * 1. 文本内容增量（content）
 * 2. 工具调用增量（toolCall）
 * 3. 思考过程增量（thinking）
 * 4. 结束标记（finishReason）
 */
@DataBean
public class ChatStreamChunk {

    /**
     * 流ID
     */
    private String id;

    /**
     * 块序号（可选，用于排序）
     */
    private Integer index;

    /**
     * 角色（通常是 assistant）
     */
    private String role;

    /**
     * 消息内容增量（delta）
     */
    private String content;

    /**
     * 思考过程增量（用于推理模型）
     */
    private String thinking;

    /**
     * 工具调用增量
     */
    private ChatToolCallChunk toolCall;

    /**
     * 结束原因（最后一个块会有）
     */
    private String finishReason;

    /**
     * Token使用统计（最后一个块可能有）
     */
    private ChatUsage usage;

    /**
     * 模型名称
     */
    private String model;

    public ChatStreamChunk() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getThinking() {
        return thinking;
    }

    public void setThinking(String thinking) {
        this.thinking = thinking;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ChatToolCallChunk getToolCall() {
        return toolCall;
    }

    public void setToolCall(ChatToolCallChunk toolCall) {
        this.toolCall = toolCall;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ChatUsage getUsage() {
        return usage;
    }

    public void setUsage(ChatUsage usage) {
        this.usage = usage;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    /**
     * 检查是否为结束块
     */
    public boolean isLastChunk() {
        return finishReason != null;
    }

    /**
     * 检查是否有内容增量
     */
    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }

    /**
     * 检查是否有思考过程增量
     */
    public boolean hasThinking() {
        return thinking != null && !thinking.isEmpty();
    }

    /**
     * 检查是否有工具调用增量
     */
    public boolean hasToolCall() {
        return toolCall != null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ChatStreamChunk{");
        if (id != null) sb.append("id='").append(id).append("\'");
        if (content != null) sb.append(", content='").append(content).append("\'");
        if (thinking != null) sb.append(", thinking='").append(thinking).append("\'");
        if (toolCall != null) sb.append(", toolCall=").append(toolCall);
        if (finishReason != null) sb.append(", finishReason='").append(finishReason).append("\'");
        sb.append('}');
        return sb.toString();
    }
}
