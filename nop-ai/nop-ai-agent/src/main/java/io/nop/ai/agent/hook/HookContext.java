package io.nop.ai.agent.hook;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.middleware.AttemptContext;

import java.util.HashMap;
import java.util.Map;

public class HookContext {

    private final AgentLifecyclePoint lifecyclePoint;
    private final AgentExecutionContext executionContext;
    private final Map<String, Object> data;
    private String toolName;
    private String toolCallId;
    // W3-1 (decision D2): per-attempt context, populated only when an
    // execution-level middleware fires. Null on session-level invocations.
    private AttemptContext attemptContext;

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

    /**
     * W3-1 (decision D2): the per-attempt context. Non-null only when an
     * execution-level ({@link io.nop.ai.agent.middleware.MiddlewareScope#EXECUTION})
     * middleware fires; {@code null} on session-level middleware / hook
     * invocations where attempt is not a meaningful concept.
     *
     * @return the attempt context, or {@code null} for session-level invocations
     */
    public AttemptContext getAttemptContext() {
        return attemptContext;
    }

    public void setAttemptContext(AttemptContext attemptContext) {
        this.attemptContext = attemptContext;
    }
}
