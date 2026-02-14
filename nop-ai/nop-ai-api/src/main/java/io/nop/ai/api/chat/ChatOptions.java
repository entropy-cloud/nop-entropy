/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天选项，用于控制AI大模型的生成行为
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
     * 响应格式（如json、text等）
     */
    private String responseFormat;

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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(String responseFormat) {
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
        copy.responseFormat = this.responseFormat;

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
        if (other.responseFormat != null) merged.responseFormat = other.responseFormat;
        if (other.tools != null) {
            if (merged.tools == null) {
                merged.tools = new ArrayList<>();
            }
            for (ChatToolDefinition tool : other.tools) {
                merged.tools.add(tool.copy());
            }
        }
        if (other.toolChoice != null) merged.toolChoice = other.toolChoice;

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

        public Builder responseFormat(String responseFormat) {
            options.setResponseFormat(responseFormat);
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
