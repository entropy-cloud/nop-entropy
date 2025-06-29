package io.nop.ai.translate;

import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.persist.DefaultAiChatExchangePersister;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class AggregateText {
    private List<AiChatExchange> messages;
    private String text;

    public AggregateText() {
    }

    public static AggregateText fromResultMessage(AiChatExchange message) {
        return new AggregateText(List.of(message), message.getResultText());
    }

    public AggregateText(List<AiChatExchange> messages, String text) {
        this.messages = messages;
        this.text = text;
    }

    public boolean isAllValid() {
        for (AiChatExchange message : messages) {
            if (!message.isValid())
                return false;
        }
        return true;
    }

    public String getDebugText() {
        return DefaultAiChatExchangePersister.instance().serializeList(messages);
    }

    public List<AiChatExchange> getMessages() {
        return messages;
    }

    public void setMessages(List<AiChatExchange> messages) {
        this.messages = messages;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
