package io.nop.ai.core.api.tool;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

@DataBean
public class CallToolRequest {
    private String name;
    private Map<String, Object> arguments;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}
