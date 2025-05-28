package io.nop.ai.core.api.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.ai.core.AiCoreConstants;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class AiAssistantMessage extends AbstractTextMessage {
    private String think;

    private List<ToolCall> toolCalls;

    @Override
    public String getRole() {
        return AiCoreConstants.ROLE_ASSISTANT;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getThink() {
        return think;
    }

    public void setThink(String think) {
        this.think = think;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }
}
