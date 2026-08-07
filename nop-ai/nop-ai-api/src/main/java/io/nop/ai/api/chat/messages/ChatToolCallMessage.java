/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.json.JSON;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具调用消息：表示一次「请求模型/客户端发起工具调用」的独立消息项。
 * <p>
 * 该类型是从 {@link ChatAssistantMessage#getToolCalls()} 寄居字段拆分出来的独立消息载体
 * （plan 325），用于支持 OpenAI Responses API 的 {@code function_call} item 语义。
 * 字段语义复用 {@link ChatToolCall} 的 id/name/arguments。
 * <p>
 * 序列化 type 标识为 {@code "tool_call"}（通过 {@link ChatMessage} 上的 @JsonSubTypes 注册）。
 */
@DataBean
public class ChatToolCallMessage extends ChatMessage {

    /**
     * 工具调用ID（对应 {@link ChatToolCall#getId()}），用于与 {@link ChatToolResponseMessage#getCallId()} 关联。
     */
    private String callId;

    /**
     * 工具/函数名称
     */
    private String name;

    /**
     * 工具调用参数（结构化 Map）
     */
    private Map<String, Object> arguments;

    public ChatToolCallMessage() {
    }

    public ChatToolCallMessage(String callId, String name, Map<String, Object> arguments) {
        this.callId = callId;
        this.name = name;
        this.arguments = arguments;
    }

    @Override
    public String getRole() {
        return "tool_call";
    }

    /**
     * 返回参数的 JSON 文本表示（派生自 {@link #arguments}，非占位值）。
     * <p>
     * 标记 {@link JsonIgnore} 以避免与 {@link #getArguments()} 在序列化时重复输出 arguments。
     */
    @JsonIgnore
    @Override
    public String getContent() {
        return getArgumentsText();
    }

    /**
     * 将 JSON 文本解析为 {@link #arguments} Map（{@link JsonIgnore}：不参与 Jackson 属性绑定）。
     */
    @JsonIgnore
    @Override
    public void setContent(String content) {
        if (content == null || content.isEmpty()) {
            this.arguments = null;
            return;
        }
        Object parsed = JSON.parse(content);
        if (parsed instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            this.arguments = map;
        } else {
            this.arguments = null;
        }
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    /**
     * 参数的 JSON 文本表示
     */
    @JsonIgnore
    public String getArgumentsText() {
        if (arguments == null) {
            return null;
        }
        return JSON.stringify(arguments);
    }

    /**
     * 从 {@link ChatToolCall} 构造工具调用消息，复用其 id/name/arguments。
     */
    public static ChatToolCallMessage fromChatToolCall(ChatToolCall toolCall) {
        Map<String, Object> args = null;
        if (toolCall.getArguments() != null) {
            args = new LinkedHashMap<>(toolCall.getArguments());
        }
        return new ChatToolCallMessage(toolCall.getId(), toolCall.getName(), args);
    }

    @Override
    public ChatToolCallMessage copy() {
        ChatToolCallMessage copy = new ChatToolCallMessage();
        copy.setMessageId(this.getMessageId());
        copy.setProviderHints(this.providerHints);
        copy.callId = this.callId;
        copy.name = this.name;
        if (this.arguments != null) {
            copy.arguments = new LinkedHashMap<>(this.arguments);
        }
        return copy;
    }
}
