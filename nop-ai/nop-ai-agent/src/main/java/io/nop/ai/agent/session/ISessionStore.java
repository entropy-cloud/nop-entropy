package io.nop.ai.agent.session;

import java.util.Collection;
import java.util.Map;

public interface ISessionStore {

    AgentSession getOrCreate(String sessionId, String agentName);

    AgentSession get(String sessionId);

    void remove(String sessionId);

    Collection<AgentSession> getAll();

    default String forkSession(String parentSessionId, boolean inheritContext, Map<String, Object> props) {
        throw new UnsupportedOperationException("forkSession requires VfsSessionStore");
    }

    default long appendEvent(String sessionId, VfsEvent event) {
        throw new UnsupportedOperationException("appendEvent requires VfsSessionStore");
    }

    default CompactionResult compact(String sessionId, CompactConfig config) {
        throw new UnsupportedOperationException("compact requires VfsSessionStore");
    }

    default SessionSnapshot loadSnapshot(String sessionId, String snapshotId) {
        throw new UnsupportedOperationException("loadSnapshot requires VfsSessionStore");
    }

    default void setPlanRef(String sessionId, String planId) {
        throw new UnsupportedOperationException("setPlanRef requires VfsSessionStore");
    }
}
