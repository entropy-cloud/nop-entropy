package io.nop.ai.translate;

import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.commons.debug.DebugMessageHelper;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

import static io.nop.ai.core.commons.debug.DebugMessageHelper.collectDebugText;

@DataBean
public class AggregateText {
    private List<AiChatResponse> messages;
    private String text;

    public AggregateText() {
    }

    public static AggregateText fromResultMessage(AiChatResponse message) {
        return new AggregateText(List.of(message), message.getContent());
    }

    public AggregateText(List<AiChatResponse> messages, String text) {
        this.messages = messages;
        this.text = text;
    }

    public boolean isAllValid() {
        for (AiChatResponse message : messages) {
            if (!message.isValid())
                return false;
        }
        return true;
    }

    public String getDebugText() {
        StringBuilder sb = new StringBuilder();
        for (AiChatResponse message : messages) {
            collectDebugText(sb, message);
            sb.append("\n");
            sb.append(DebugMessageHelper.MESSAGE_SEPARATOR);
            sb.append("\n");
        }
        return sb.toString();
    }

    public List<AiChatResponse> getMessages() {
        return messages;
    }

    public void setMessages(List<AiChatResponse> messages) {
        this.messages = messages;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
