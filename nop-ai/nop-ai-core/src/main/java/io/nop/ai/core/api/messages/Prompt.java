package io.nop.ai.core.api.messages;

import io.nop.ai.core.api.support.Metadata;

import java.util.Collection;
import java.util.List;

public abstract class Prompt extends Metadata {
    public abstract List<Message> toMessages();

    public abstract void addMessage(Message message);

    public void addHumanMessage(String text){
        addMessage(new HumanMessage(text));
    }

    public abstract void addMessages(Collection<Message> messages);

    public abstract void removeMessages(Collection<String> messageIds);
}