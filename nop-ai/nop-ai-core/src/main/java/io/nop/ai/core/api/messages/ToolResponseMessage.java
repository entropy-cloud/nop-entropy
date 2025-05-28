package io.nop.ai.core.api.messages;

import io.nop.ai.core.AiCoreConstants;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class ToolResponseMessage extends AiMessage {
    private List<ToolCall> responses;

    @Override
    public String getRole() {
        return AiCoreConstants.ROLE_TOOL;
    }

    public List<ToolCall> getResponses() {
        return responses;
    }

    public void setResponses(List<ToolCall> responses) {
        this.responses = responses;
    }

    @Override
    public String getContent() {
        return null;
    }
}
