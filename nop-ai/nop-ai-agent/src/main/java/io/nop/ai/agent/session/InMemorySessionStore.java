package io.nop.ai.agent.session;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySessionStore implements ISessionStore {

    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();

    @Override
    public AgentSession getOrCreate(String sessionId, String agentName) {
        return sessions.computeIfAbsent(sessionId, id -> AgentSession.create(id, agentName));
    }

    @Override
    public AgentSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public Collection<AgentSession> getAll() {
        return sessions.values();
    }
}
