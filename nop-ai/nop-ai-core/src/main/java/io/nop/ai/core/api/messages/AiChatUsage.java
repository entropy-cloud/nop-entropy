package io.nop.ai.core.api.messages;

import io.nop.api.core.annotations.data.DataBean;

/**
 * AI chat usage statistics. Part of the legacy {@code AiChat*} naming convention.
 * @deprecated Use {@link io.nop.ai.api.chat.messages.ChatUsage} instead.
 */
@DataBean
@Deprecated(forRemoval = true)
public class AiChatUsage {
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer usedTime;

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Integer getUsedTime() {
        return usedTime;
    }

    public void setUsedTime(Integer usedTime) {
        this.usedTime = usedTime;
    }
}
