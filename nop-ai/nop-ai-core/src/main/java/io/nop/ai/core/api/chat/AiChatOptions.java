/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.api.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.tool.IToolSet;
import io.nop.ai.core.api.tool.ToolSpecification;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ExtensibleBean;
import io.nop.commons.collections.IKeyedList;
import io.nop.commons.collections.KeyedList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@DataBean
public class AiChatOptions extends ExtensibleBean {
    private String sessionId;
    private String provider;
    private String model;
    private String seed;
    private Float temperature;
    private Float topP;
    private Integer topK;
    private Integer maxTokens;
    private Integer contextLength;
    private Float frequencyPenalty;
    private Float presencePenalty;
    private List<String> stop;

    private Long requestTimeout;

    private Boolean stream;

    private Boolean enableThinking;

    private String responseFormat;

    private Consumer<AiChatExchange> streamListener;

    private Boolean disableCache;

    /**
     * 工作模式，用于加载系统提示
     */
    private String workMode;
    private Boolean enableCognitivePrompt;
    private Boolean enableMetaPrompt;
    private Boolean enableSystemPrompt;


    //============= 以下为coze支持的参数 =====
    private String botId;

    private String conversationId;

    private String userId;

    private IKeyedList<ToolSpecification> tools = KeyedList.emptyList();

    public AiChatOptions cloneInstance() {
        AiChatOptions clone = new AiChatOptions();

        clone.setSessionId(this.sessionId);
        clone.setProvider(this.provider);
        clone.setModel(this.model);
        clone.setSeed(this.seed);
        clone.setTemperature(this.temperature);
        clone.setTopP(this.topP);
        clone.setTopK(this.topK);
        clone.setMaxTokens(this.maxTokens);
        clone.setContextLength(this.contextLength);
        clone.setFrequencyPenalty(this.frequencyPenalty);
        clone.setPresencePenalty(this.presencePenalty);

        if (this.stop != null) {
            clone.setStop(new ArrayList<>(this.stop)); // Create a new ArrayList with the same elements
        }

        clone.setRequestTimeout(this.requestTimeout);
        clone.setStream(this.stream);
        clone.setEnableThinking(this.enableThinking);
        clone.setResponseFormat(this.responseFormat);
        clone.setStreamListener(this.streamListener);
        clone.setDisableCache(this.disableCache);
        clone.setWorkMode(this.workMode);
        clone.setEnableCognitivePrompt(this.enableCognitivePrompt);
        clone.setEnableMetaPrompt(this.enableMetaPrompt);
        clone.setEnableSystemPrompt(this.enableSystemPrompt);

        // Coze-specific parameters
        clone.setBotId(this.botId);
        clone.setConversationId(this.conversationId);
        clone.setUserId(this.userId);

        clone.addAttrs(this.getAttrs());

        if (tools != null)
            clone.setTools(new ArrayList<>(tools));

        return clone;
    }

    public void update(AiChatOptions options) {
        if (options == null) {
            return;
        }

        if (options.getWorkMode() != null)
            this.setWorkMode(options.getWorkMode());

        if (options.getSessionId() != null) {
            this.setSessionId(options.getSessionId());
        }
        if (options.getProvider() != null) {
            this.setProvider(options.getProvider());
        }
        if (options.getModel() != null) {
            this.setModel(options.getModel());
        }
        if (options.getSeed() != null) {
            this.setSeed(options.getSeed());
        }
        if (options.getTemperature() != null) {
            this.setTemperature(options.getTemperature());
        }
        if (options.getTopP() != null) {
            this.setTopP(options.getTopP());
        }
        if (options.getTopK() != null) {
            this.setTopK(options.getTopK());
        }
        if (options.getMaxTokens() != null) {
            this.setMaxTokens(options.getMaxTokens());
        }
        if (options.getContextLength() != null) {
            this.setContextLength(options.getContextLength());
        }
        if (options.getFrequencyPenalty() != null) {
            this.setFrequencyPenalty(options.getFrequencyPenalty());
        }
        if (options.getPresencePenalty() != null) {
            this.setPresencePenalty(options.getPresencePenalty());
        }

        if (options.getStop() != null) {
            this.setStop(new ArrayList<>(options.getStop()));
        }
        if (options.getRequestTimeout() != null) {
            this.setRequestTimeout(options.getRequestTimeout());
        }
        if (options.getStream() != null) {
            this.setStream(options.getStream());
        }
        if (options.getEnableThinking() != null) {
            this.setEnableThinking(options.getEnableThinking());
        }
        if (options.getResponseFormat() != null) {
            this.setResponseFormat(options.getResponseFormat());
        }
        if (options.getStreamListener() != null) {
            this.setStreamListener(options.getStreamListener());
        }

        if (options.getDisableCache() != null) {
            this.setDisableCache(options.getDisableCache());
        }

        // Coze-specific parameters
        if (options.getBotId() != null) {
            this.setBotId(options.getBotId());
        }
        if (options.getConversationId() != null) {
            this.setConversationId(options.getConversationId());
        }
        if (options.getUserId() != null) {
            this.setUserId(options.getUserId());
        }

        if (options.getAttrs() != null) {
            this.addAttrs(options.getAttrs());
        }

        if (options.getTools() != null)
            this.addTools(options.getTools());
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getWorkMode() {
        return workMode;
    }

    public void setWorkMode(String workMode) {
        this.workMode = workMode;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Boolean getEnableCognitivePrompt() {
        return enableCognitivePrompt;
    }

    public void setEnableCognitivePrompt(Boolean enableCognitivePrompt) {
        this.enableCognitivePrompt = enableCognitivePrompt;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Boolean getEnableMetaPrompt() {
        return enableMetaPrompt;
    }

    public void setEnableMetaPrompt(Boolean enableMetaPrompt) {
        this.enableMetaPrompt = enableMetaPrompt;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Boolean getEnableSystemPrompt() {
        return enableSystemPrompt;
    }

    public void setEnableSystemPrompt(Boolean enableSystemPrompt) {
        this.enableSystemPrompt = enableSystemPrompt;
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
    public Long getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    @JsonIgnore
    public Consumer<AiChatExchange> getStreamListener() {
        return streamListener;
    }

    public void setStreamListener(Consumer<AiChatExchange> streamListener) {
        this.streamListener = streamListener;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Float getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Float frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getContextLength() {
        return contextLength;
    }

    public void setContextLength(Integer contextLength) {
        this.contextLength = contextLength;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getSeed() {
        return seed;
    }

    public void setSeed(String seed) {
        this.seed = seed;
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
    public List<String> getStop() {
        return stop;
    }

    public void setStop(List<String> stop) {
        this.stop = stop;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getBotId() {
        return botId;
    }

    public void setBotId(String botId) {
        this.botId = botId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Float getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Float presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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
    public Boolean getDisableCache() {
        return disableCache;
    }

    public void setDisableCache(Boolean disableCache) {
        this.disableCache = disableCache;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<ToolSpecification> getTools() {
        return tools;
    }

    public void setTools(List<ToolSpecification> tools) {
        this.tools = KeyedList.fromList(tools, ToolSpecification::getName);
    }

    public ToolSpecification getTool(String name) {
        return tools.getByKey(name);
    }

    public AiChatOptions addTool(ToolSpecification tool) {
        tools.add(tool);
        return this;
    }

    public AiChatOptions addTools(Collection<ToolSpecification> tools) {
        this.tools.addAll(tools);
        return this;
    }

    public AiChatOptions addToolSet(IToolSet toolSet) {
        return addTools(toolSet.getTools());
    }
}