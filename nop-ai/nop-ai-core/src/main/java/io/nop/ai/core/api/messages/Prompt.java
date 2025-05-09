package io.nop.ai.core.api.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.ai.core.api.support.Metadata;
import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DataBean
public class Prompt extends Metadata {
    private int retryTimes;
    private Map<String, Object> variables;
    private List<AiMessage> messages = Collections.emptyList();

    public static Prompt userText(String text) {
        Prompt prompt = new Prompt();
        prompt.addUserMessage(text);
        return prompt;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
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

    @JsonIgnore
    public boolean isSingleMessage() {
        return getMessages().size() == 1;
    }

    public List<AiMessage> getMessages() {
        return messages;
    }

    @JsonIgnore
    public AiMessage getLastMessage() {
        return messages.get(messages.size() - 1);
    }

    public void addMessage(AiMessage message) {
        if (this.messages.isEmpty())
            this.messages = new ArrayList<>();
        this.messages.add(message);
    }

    public void addSystemMessage(String text) {
        addMessage(new AiSystemMessage(text));
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