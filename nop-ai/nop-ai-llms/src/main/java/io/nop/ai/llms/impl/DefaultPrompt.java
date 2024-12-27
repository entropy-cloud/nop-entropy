package io.nop.ai.llms.impl;

import io.nop.ai.core.api.messages.Message;
import io.nop.ai.core.api.messages.Prompt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DefaultPrompt extends Prompt {
    private List<Message> messages = Collections.emptyList();

    @Override
    public List<Message> toMessages() {
        return messages;
    }

    @Override
    public void addMessage(Message message) {
        if (this.messages.isEmpty())
            this.messages = new ArrayList<>();
        this.messages.add(message);
    }

    @Override
    public void addMessages(Collection<Message> messages) {
        if (messages.isEmpty()) {
            this.messages = new ArrayList<>();
        }
        this.messages.addAll(messages);
    }

    @Override
    public void removeMessages(Collection<String> messageIds) {
        messages.removeIf(msg -> messageIds.contains(msg.getMessageId()));
    }
}
