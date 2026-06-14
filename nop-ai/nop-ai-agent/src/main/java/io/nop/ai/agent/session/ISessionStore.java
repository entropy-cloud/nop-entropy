package io.nop.ai.agent.session;

import java.util.Collection;
import java.util.Map;

public interface ISessionStore {

    AgentSession getOrCreate(String sessionId, String agentName);

    AgentSession get(String sessionId);

    void remove(String sessionId);

    Collection<AgentSession> getAll();

    /**
     * Fork a session: create an independent child session based on the parent
     * identified by {@code parentSessionId}.
     * <p>
     * Contract of {@code props}:
     * <ul>
     *   <li>The key {@code "agentName"} (if present, must be a {@code String})
     *       overrides the child session's agent name; otherwise the child
     *       inherits the parent's agent name.</li>
     *   <li>All other entries are merged into the child session's metadata.</li>
     * </ul>
     * <p>
     * Contract of {@code inheritContext}:
     * <ul>
     *   <li>{@code true}: the child receives an independent snapshot copy of
     *       the parent's message history, {@code planId} reference, and
     *       metadata. After fork the two sessions are fully independent —
     *       appending messages to one does not affect the other.</li>
     *   <li>{@code false}: the child starts with an empty message history,
     *       {@code null} planId, and empty metadata (only {@code props}
     *       entries are merged in). The child always inherits the parent
     *       link ({@code parentSessionId}).</li>
     * </ul>
     * <p>
     * Fail-fast behaviour: if {@code parentSessionId} does not resolve to an
     * existing session, the implementation throws a runtime exception rather
     * than silently returning {@code null}.
     *
     * @param parentSessionId the parent session to fork from
     * @param inheritContext  whether to inherit the parent's message history,
     *                        planId, and metadata
     * @param props           additional properties (agentName override + metadata entries)
     * @return the new child session id
     */
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
