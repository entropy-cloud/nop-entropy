package io.nop.ai.core.api.messages;

import io.nop.ai.core.AiCoreConstants;
import io.nop.api.core.annotations.data.DataBean;

/**
 * @deprecated This internal AI core class is deprecated and will be removed in future versions.
 * Please use the new AI API instead.
 */
@DataBean
@Deprecated
public class AiToolResponseMessage extends AiMessage {
    private String toolCallId;
    private String name;
    private String content;

    @Override
    public String getRole() {
        return AiCoreConstants.ROLE_TOOL;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
