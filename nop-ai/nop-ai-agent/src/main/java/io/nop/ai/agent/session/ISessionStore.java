package io.nop.ai.agent.session;

import java.util.Collection;
import java.util.Map;
import io.nop.ai.agent.engine.NopAiAgentException;

public interface ISessionStore {

    AgentSession getOrCreate(String sessionId, String agentName);

    AgentSession get(String sessionId);

    void remove(String sessionId);

    Collection<AgentSession> getAll();

    /**
     * Discover and return <b>all</b> sessions known to this store, including
     * sessions that have been persisted but not yet loaded into the in-memory
     * cache. This is the auto-restore-on-startup discovery contract
     * (design §1.1 recovery model, plan 184): a new process that points a
     * fresh store instance at an existing persistent root must be able to
     * enumerate every persisted session so the engine's
     * {@code restorePendingSessions} orchestrator can select restore
     * candidates without a caller having to know any session id ahead of
     * time.
     *
     * <p><b>Relation to {@link #getAll()}</b>: {@code getAll()} returns only
     * the in-memory cache (its semantics are preserved unchanged for backward
     * compatibility). This method is the broader discovery view — for an
     * in-memory store the two are equivalent; for a file-backed store this
     * method scans the backing directory while {@code getAll()} does not.
     *
     * <p><b>Implementation overrides</b>:
     * <ul>
     *   <li>{@link InMemorySessionStore}: returns {@link #getAll()} — every
     *       in-memory session is already in the cache, there is no disk
     *       concept.</li>
     *   <li>{@code FileBackedSessionStore}: scans the root directory's
     *       per-session subdirectories and loads each {@code session.json};
     *       successfully loaded sessions are inserted into the cache so
     *       subsequent {@link #get} calls hit the cache. A corrupt or
     *       truncated {@code session.json} is skipped with a logged warning
     *       rather than aborting discovery of the rest (one corrupt file
     *       must not block discovery of all sessions — Minimum Rules #24:
     *       the corruption is surfaced via the warning, not silently
     *       swallowed).</li>
     * </ul>
     *
     * <p><b>Fail-fast</b>: the interface default throws
     * {@link UnsupportedOperationException} so that any store implementation
     * which silently does nothing on discovery is detected at runtime
     * (Minimum Rules #24 No Silent No-Op). A store that does not provide
     * discovery must surface this explicitly; the auto-restore orchestrator
     * treats the UOE as "store does not support discovery" and propagates it
     * as a {@link io.nop.ai.agent.engine.NopAiAgentException} rather than
     * silently returning an empty restore result.
     *
     * @return all sessions known to this store (cache + persisted-but-uncached);
     *         never null, possibly empty if the store is empty
     */
    default Collection<AgentSession> listAllSessions() {
        throw new UnsupportedOperationException(
                "listAllSessions requires a session store that supports discovery "
                        + "(e.g. FileBackedSessionStore or InMemorySessionStore)");
    }

    /**
     * Persist a session to the backing store so its state survives a process
     * restart (design §1.1 recovery model + plan 183 crash/restart restore
     * protocol). Implementations that do not provide persistence (e.g.
     * {@link InMemorySessionStore}) override this as a documented no-op
     * (in-memory readers already share the live session reference, so no extra
     * work is required). Implementations that provide persistence (e.g.
     * {@code FileBackedSessionStore}) override this to write the session to
     * disk.
     * <p>
     * <b>Fail-fast</b>: the interface default throws
     * {@link UnsupportedOperationException} so that any store implementation
     * which silently does nothing on {@code save} is detected at runtime
     * (Minimum Rules #24 No Silent No-Op), rather than silently dropping
     * persistence requests.
     *
     * @param session the session to persist; never null
     */
    default void save(AgentSession session) {
        throw new UnsupportedOperationException(
                "save requires a persistent session store (e.g. FileBackedSessionStore)");
    }

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
