package io.nop.ai.llms.impl;

import io.nop.ai.core.api.chat.ChatOptions;
import io.nop.ai.core.api.chat.IChatSession;
import io.nop.ai.core.api.messages.Message;
import io.nop.ai.core.api.messages.Prompt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AbstractChatSession implements IChatSession {
    private String sessionId;
    private ChatOptions chatOptions;

    private List<Message> messages = Collections.emptyList();

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    public void setChatOptions(ChatOptions chatOptions) {
        this.chatOptions = chatOptions;
    }

    public ChatOptions getChatOptions() {
        return chatOptions;
    }

    @Override
    public List<Message> getActiveHistoryMessages() {
        return messages;
    }

    @Override
    public void disableMessages(Collection<String> messageIds) {
        if (!this.messages.isEmpty())
            this.messages.removeIf(message -> messageIds.contains(message.getMessageId()));
    }

    @Override
    public void addMessages(Collection<Message> messages) {
        if (this.messages.isEmpty())
            this.messages = new ArrayList<>();
        this.messages.addAll(messages);
    }

    @Override
    public void addMessage(Message message) {
        if (this.messages.isEmpty())
            this.messages = new ArrayList<>();
        this.messages.add(message);
    }

    @Override
    public Prompt newPrompt(boolean includeHistory) {
        Prompt prompt = new DefaultPrompt();
        if (includeHistory)
            prompt.addMessages(messages);
        return prompt;
    }

    @Override
    public void close() throws Exception {

    }
}
