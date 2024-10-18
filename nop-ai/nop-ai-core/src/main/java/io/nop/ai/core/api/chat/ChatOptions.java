/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.api.chat;

import io.nop.api.core.beans.ExtensibleBean;

import java.util.List;

public class ChatOptions extends ExtensibleBean {
    private IChatProgressListener progressListener;

    public ChatOptions progressListener(IChatProgressListener progressListener) {
        this.setProgressListener(progressListener);
        return this;
    }

    public IChatProgressListener getProgressListener() {
        return progressListener;
    }

    public void setProgressListener(IChatProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    private String seed;
    private Float temperature;
    private Float topP;
    private Integer topK;
    private Integer maxTokens;
    private List<String> stop;

    //============= 以下为coze支持的参数 =====
    private String botId;

    private String conversationId;

    private String userId;

    private boolean stream;

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
}