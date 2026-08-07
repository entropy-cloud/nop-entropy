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
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天响应，AI大模型返回的结果
 */
@DataBean
public class ChatResponse {

    /**
     * 响应消息（旧的单条 assistant 文本载体，plan 326 起由 {@link #messages} 序列承担规范语义）。
     * 保留以兼容 {@link #getMessage()}/{@link #setMessage(ChatAssistantMessage)} 既有路径；
     * 双轨过渡期内由各 dialect parseResponse 同时填充本字段与 {@link #messages}。
     */
    private ChatAssistantMessage message;

    /**
     * 响应消息序列（plan 326）。规范语义载体：assistant 文本、reasoning、tool_call 等按语义顺序排列。
     * 双轨过渡期内 dialect 同时填充旧 {@link #message} 与本序列；327 起 agent 引擎将切换为读本序列。
     */
    private List<ChatMessage> messages;

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

    /**
     * 错误规范化分类（当响应为错误时由 dialect.parseErrorResponse 规范化产出）。
     * 驱动上层可靠性层（IRetryPolicy）的 RETRY / STOP / FALLBACK 决策。
     * 仅在 {@link #isSuccess()} == false 时有意义；成功响应为 null。
     *
     * <p>类型归属见 {@link ErrorClassification}：本枚举与 {@link ChatResponse} 同处
     * nop-ai-api，故信号通路（core 产出 → ChatResponse 携带 → agent 消费）全程同一类型。</p>
     */
    private ErrorClassification errorClassification;

    /**
     * Retry-After 归一值（毫秒）。provider 经 HTTP 头或 body 字段告知的等待时长，
     * 由 dialect.parseErrorResponse 归一后填入（见设计 §3.7 多源解析）。null 表示无
     * 服务器提示（策略层退回纯 full-jitter 退避）。
     */
    private Long retryAfterMs;

    /**
     * 原始 HTTP 状态码（诊断用，非 200 错误响应时填充）。
     */
    private Integer httpStatus;

    public ChatResponse() {
    }

    public ChatResponse(ChatAssistantMessage message) {
        this.message = message;
    }

    /**
     * 返回旧的单条 assistant 消息。已废弃，新的规范访问为 {@link #getMessages()}。
     * <p>
     * 委托规则（双轨过渡期）：
     * <ol>
     *   <li>若 {@link #messages} 非空，返回其中首个 {@link ChatAssistantMessage}（按类型筛选，
     *       跳过前置的 reasoning/tool_call，保证返回的是 assistant 文本而非推理）。</li>
     *   <li>否则返回 {@link #message} 字段（兼容仅调用 {@link #setMessage(ChatAssistantMessage)}
     *       而未填充 {@link #messages} 的既有代码，行为不变）。</li>
     * </ol>
     */
    @Deprecated
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ChatAssistantMessage getMessage() {
        if (messages != null) {
            for (ChatMessage msg : messages) {
                if (msg instanceof ChatAssistantMessage) {
                    return (ChatAssistantMessage) msg;
                }
            }
        }
        return message;
    }

    public void setMessage(ChatAssistantMessage message) {
        this.message = message;
    }

    /**
     * 返回响应消息序列（规范语义载体，plan 326）。
     */
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ErrorClassification getErrorClassification() {
        return errorClassification;
    }

    public void setErrorClassification(ErrorClassification errorClassification) {
        this.errorClassification = errorClassification;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long getRetryAfterMs() {
        return retryAfterMs;
    }

    public void setRetryAfterMs(Long retryAfterMs) {
        this.retryAfterMs = retryAfterMs;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
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
        ChatAssistantMessage msg = getMessage();
        return msg == null ? null : msg.getFullContent();
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
     * 创建成功的响应（plan 326），以消息序列为载体。同时把序列中首个 {@link ChatAssistantMessage}
     * 写入旧 {@link #message} 字段，保证 {@link #getMessage()} 既有路径可用（双轨过渡）。
     */
    public static ChatResponse success(List<ChatMessage> messages) {
        ChatResponse response = new ChatResponse();
        if (messages != null) {
            response.setMessages(new ArrayList<>(messages));
            for (ChatMessage msg : messages) {
                if (msg instanceof ChatAssistantMessage) {
                    response.setMessage((ChatAssistantMessage) msg);
                    break;
                }
            }
        }
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
     * 创建带规范化分类的错误响应（供 dialect.parseErrorResponse 使用）。
     *
     * @param errorClassification 规范化分类（驱动重试/回退决策）
     * @param httpStatus          原始 HTTP 状态码
     * @param errorCode           provider 错误码（可空）
     * @param errorMessage        provider 错误消息（可空）
     * @param retryAfterMs        Retry-After 归一值（毫秒，可空）
     */
    public static ChatResponse error(ErrorClassification errorClassification,
                                     Integer httpStatus,
                                     String errorCode, String errorMessage,
                                     Long retryAfterMs) {
        ChatResponse response = new ChatResponse();
        response.setErrorClassification(errorClassification);
        response.setHttpStatus(httpStatus);
        response.setErrorCode(errorCode);
        response.setError(errorMessage);
        response.setRetryAfterMs(retryAfterMs);
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
        if (this.messages != null) {
            copy.messages = new ArrayList<>();
            for (ChatMessage msg : this.messages) {
                copy.messages.add(msg.copy());
            }
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
        copy.errorClassification = this.errorClassification;
        copy.retryAfterMs = this.retryAfterMs;
        copy.httpStatus = this.httpStatus;
        return copy;
    }
}
