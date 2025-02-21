package io.nop.ai.core.api.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.ai.core.AiCoreConstants;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class AiAssistantMessage extends AbstractTextMessage {
    private String think;

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

}
