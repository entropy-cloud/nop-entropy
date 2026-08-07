/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

/**
 * 工具调用结果消息，用于将工具执行结果返回给AI模型
 * 
 * 在OpenAI等API中，当Assistant返回tool_calls后，
 * 需要执行工具并将结果以tool角色的消息发送回去
 * 
 * 序列化 type 标识为 {@code "tool_output"}（plan 325 起从 {@code "tool"} 改名，
 * 与 {@link ChatToolCallMessage} 的 {@code "tool_call"} 配对）。
 */
@DataBean
public class ChatToolResponseMessage extends ChatMessage {

    /**
     * 工具调用ID（对应 {@link ChatToolCall#getId()} / {@link ChatToolCallMessage#getCallId()}）
     */
    private String callId;

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具执行结果内容
     */
    private String content;

    /**
     * 结构化工具结果（JSON 对象），与 content 互补：
     * content 为文本摘要，result 为结构化数据。
     * 为 null 时表示纯文本结果（使用 content 字段）
     */
    private Object result;

    /**
     * 结果类型标记："text"(默认) | "json" | "error" | "content"
     */
    private String resultType;

    public ChatToolResponseMessage() {
    }

    public ChatToolResponseMessage(String callId, String name, String content) {
        this.callId = callId;
        this.name = name;
        this.content = content;
    }

    @Override
    public String getRole() {
        return "tool_output";
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public void setContent(String content) {
        this.content = content;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    /**
     * @deprecated plan 325 起关联字段已改名为 {@link #getCallId()}；保留此委托方法以兼容既有调用点。
     */
    @Deprecated
    @JsonIgnore
    public String getToolCallId() {
        return getCallId();
    }

    /**
     * @deprecated plan 325 起关联字段已改名为 {@link #setCallId(String)}；保留此委托方法以兼容既有调用点。
     */
    @Deprecated
    @JsonIgnore
    public void setToolCallId(String toolCallId) {
        setCallId(toolCallId);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }

    @Override
    public ChatToolResponseMessage copy() {
        ChatToolResponseMessage copy = new ChatToolResponseMessage();
        copy.setMessageId(this.getMessageId());
        copy.setProviderHints(this.providerHints);
        copy.callId = this.callId;
        copy.name = this.name;
        copy.content = this.content;
        copy.result = this.result;
        copy.resultType = this.resultType;
        return copy;
    }

    /**
     * 从工具调用创建响应消息
     */
    public static ChatToolResponseMessage fromToolCall(ChatToolCall toolCall, String result) {
        return new ChatToolResponseMessage(toolCall.getId(), toolCall.getName(), result);
    }

    /**
     * 创建错误响应
     */
    public static ChatToolResponseMessage error(String callId, String name, String errorMessage) {
        return new ChatToolResponseMessage(callId, name, "Error: " + errorMessage);
    }

    public static ChatToolResponseMessage structured(String callId, String name,
                                                       String content, Object result) {
        ChatToolResponseMessage msg = new ChatToolResponseMessage(callId, name, content);
        msg.setResult(result);
        msg.setResultType("json");
        return msg;
    }
}
