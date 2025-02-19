package io.nop.ai.core.service;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.chat.IAiChatSession;
import io.nop.ai.core.api.messages.AiMessage;
import io.nop.ai.core.api.messages.Prompt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AbstractAiChatSession implements IAiChatSession {
    private String sessionId;
    private AiChatOptions chatOptions;

    private List<AiMessage> messages = Collections.emptyList();

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    public void setChatOptions(AiChatOptions chatOptions) {
        this.chatOptions = chatOptions;
    }

    public AiChatOptions getChatOptions() {
        return chatOptions;
    }

    @Override
    public List<AiMessage> getActiveHistoryMessages() {
        return messages;
    }

    @Override
    public void disableMessages(Collection<String> messageIds) {
        if (!this.messages.isEmpty())
            this.messages.removeIf(message -> messageIds.contains(message.getMessageId()));
    }

    @Override
    public void addMessages(Collection<AiMessage> messages) {
        if (this.messages.isEmpty())
            this.messages = new ArrayList<>();
        this.messages.addAll(messages);
    }

    @Override
    public void addMessage(AiMessage message) {
        if (this.messages.isEmpty())
            this.messages = new ArrayList<>();
        this.messages.add(message);
    }

    @Override
    public Prompt newPrompt(boolean includeHistory) {
        Prompt prompt = new Prompt();
        if (includeHistory)
            prompt.addMessages(messages);
        return prompt;
    }

    @Override
    public void close() throws Exception {

    }
}
