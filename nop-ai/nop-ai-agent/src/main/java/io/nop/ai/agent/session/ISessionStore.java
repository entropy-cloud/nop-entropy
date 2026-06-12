package io.nop.ai.agent.session;

import java.util.Collection;

public interface ISessionStore {

    AgentSession getOrCreate(String sessionId, String agentName);

    AgentSession get(String sessionId);

    void remove(String sessionId);

    Collection<AgentSession> getAll();
}
