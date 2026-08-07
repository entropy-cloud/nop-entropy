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
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天选项，用于控制AI大模型的生成行为（new AI API，{@code IChatService} 使用）。
 * <p>
 * <b>P2-MA3-06 ruling — historical residue documented, no merge this batch:</b>
 * the legacy pipeline keeps its own options type
 * {@code io.nop.ai.core.api.chat.AiChatOptions} (deprecated, used by
 * {@code IAiChatService}/{@code AiCommand}); the two classes overlap on a common
 * subset of fields but each carries pipeline-specific fields (this class:
 * {@code tools}/{@code toolChoice}/{@code responseFormat}; the legacy class:
 * {@code seed}/{@code contextLength}/{@code workMode}/{@code botId}/
 * {@code conversationId}/{@code userId}/{@code streamListener}/
 * {@code enabledTools} etc.). Consolidation into a single options type is part of
 * the legacy-pipeline migration (future major version). New code must use this class.
 */
@DataBean
public class ChatOptions {
    /**
     * 会话ID，用于多轮对话
     */
    private String sessionId;

    /**
     * AI提供商（如openai、coze等）
     */
    private String provider;

    /**
     * 模型名称（如gpt-4、claude-3等）
     */
    private String model;

    /**
     * 采样温度，控制输出的随机性（0.0-2.0）
     */
    private Float temperature;

    /**
     * 核采样，控制输出词汇的多样性（0.0-1.0）
     */
    private Float topP;

    /**
     * Top-K采样
     */
    private Integer topK;

    /**
     * 最大生成token数
     */
    private Integer maxTokens;

    /**
     * 频率惩罚（-2.0到2.0）
     */
    private Float frequencyPenalty;

    /**
     * 存在惩罚（-2.0到2.0）
     */
    private Float presencePenalty;

    /**
     * 停止序列，当生成到这些内容时停止
     */
    private List<String> stop;

    /**
     * 请求超时时间（毫秒）
     */
    private Long requestTimeout;

    /**
     * 是否使用流式输出
     */
    private Boolean stream;

    /**
     * 是否启用思考过程（用于推理模型）
     */
    private Boolean enableThinking;

    /**
     * 响应格式（对象载体，plan 326）。规范取值见 {@link ResponseFormat}。
     * 升级自纯 String 字段，以承载结构化 {@code json_schema}（ResponsesDialect 前置）。
     * 旧的 String 视图经 {@link #getResponseFormat()} / {@link #setResponseFormat(String)} 透传，
     * 向后兼容（如 {@code WfAiHelper} 的 {@code "json"} 用法）。
     */
    private ResponseFormat responseFormat;

    /**
     * 可用工具定义列表
     */
    private List<ChatToolDefinition> tools;

    /**
     * 工具选择策略
     * - "auto": 模型自行决定是否使用工具（默认）
     * - "none": 强制模型不使用工具（OpenAI）
     * - "required" / "any": 强制模型必须使用至少一个工具
     * - {"type": "function", "function": {"name": "xxx"}}: 强制使用指定工具
     */
    private String toolChoice;

    /**
     * [账号回退链 plan 2026-08-01-1505-1，设计 §3.6] 当前调用所用的账号 apiKey 值。
     * 由 agent 层重试循环在 QUOTA_EXCEEDED/AUTH_INVALID FALLBACK 时从备用账号链取下一个账号后设置，
     * 经本字段跨层下沉到 {@code ChatServiceImpl.buildHttpRequest}（nop-ai-core）——后者读它而非按
     * provider 解析单 key。为空（默认）时退回 {@code resolveApiKey(provider)}（今日行为，零回归）。
     * <p>
     * <b>序列化排除</b>：apiKey 是机密，{@code @JsonIgnore} 确保它永不序列化进请求体/日志/审计。
     * apiKey 走 HTTP header（与今日 {@code resolveApiKey}→{@code dialect.setHeaders} 同一通道，
     * 无新增泄漏面），{@code @JsonIgnore} 仅防止经 {@code ChatOptions} 序列化额外泄漏。
     * <p>
     * <b>copy()/merge() 已同步</b>（Rule #11：否则复制/合并静默丢账号）。
     */
    @JsonIgnore
    private String accountKey;

    /**
     * [账号回退链] 可选 per-account baseUrl 覆盖（不同账号可对应不同代理/区域 endpoint）。
     * 由 agent 层与 {@link #accountKey} 一起设置，下沉到 {@code ChatServiceImpl}。
     * 为空时退回 provider 级 baseUrl（今日行为）。{@code @JsonIgnore} 同 {@link #accountKey}。
     */
    @JsonIgnore
    private String accountBaseUrl;

    public ChatOptions() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(Float temperature) {
        this.temperature = temperature;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Float getTopP() {
        return topP;
    }

    public void setTopP(Float topP) {
        this.topP = topP;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Float getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Float frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Float getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Float presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> getStop() {
        return stop;
    }

    public void setStop(List<String> stop) {
        this.stop = stop;
    }

    public void addStop(String stopToken) {
        if (this.stop == null) {
            this.stop = new ArrayList<>();
        }
        this.stop.add(stopToken);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getEnableThinking() {
        return enableThinking;
    }

    public void setEnableThinking(Boolean enableThinking) {
        this.enableThinking = enableThinking;
    }

    /**
     * 响应格式对象载体（规范访问，plan 326）。Plan 329：String 视图委托已删除。
     */
    public ResponseFormat getResponseFormatConfig() {
        return responseFormat;
    }

    public void setResponseFormatConfig(ResponseFormat responseFormat) {
        this.responseFormat = responseFormat;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<ChatToolDefinition> getTools() {
        return tools;
    }

    public void setTools(List<ChatToolDefinition> tools) {
        this.tools = tools;
    }

    public void addTool(ChatToolDefinition tool) {
        if (this.tools == null) {
            this.tools = new ArrayList<>();
        }
        this.tools.add(tool);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(String toolChoice) {
        this.toolChoice = toolChoice;
    }

    /**
     * 当前调用所用的账号 apiKey（设计 §3.6，跨层下沉载体）。{@code @JsonIgnore} 不参与序列化。
     */
    public String getAccountKey() {
        return accountKey;
    }

    public void setAccountKey(String accountKey) {
        this.accountKey = accountKey;
    }

    /**
     * 可选 per-account baseUrl 覆盖（设计 §3.6）。{@code @JsonIgnore} 不参与序列化。
     */
    public String getAccountBaseUrl() {
        return accountBaseUrl;
    }

    public void setAccountBaseUrl(String accountBaseUrl) {
        this.accountBaseUrl = accountBaseUrl;
    }

    /**
     * 禁用工具（强制模型不使用工具）
     * OpenAI: tool_choice = "none"
     */
    public void disableTools() {
        this.toolChoice = "none";
    }

    /**
     * 自动选择工具（默认行为）
     * OpenAI/Claude: tool_choice = "auto"
     */
    public void autoToolChoice() {
        this.toolChoice = "auto";
    }

    /**
     * 强制模型必须使用至少一个工具
     * OpenAI: tool_choice = "required"
     * Claude: tool_choice = "any"
     */
    public void requireTool() {
        this.toolChoice = "required";
    }

    /**
     * 强制使用指定工具
     * OpenAI: {"type": "function", "function": {"name": "xxx"}}
     * Claude: {"type": "tool", "name": "xxx"}
     */
    public void forceTool(String toolName) {
        this.toolChoice = "tool:" + toolName;
    }

    /**
     * 创建当前选项的深拷贝
     *
     * @return ChatOptions 的深拷贝实例
     */
    public ChatOptions copy() {
        ChatOptions copy = new ChatOptions();

        // 复制基本类型字段
        copy.sessionId = this.sessionId;
        copy.provider = this.provider;
        copy.model = this.model;
        copy.temperature = this.temperature;
        copy.topP = this.topP;
        copy.topK = this.topK;
        copy.maxTokens = this.maxTokens;
        copy.frequencyPenalty = this.frequencyPenalty;
        copy.presencePenalty = this.presencePenalty;
        copy.requestTimeout = this.requestTimeout;
        copy.stream = this.stream;
        copy.enableThinking = this.enableThinking;
        copy.responseFormat = this.responseFormat != null ? this.responseFormat.copy() : null;

        // 深拷贝 stop 列表
        if (this.stop != null) {
            copy.stop = new ArrayList<>(this.stop);
        }

        // 深拷贝 tools 列表
        if (this.tools != null) {
            copy.tools = new ArrayList<>();
            for (ChatToolDefinition tool : this.tools) {
                copy.tools.add(tool.copy());
            }
        }

        copy.toolChoice = this.toolChoice;

        // 账号回退链字段（设计 §3.6）：必须随 copy() 携带，否则重试循环的 routedOptions
        // 重赋值会静默丢账号（Rule #11 陷阱）。
        copy.accountKey = this.accountKey;
        copy.accountBaseUrl = this.accountBaseUrl;

        return copy;
    }

    /**
     * 合并另一个选项对象，非null值会覆盖当前值
     *
     * @param other 另一个选项对象
     * @return 合并后的新实例
     */
    public ChatOptions merge(ChatOptions other) {
        if (other == null) {
            return this.copy();
        }

        ChatOptions merged = this.copy();

        if (other.sessionId != null) merged.sessionId = other.sessionId;
        if (other.provider != null) merged.provider = other.provider;
        if (other.model != null) merged.model = other.model;
        if (other.temperature != null) merged.temperature = other.temperature;
        if (other.topP != null) merged.topP = other.topP;
        if (other.topK != null) merged.topK = other.topK;
        if (other.maxTokens != null) merged.maxTokens = other.maxTokens;
        if (other.frequencyPenalty != null) merged.frequencyPenalty = other.frequencyPenalty;
        if (other.presencePenalty != null) merged.presencePenalty = other.presencePenalty;
        if (other.stop != null) {
            if (merged.stop == null) {
                merged.stop = new ArrayList<>(other.stop);
            } else {
                merged.stop.addAll(other.stop);
            }
        }
        if (other.requestTimeout != null) merged.requestTimeout = other.requestTimeout;
        if (other.stream != null) merged.stream = other.stream;
        if (other.enableThinking != null) merged.enableThinking = other.enableThinking;
        if (other.responseFormat != null) merged.responseFormat = other.responseFormat.copy();
        if (other.tools != null) {
            if (merged.tools == null) {
                merged.tools = new ArrayList<>();
            }
            for (ChatToolDefinition tool : other.tools) {
                merged.tools.add(tool.copy());
            }
        }
        if (other.toolChoice != null) merged.toolChoice = other.toolChoice;

        // 账号回退链字段（设计 §3.6）：merge 时 other 的账号身份覆盖 this（重试循环切换账号时
        // 用新 ChatOptions merge 到 routedOptions，账号身份须随之切换）。
        if (other.accountKey != null) merged.accountKey = other.accountKey;
        if (other.accountBaseUrl != null) merged.accountBaseUrl = other.accountBaseUrl;

        return merged;
    }


    /**
     * 构建器模式创建选项
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * ChatOptions 构建器
     */
    public static class Builder {
        private final ChatOptions options = new ChatOptions();

        public Builder sessionId(String sessionId) {
            options.setSessionId(sessionId);
            return this;
        }

        public Builder provider(String provider) {
            options.setProvider(provider);
            return this;
        }

        public Builder model(String model) {
            options.setModel(model);
            return this;
        }

        public Builder temperature(Float temperature) {
            options.setTemperature(temperature);
            return this;
        }

        public Builder topP(Float topP) {
            options.setTopP(topP);
            return this;
        }

        public Builder topK(Integer topK) {
            options.setTopK(topK);
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            options.setMaxTokens(maxTokens);
            return this;
        }

        public Builder frequencyPenalty(Float frequencyPenalty) {
            options.setFrequencyPenalty(frequencyPenalty);
            return this;
        }

        public Builder presencePenalty(Float presencePenalty) {
            options.setPresencePenalty(presencePenalty);
            return this;
        }

        public Builder stop(List<String> stop) {
            options.setStop(stop);
            return this;
        }

        public Builder addStop(String stop) {
            options.addStop(stop);
            return this;
        }

        public Builder requestTimeout(Long requestTimeout) {
            options.setRequestTimeout(requestTimeout);
            return this;
        }

        public Builder stream(Boolean stream) {
            options.setStream(stream);
            return this;
        }

        public Builder enableThinking(Boolean enableThinking) {
            options.setEnableThinking(enableThinking);
            return this;
        }

        public Builder responseFormatConfig(ResponseFormat responseFormat) {
            options.setResponseFormatConfig(responseFormat);
            return this;
        }

        public Builder tools(List<ChatToolDefinition> tools) {
            options.setTools(tools);
            return this;
        }

        public Builder addTool(ChatToolDefinition tool) {
            options.addTool(tool);
            return this;
        }

        public Builder addTool(String name, String description) {
            options.addTool(ChatToolDefinition.of(name, description));
            return this;
        }

        public Builder toolChoice(String toolChoice) {
            options.setToolChoice(toolChoice);
            return this;
        }

        public Builder accountKey(String accountKey) {
            options.setAccountKey(accountKey);
            return this;
        }

        public Builder accountBaseUrl(String accountBaseUrl) {
            options.setAccountBaseUrl(accountBaseUrl);
            return this;
        }

        public Builder disableTools() {
            options.disableTools();
            return this;
        }

        public Builder autoToolChoice() {
            options.autoToolChoice();
            return this;
        }

        public Builder requireTool() {
            options.requireTool();
            return this;
        }

        public ChatOptions build() {
            return options.copy();
        }
    }
}
