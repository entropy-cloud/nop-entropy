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
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.api.core.annotations.data.DataBean;

/**
 * 聊天响应，AI大模型返回的结果
 */
@DataBean
public class ChatResponse {

    /**
     * 响应消息
     */
    private ChatAssistantMessage message;

    /**
     * Token使用信息
     */
    private ChatUsage usage;

    /**
     * 响应选项
     */
    private ChatOptions options;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 结束原因（stop、length等）
     */
    private String finishReason;

    private long responseTime;

    /**
     * 响应ID
     */
    private String id;

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 错误信息（如果有）
     */
    private String error;

    /**
     * 错误码（如果有）
     */
    private String errorCode;

    public ChatResponse() {
    }

    public ChatResponse(ChatAssistantMessage message) {
        this.message = message;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ChatAssistantMessage getMessage() {
        return message;
    }

    public void setMessage(ChatAssistantMessage message) {
        this.message = message;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ChatUsage getUsage() {
        return usage;
    }

    public void setUsage(ChatUsage usage) {
        this.usage = usage;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ChatOptions getOptions() {
        return options;
    }

    public void setOptions(ChatOptions options) {
        this.options = options;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public long getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(long responseTime) {
        this.responseTime = responseTime;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * 检查是否成功
     */
    @JsonIgnore
    public boolean isSuccess() {
        return error == null;
    }

    @JsonIgnore
    public Integer getPromptTokens() {
        return usage == null ? null : usage.getPromptTokens();
    }

    @JsonIgnore
    public Integer getCompletionTokens() {
        return usage == null ? null : usage.getCompletionTokens();
    }

    @JsonIgnore
    public String getFullContent() {
        return message == null ? null : message.getFullContent();
    }

    /**
     * 创建成功的响应
     */
    public static ChatResponse success(ChatAssistantMessage message) {
        ChatResponse response = new ChatResponse();
        response.setMessage(message);
        return response;
    }

    /**
     * 创建错误的响应
     */
    public static ChatResponse error(String errorCode, String errorMessage) {
        ChatResponse response = new ChatResponse();
        response.setErrorCode(errorCode);
        response.setError(errorMessage);
        return response;
    }

    /**
     * 创建响应的深拷贝
     */
    public ChatResponse copy() {
        ChatResponse copy = new ChatResponse();
        if (this.message != null) {
            copy.message = this.message.copy();
        }
        if (this.usage != null) {
            copy.usage = this.usage.copy();
        }
        if (this.options != null) {
            copy.options = this.options.copy();
        }
        copy.model = this.model;
        copy.finishReason = this.finishReason;
        copy.responseTime = this.responseTime;
        copy.id = this.id;
        copy.requestId = this.requestId;
        copy.error = this.error;
        copy.errorCode = this.errorCode;
        return copy;
    }
}
