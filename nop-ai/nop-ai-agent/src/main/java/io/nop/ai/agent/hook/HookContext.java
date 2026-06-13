package io.nop.ai.agent.hook;

import io.nop.ai.agent.engine.AgentExecutionContext;

import java.util.HashMap;
import java.util.Map;

public class HookContext {

    private final AgentLifecyclePoint lifecyclePoint;
    private final AgentExecutionContext executionContext;
    private final Map<String, Object> data;
    private String toolName;
    private String toolCallId;

    public HookContext(AgentLifecyclePoint lifecyclePoint, AgentExecutionContext executionContext) {
        this.lifecyclePoint = lifecyclePoint;
        this.executionContext = executionContext;
        this.data = new HashMap<>();
    }

    public AgentLifecyclePoint getLifecyclePoint() {
        return lifecyclePoint;
    }

    public AgentExecutionContext getExecutionContext() {
        return executionContext;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }
}
