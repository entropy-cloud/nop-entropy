package io.nop.ai.core.api.messages;

import io.nop.ai.core.api.support.Metadata;

import java.util.List;

public abstract class Prompt extends Metadata {
    public abstract List<Message> toMessages();
}