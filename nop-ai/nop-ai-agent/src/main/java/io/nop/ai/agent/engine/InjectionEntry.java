package io.nop.ai.agent.engine;

import io.nop.ai.api.chat.messages.ChatMessage;

/**
 * Plan 2289 (WI8): one queued injection — a chat message plus its
 * {@link InjectionSemantics}. The steering queue carries entries (not bare
 * messages) so the wakeup probe can filter by semantics and the round
 * boundary can route consumption (STEER → wakeup turn; FOLLOWUP/INJECT →
 * boundary append).
 */
public final class InjectionEntry {

    private final InjectionSemantics semantics;
    private final ChatMessage message;

    public InjectionEntry(InjectionSemantics semantics, ChatMessage message) {
        this.semantics = semantics != null ? semantics : InjectionSemantics.STEER;
        this.message = message;
    }

    public InjectionSemantics getSemantics() {
        return semantics;
    }

    public ChatMessage getMessage() {
        return message;
    }
}
