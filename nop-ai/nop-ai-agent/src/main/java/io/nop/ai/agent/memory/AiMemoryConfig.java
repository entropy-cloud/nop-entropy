package io.nop.ai.agent.memory;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class AiMemoryConfig {
    private int trimRounds = 100;
    private boolean enableSummary = true;
    private int summaryRounds = 5;
    private int summaryContextLength = 10000;

    // 上一次消息的长度超过限制
    private int summarySingleContextLength = 10000;

    public int getTrimRounds() {
        return trimRounds;
    }

    public void setTrimRounds(int trimRounds) {
        this.trimRounds = trimRounds;
    }

    public boolean isEnableSummary() {
        return enableSummary;
    }

    public void setEnableSummary(boolean enableSummary) {
        this.enableSummary = enableSummary;
    }

    public int getSummaryRounds() {
        return summaryRounds;
    }

    public void setSummaryRounds(int summaryRounds) {
        this.summaryRounds = summaryRounds;
    }

    public int getSummaryContextLength() {
        return summaryContextLength;
    }

    public void setSummaryContextLength(int summaryContextLength) {
        this.summaryContextLength = summaryContextLength;
    }

    public int getSummarySingleContextLength() {
        return summarySingleContextLength;
    }

    public void setSummarySingleContextLength(int summarySingleContextLength) {
        this.summarySingleContextLength = summarySingleContextLength;
    }
}
