package io.nop.ai.core.api.messages;

import io.nop.ai.core.AiCoreConstants;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class ToolResponseMessage extends AiMessage {
    private List<ToolResponse> responses;

    @Override
    public String getRole() {
        return AiCoreConstants.ROLE_TOOL;
    }

    public List<ToolResponse> getResponses() {
        return responses;
    }

    public void setResponses(List<ToolResponse> responses) {
        this.responses = responses;
    }

    @Override
    public String getContent() {
        return null;
    }
}
