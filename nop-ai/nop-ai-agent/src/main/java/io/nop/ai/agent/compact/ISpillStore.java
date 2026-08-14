package io.nop.ai.agent.compact;

/**
 * Per-session spill store for oversized tool results (design §3.3 of the
 * context-compaction-economics design doc). When a successful tool result
 * exceeds the inline threshold, the dispatcher PUTs the full text here under
 * an opaque spill id and keeps only a bounded preview +
 * {@code [SPILL_REF id=...]} marker in the message history; the
 * {@code read-spill} tool reads the full text back through the same
 * session-scoped instance. If the store is full, a put may throw
 * {@link IllegalStateException}.
 * <p>
 * The store is always present on a session (default
 * {@link InMemorySpillStore} assembly — there is no null-store branch).
 * Failures are explicit: {@code put} must throw (memory/capacity) rather than
 * silently drop, so the caller degrades to plain truncation with a logged
 * warning. {@code get} of an unknown id must return {@code null} so the read
 * side reports an explicit "spill id not found" error (fail-loud).
 */
public interface ISpillStore {

    /**
     * Store the full content and return an opaque spill id that can be used
     * later to {@link #get(String)} the content back.
     *
     * @param content the full tool-result text; must be non-null
     * @return the opaque spill id
     * @throws IllegalArgumentException when content is null
     * @throws IllegalStateException    when the store cannot hold the content
     *                                  (memory/capacity) — caller degrades to
     *                                  truncation and logs a warning
     */
    String put(String content);

    /**
     * @param spillId the opaque spill id previously returned by
     *                {@link #put(String)}
     * @return the stored content, or {@code null} when the spill id is unknown
     *         or has been deleted
     */
    String get(String spillId);

    /**
     * Remove the stored content; unknown ids are ignored (idempotent).
     *
     * @param spillId the opaque spill id to remove; may be {@code null} (no-op)
     */
    void delete(String spillId);
}
