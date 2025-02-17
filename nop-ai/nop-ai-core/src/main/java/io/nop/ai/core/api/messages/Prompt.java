package io.nop.ai.core.api.messages;

import io.nop.ai.core.api.support.Metadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Prompt extends Metadata {
    private List<Message> messages = Collections.emptyList();

    public static Prompt humanText(String text) {
        Prompt prompt = new Prompt();
        prompt.addHumanMessage(text);
        return prompt;
    }

    public boolean isSingleMessage() {
        return getMessages().size() == 1;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void addMessage(Message message) {
        if (this.messages.isEmpty())
            this.messages = new ArrayList<>();
        this.messages.add(message);
    }

    public void addHumanMessage(String text) {
        addMessage(new HumanMessage(text));
    }

    public void addMessages(Collection<Message> messages) {
        if (messages.isEmpty()) {
            this.messages = new ArrayList<>();
        }
        this.messages.addAll(messages);
    }

    public void removeMessages(Collection<String> messageIds) {
        messages.removeIf(msg -> messageIds.contains(msg.getMessageId()));
    }
}