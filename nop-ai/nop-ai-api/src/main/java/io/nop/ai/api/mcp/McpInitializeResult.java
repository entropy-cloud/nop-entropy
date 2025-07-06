package io.nop.ai.api.mcp;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

@DataBean
public class McpInitializeResult {
    private Map<String, Object> capabilities;
    private String sessionId;

    // Getters and Setters
    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Map<String, Object> capabilities) {
        this.capabilities = capabilities;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}