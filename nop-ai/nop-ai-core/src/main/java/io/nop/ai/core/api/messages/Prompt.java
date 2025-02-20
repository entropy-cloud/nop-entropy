package io.nop.ai.core.api.messages;

import io.nop.ai.core.api.support.Metadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Prompt extends Metadata {
    private Float temperature;
    private Map<String, Object> variables;
    private List<AiMessage> messages = Collections.emptyList();

    public static Prompt userText(String text) {
        Prompt prompt = new Prompt();
        prompt.addUserMessage(text);
        return prompt;
    }

    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(Float temperature) {
        this.temperature = temperature;
    }

    public Object getVariable(String name) {
        return variables == null ? null : variables.get(name);
    }

    public void setVariable(String name, Object value) {
        if (variables == null)
            variables = new HashMap<>();
        variables.put(name, value);
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public boolean isSingleMessage() {
        return getMessages().size() == 1;
    }

    public List<AiMessage> getMessages() {
        return messages;
    }

    public void addMessage(AiMessage message) {
        if (this.messages.isEmpty())
            this.messages = new ArrayList<>();
        this.messages.add(message);
    }

    public void addUserMessage(String text) {
        addMessage(new AiUserMessage(text));
    }

    public void addMessages(Collection<AiMessage> messages) {
        if (messages.isEmpty()) {
            this.messages = new ArrayList<>();
        }
        this.messages.addAll(messages);
    }

    public void removeMessages(Collection<String> messageIds) {
        messages.removeIf(msg -> messageIds.contains(msg.getMessageId()));
    }
}