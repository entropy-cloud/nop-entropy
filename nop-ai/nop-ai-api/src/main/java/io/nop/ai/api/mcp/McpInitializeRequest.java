package io.nop.ai.api.mcp;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

@DataBean
public class McpInitializeRequest {
    private String clientName;
    private String clientVersion;
    private Map<String, Object> capabilities;

    // Getters and Setters
    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Map<String, Object> capabilities) {
        this.capabilities = capabilities;
    }
}