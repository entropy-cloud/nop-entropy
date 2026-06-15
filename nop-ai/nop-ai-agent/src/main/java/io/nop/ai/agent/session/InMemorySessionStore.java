package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.NopAiAgentException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySessionStore implements ISessionStore {

    static final String PROPS_KEY_AGENT_NAME = "agentName";

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

    /**
     * {@inheritDoc}
     * <p>
     * <b>In-memory store semantics</b>: every session held by an
     * {@code InMemorySessionStore} already lives in the in-memory cache
     * (there is no disk concept), so discovery is equivalent to
     * {@link #getAll()}. A fresh {@code InMemorySessionStore} instance has
     * an empty cache — this method correctly returns an empty collection,
     * which is the "no persisted sessions to restore" signal consumed by
     * the auto-restore orchestrator (it returns an empty restore summary
     * rather than failing, because "no unfinished sessions" is a legitimate
     * state).
     */
    @Override
    public Collection<AgentSession> listAllSessions() {
        return getAll();
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>In-memory store semantics</b>: this override is a documented no-op,
     * not a silent skip. The {@code InMemorySessionStore} holds the live
     * {@link AgentSession} object reference returned by {@code getOrCreate} /
     * {@code get}; every reader of the store sees the latest in-memory state
     * directly through that reference. Therefore persisting the session to a
     * side-channel is unnecessary for an in-memory store: there is nothing to
     * copy. This is the correct, complete implementation of {@code save} for
     * an in-memory backend — distinct from the interface default
     * {@link UnsupportedOperationException}, which is reserved for stores that
     * claim no persistence capability at all (and thus must fail fast to
     * surface programmer error rather than silently swallow the save).
     */
    @Override
    public void save(AgentSession session) {
        // No-op: see Javadoc above. The live session object already shared via
        // the in-memory map is the source of truth; no extra persistence work
        // is required for an in-memory backend.
    }

    @Override
    public String forkSession(String parentSessionId, boolean inheritContext, Map<String, Object> props) {
        AgentSession parent = sessions.get(parentSessionId);
        if (parent == null) {
            throw new NopAiAgentException(
                    "forkSession failed: parent session not found: parentSessionId=" + parentSessionId);
        }

        String childAgentName = resolveChildAgentName(parent, props);
        String childSessionId = UUID.randomUUID().toString();
        AgentSession child = AgentSession.create(childSessionId, childAgentName);

        if (inheritContext) {
            child.appendMessages(parent.getMessages());
            child.setPlanId(parent.getPlanId());
            child.setMetadata(parent.getMetadata());
        }

        mergeProps(child, props);

        child.setParentSessionId(parentSessionId);
        sessions.put(childSessionId, child);

        return childSessionId;
    }

    private static String resolveChildAgentName(AgentSession parent, Map<String, Object> props) {
        if (props != null) {
            Object agentNameValue = props.get(PROPS_KEY_AGENT_NAME);
            if (agentNameValue instanceof String && !((String) agentNameValue).isEmpty()) {
                return (String) agentNameValue;
            }
        }
        return parent.getAgentName();
    }

    private static void mergeProps(AgentSession child, Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return;
        }
        Map<String, Object> merged = new HashMap<>(child.getMetadata());
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (!PROPS_KEY_AGENT_NAME.equals(entry.getKey())) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        child.setMetadata(merged);
    }
}
