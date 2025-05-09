/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.api.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ExtensibleBean;

import java.util.ArrayList;
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
    private List<String> stop;

    private Long requestTimeout;

    private boolean stream;

    private boolean enableThinking = false;

    private String responseFormat;

    private Consumer<AiChatResponse> streamListener;

    //============= 以下为coze支持的参数 =====
    private String botId;

    private String conversationId;

    private String userId;

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

        if (this.stop != null) {
            clone.setStop(new ArrayList<>(this.stop)); // Create a new ArrayList with the same elements
        }

        clone.setRequestTimeout(this.requestTimeout);
        clone.setStream(this.stream);
        clone.setEnableThinking(this.enableThinking);
        clone.setResponseFormat(this.responseFormat);
        clone.setStreamListener(this.streamListener);

        // Coze-specific parameters
        clone.setBotId(this.botId);
        clone.setConversationId(this.conversationId);
        clone.setUserId(this.userId);

        clone.addAttrs(this.getAttrs());

        return clone;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Long getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    @JsonIgnore
    public Consumer<AiChatResponse> getStreamListener() {
        return streamListener;
    }

    public void setStreamListener(Consumer<AiChatResponse> streamListener) {
        this.streamListener = streamListener;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Float getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Float frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public String getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
    }

    public Integer getContextLength() {
        return contextLength;
    }

    public void setContextLength(Integer contextLength) {
        this.contextLength = contextLength;
    }

    public String getSeed() {
        return seed;
    }

    public void setSeed(String seed) {
        this.seed = seed;
    }

    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(Float temperature) {
        this.temperature = temperature;
    }

    public Float getTopP() {
        return topP;
    }

    public void setTopP(Float topP) {
        this.topP = topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public List<String> getStop() {
        return stop;
    }

    public void setStop(List<String> stop) {
        this.stop = stop;
    }

    public String getBotId() {
        return botId;
    }

    public void setBotId(String botId) {
        this.botId = botId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public boolean isEnableThinking() {
        return enableThinking;
    }

    public void setEnableThinking(boolean enableThinking) {
        this.enableThinking = enableThinking;
    }
}