package io.nop.ai.agent.compact;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default per-session, in-memory {@link ISpillStore} (design §3.3). Lives for
 * the duration of a single {@code AgentSession}; the session holds the only
 * reference and the store is garbage-collected when the session ends.
 * <p>
 * Spill ids are opaque: a session-scoped sequence prefix plus a random UUID
 * suffix, so ids cannot be guessed across sessions and each put yields a
 * fresh entry. {@code put} rejects null content with
 * {@link NullPointerException}; {@code get} of an unknown id returns
 * {@code null} so the read side reports an explicit "spill id not found"
 * error (fail-loud, Minimum Rules #24).
 */
public final class InMemorySpillStore implements ISpillStore {

    /** Session-scoped monotonic sequence shared across all store instances. */
    private static final AtomicLong SEQ = new AtomicLong();

    /** Live spilled entries keyed by their opaque spill id. */
    private final Map<String, String> store = new HashMap<>();

    /** Opaque id prefix so spill ids embed the owning session. */
    private final String sessionPrefix;

    /** Default-constructed with {@code "anon"} session prefix. */
    public InMemorySpillStore() {
        this(null);
    }

    /**
     * @param sessionId owning session id, embedded as a prefix in every spill
     *                  id this store produces; {@code null} falls back to
     *                  {@code "anon"}
     */
    public InMemorySpillStore(final String sessionId) {
        this.sessionPrefix = sessionId != null ? sessionId : "anon";
    }

    @Override
    public String put(final String content) {
        Objects.requireNonNull(content, "spill content must not be null");
        String spillId = sessionPrefix + "-spill-" + SEQ.incrementAndGet()
                + "-" + UUID.randomUUID();
        store.put(spillId, content);
        return spillId;
    }

    @Override
    public String get(final String spillId) {
        return spillId == null ? null : store.get(spillId);
    }

    @Override
    public void delete(final String spillId) {
        if (spillId != null) {
            store.remove(spillId);
        }
    }

    /**
     * @return the number of live spilled entries (for test/diagnostics)
     */
    public int size() {
        return store.size();
    }
}
