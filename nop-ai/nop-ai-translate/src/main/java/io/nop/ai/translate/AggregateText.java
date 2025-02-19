package io.nop.ai.translate;

import io.nop.ai.core.api.messages.AiResultMessage;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class AggregateText {
    private List<AiResultMessage> messages;
    private String text;

    public AggregateText() {
    }

    public static AggregateText fromResultMessage(AiResultMessage message) {
        return new AggregateText(List.of(message), message.getContent());
    }

    public AggregateText(List<AiResultMessage> messages, String text) {
        this.messages = messages;
        this.text = text;
    }

    public String getDebugText() {
        StringBuilder sb = new StringBuilder();
        for (AiResultMessage message : messages) {
            message.collectDebugText(sb);
            sb.append("\n*****************************\n");
        }
        return sb.toString();
    }

    public List<AiResultMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AiResultMessage> messages) {
        this.messages = messages;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
