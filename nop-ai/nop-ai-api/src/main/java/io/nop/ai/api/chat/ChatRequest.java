/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.ai.api.chat.messages.*;
import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天请求，用于调用AI大模型
 */
@DataBean
public class ChatRequest {

    /**
     * 消息列表，包含历史对话和当前消息
     */
    private List<ChatMessage> messages;

    /**
     * 聊天选项，控制生成行为
     */
    private ChatOptions options;

    public ChatRequest() {
    }

    public ChatRequest(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public ChatRequest(List<ChatMessage> messages, ChatOptions options) {
        this.messages = messages;
        this.options = options;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public void addMessage(ChatMessage message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ChatOptions getOptions() {
        return options;
    }

    public void setOptions(ChatOptions options) {
        this.options = options;
    }

    /**
     * 获取系统提示词
     * 返回第一条系统消息的内容，如果没有则返回null
     */
    @JsonIgnore
    public String getSystemPrompt() {
        if (messages == null) return null;
        return messages.stream()
                .filter(msg -> msg instanceof ChatSystemMessage)
                .map(ChatMessage::getContent)
                .findFirst()
                .orElse(null);
    }

    /**
     * 设置系统提示词
     * 如果已存在系统消息，则更新第一条；否则在开头添加
     */
    public void setSystemPrompt(String content) {
        if (messages == null) {
            messages = new ArrayList<>();
        }

        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof ChatSystemMessage) {
                messages.set(i, new ChatSystemMessage(content));
                return;
            }
        }
        // 没有找到系统消息，在开头添加
        messages.add(0, new ChatSystemMessage(content));
    }

    /**
     * 获取最后一条用户消息
     */
    @JsonIgnore
    public String getLastUserPrompt() {
        if (messages == null) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof ChatUserMessage) {
                return msg.getContent();
            }
        }
        return null;
    }

    /**
     * 添加用户消息
     */
    public void addUserPrompt(String content) {
        addMessage(new ChatUserMessage(content));
    }

    /**
     * 获取最后一条消息
     */
    @JsonIgnore
    public ChatMessage getLastMessage() {
        if (messages == null || messages.isEmpty()) return null;
        return messages.get(messages.size() - 1);
    }

    /**
     * 获取最后一条非系统消息
     */
    @JsonIgnore
    public ChatMessage getLastNonSystemMessage() {
        if (messages == null) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (!(msg instanceof ChatSystemMessage)) {
                return msg;
            }
        }
        return null;
    }

    /**
     * 获取最后一条助手消息
     */
    @JsonIgnore
    public ChatAssistantMessage getLastAssistantMessage() {
        if (messages == null) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof ChatAssistantMessage) {
                return (ChatAssistantMessage) msg;
            }
        }
        return null;
    }

    /**
     * 获取最后一条助手消息中的工具调用列表
     */
    @JsonIgnore
    public List<ChatToolCall> getToolCalls() {
        ChatAssistantMessage assistantMsg = getLastAssistantMessage();
        if (assistantMsg != null) {
            return assistantMsg.getToolCalls();
        }
        return null;
    }

    /**
     * 检查最后一条助手消息是否包含工具调用
     */
    @JsonIgnore
    public boolean hasToolCalls() {
        List<ChatToolCall> toolCalls = getToolCalls();
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * 添加工具调用结果消息
     *
     * @param toolCallId 工具调用ID（来自ChatToolCall.getId()）
     * @param name       工具名称
     * @param content    工具执行结果
     */
    public void addToolResponse(String toolCallId, String name, String content) {
        addMessage(new ChatToolResponseMessage(toolCallId, name, content));
    }

    /**
     * 链式添加工具调用结果消息
     */
    public ChatRequest withToolResponse(String toolCallId, String name, String content) {
        addToolResponse(toolCallId, name, content);
        return this;
    }

    /**
     * 获取消息数量
     */
    @JsonIgnore
    public int getMessageCount() {
        return messages == null ? 0 : messages.size();
    }

    /**
     * 获取或创建Options
     */
    public ChatOptions makeOptions() {
        if (options == null) {
            options = new ChatOptions();
        }
        return options;
    }

    /**
     * 设置温度参数
     */
    public void setTemperature(Float temperature) {
        makeOptions().setTemperature(temperature);
    }

    /**
     * 获取温度参数
     */
    @JsonIgnore
    public Float getTemperature() {
        return options == null ? null : options.getTemperature();
    }

    /**
     * 设置最大生成令牌数
     */
    public void setMaxTokens(Integer maxTokens) {
        makeOptions().setMaxTokens(maxTokens);
    }

    /**
     * 获取最大生成令牌数
     */
    @JsonIgnore
    public Integer getMaxTokens() {
        return options == null ? null : options.getMaxTokens();
    }


    // ================ 链式调用方法 ================

    /**
     * 链式添加用户消息
     */
    public ChatRequest withUserPrompt(String content) {
        addUserPrompt(content);
        return this;
    }

    /**
     * 链式添加系统消息
     */
    public ChatRequest withSystemPrompt(String content) {
        setSystemPrompt(content);
        return this;
    }


    /**
     * 链式设置温度
     */
    public ChatRequest withTemperature(Float temperature) {
        setTemperature(temperature);
        return this;
    }

    /**
     * 链式设置最大令牌数
     */
    public ChatRequest withMaxTokens(Integer maxTokens) {
        setMaxTokens(maxTokens);
        return this;
    }

    /**
     * 添加工具定义
     */
    public void addTool(ChatToolDefinition tool) {
        makeOptions().addTool(tool);
    }

    /**
     * 链式添加工具定义
     */
    public ChatRequest withTool(ChatToolDefinition tool) {
        addTool(tool);
        return this;
    }

    /**
     * 链式添加工具定义（快捷方式）
     */
    public ChatRequest withTool(String name, String description) {
        addTool(ChatToolDefinition.of(name, description));
        return this;
    }

    /**
     * 设置工具列表
     */
    public void setTools(List<ChatToolDefinition> tools) {
        makeOptions().setTools(tools);
    }

    /**
     * 获取工具列表
     */
    @JsonIgnore
    public List<ChatToolDefinition> getTools() {
        return options == null ? null : options.getTools();
    }

    /**
     * 链式设置工具列表
     */
    public ChatRequest withTools(List<ChatToolDefinition> tools) {
        setTools(tools);
        return this;
    }

    /**
     * 设置工具选择策略
     */
    public void setToolChoice(String toolChoice) {
        makeOptions().setToolChoice(toolChoice);
    }

    /**
     * 获取工具选择策略
     */
    @JsonIgnore
    public String getToolChoice() {
        return options == null ? null : options.getToolChoice();
    }

    /**
     * 链式设置工具选择策略
     */
    public ChatRequest withToolChoice(String toolChoice) {
        setToolChoice(toolChoice);
        return this;
    }

    /**
     * 禁用工具
     */
    public ChatRequest disableTools() {
        makeOptions().disableTools();
        return this;
    }

    /**
     * 自动选择工具（默认）
     */
    public ChatRequest autoToolChoice() {
        makeOptions().autoToolChoice();
        return this;
    }

    /**
     * 强制必须使用至少一个工具
     */
    public ChatRequest requireTool() {
        makeOptions().requireTool();
        return this;
    }

    /**
     * 强制使用指定工具
     */
    public ChatRequest forceTool(String toolName) {
        makeOptions().forceTool(toolName);
        return this;
    }

    /**
     * 创建当前请求的深拷贝
     */
    public ChatRequest copy() {
        ChatRequest copy = new ChatRequest();
        if (messages != null) {
            copy.messages = messages.stream()
                    .map(ChatMessage::copy) // 假设ChatMessage有copy方法
                    .collect(Collectors.toList());
        }
        if (options != null) {
            copy.options = options.copy(); // 假设ChatOptions有copy方法
        }
        return copy;
    }


    /**
     * 创建包含用户消息的请求
     */
    public static ChatRequest userPrompt(String content) {
        ChatRequest request = new ChatRequest();
        request.addMessage(new ChatUserMessage(content));
        return request;
    }

    /**
     * 创建包含系统和用户消息的请求
     */
    public static ChatRequest systemAndUserPrompt(String systemPrompt, String userMessage) {
        ChatRequest request = new ChatRequest();
        request.addMessage(new ChatSystemMessage(systemPrompt));
        request.addMessage(new ChatUserMessage(userMessage));
        return request;
    }

    /**
     * 创建多轮对话请求
     */
    public static ChatRequest conversation(List<ChatMessage> messages) {
        return new ChatRequest(messages);
    }
}
