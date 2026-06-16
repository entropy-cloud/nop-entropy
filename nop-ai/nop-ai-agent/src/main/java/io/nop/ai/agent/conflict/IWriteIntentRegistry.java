package io.nop.ai.agent.conflict;

/**
 * Process-level registry of active {@link WriteIntent}s, consulted by the
 * dispatch path to detect concurrent write conflicts before a tool is
 * allowed to execute.
 *
 * <p>Design {@code nop-ai-agent-multi-agent.md} §3.1 (Phase 1 simplified
 * in-process mechanism). The shipped implementation is
 * {@link InMemoryWriteIntentRegistry}; future successors may back this by
 * a database or distributed cache for cross-process conflict detection
 * (depends on L4-8 Actor Runtime).
 *
 * <p>Atomicity contract: {@link #registerAndGetConflicting} performs the
 * check-then-register step inside a single atomic region so two concurrent
 * registrations for the same file from different sessions cannot both
 * observe an empty conflicting set (TOCTOU-safe).
 */
public interface IWriteIntentRegistry {

    /**
     * Atomically register a new write intent and return the set of
     * already-registered intents on the same {@code filePath} whose
     * {@code sessionId} differs from {@code intent.sessionId}.
     *
     * <p>An empty returned set means "no cross-session conflict on this
     * path" — the intent has still been registered and the caller should
     * proceed. A non-empty set is a real conflict: the caller must
     * delegate to {@link IConflictStrategy#resolve} to decide allow/deny.
     *
     * <p>If the strategy denies the call, the registry is left in a
     * consistent state — the denied intent remains registered, which is
     * correct: a subsequent retry from the same session should still see
     * its own prior intent, and the conflicting session's intent is the
     * authoritative one. The denied session's intent is reclaimed when the
     * session terminates via {@link #releaseSession(String)}.
     *
     * @param intent the write intent to register; never null
     * @return the set of conflicting intents (other sessions, same path);
     *         never null, possibly empty
     */
    java.util.Set<WriteIntent> registerAndGetConflicting(WriteIntent intent);

    /**
     * Release (remove) every write intent belonging to the given session.
     * Called from the engine's session-lifecycle finally block when an
     * execution completes (so a finished session's stale intents do not
     * block future sessions from writing the same files).
     *
     * @param sessionId the session whose intents should be released; never
     *                  null
     */
    void releaseSession(String sessionId);
}
