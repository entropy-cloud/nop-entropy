package io.nop.ai.core.api.messages;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * @deprecated Use {@link io.nop.ai.api.chat.messages.ChatMessage} (the new AI API text-message base) instead.
 */
@Deprecated
public abstract class AbstractTextMessage extends AiMessage {
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getThink() {
        return null;
    }

    public void setThink(String think) {

    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<AiMessageAttachment> getAttachments() {
        return null;
    }

    public void setAttachments(List<AiMessageAttachment> attachments) {

    }
}
