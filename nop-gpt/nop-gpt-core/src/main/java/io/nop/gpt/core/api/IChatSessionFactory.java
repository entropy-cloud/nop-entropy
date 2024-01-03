package io.nop.gpt.core.api;

public interface IChatSessionFactory {
    IChatSession newSession(String initPrompt);

    IChatSession getSession(String sessionId);
}