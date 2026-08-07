/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

/**
 * 推理消息：表示模型的一段推理（reasoning / thinking）摘要。
 * <p>
 * 该类型是从 {@link ChatAssistantMessage#getThink()} 寄居字段拆分出来的独立消息载体
 * （plan 325），用于支持 OpenAI Responses API 的 {@code reasoning} item 语义。
 * {@code summary} 为必填的推理摘要，{@code detail} 为可选的详细推理内容。
 * <p>
 * 序列化 type 标识为 {@code "reasoning"}（通过 {@link ChatMessage} 上的 @JsonSubTypes 注册）。
 */
@DataBean
public class ChatReasoningMessage extends ChatMessage {

    /**
     * 推理摘要（必填）
     */
    private String summary;

    /**
     * 推理详细内容（可选）
     */
    private String detail;

    public ChatReasoningMessage() {
    }

    public ChatReasoningMessage(String summary) {
        this.summary = summary;
    }

    public ChatReasoningMessage(String summary, String detail) {
        this.summary = summary;
        this.detail = detail;
    }

    @Override
    public String getRole() {
        return "reasoning";
    }

    /**
     * 返回 {@link #summary}（推理摘要即该消息的内容载体，非占位值）。
     */
    @Override
    public String getContent() {
        return summary;
    }

    @Override
    public void setContent(String content) {
        this.summary = content;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    @Override
    public ChatReasoningMessage copy() {
        ChatReasoningMessage copy = new ChatReasoningMessage();
        copy.setMessageId(this.getMessageId());
        copy.setProviderHints(this.providerHints);
        copy.summary = this.summary;
        copy.detail = this.detail;
        return copy;
    }
}
