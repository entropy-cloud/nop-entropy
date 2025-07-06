package io.nop.ai.api.mcp;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

@DataBean
public class McpToolCallProgress {
    private double progress; // 0.0 - 1.0
    private String message;
    private Map<String, Object> intermediateResult;

    // Getters and Setters
    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getIntermediateResult() {
        return intermediateResult;
    }

    public void setIntermediateResult(Map<String, Object> intermediateResult) {
        this.intermediateResult = intermediateResult;
    }
}