package io.nop.ai.api.mcp;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

@DataBean
public class McpToolCallRequest {
    private String toolName;
    private Map<String, Object> parameters;

    // Getters and Setters
    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
}