package io.nop.ai.agent.session;

import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Per-session, in-memory, per-compaction-event implementation of
 * {@link ICompactionSnapshotArchive}. Lives for the duration of a single
 * {@code AgentSession}; the session holds the only reference and the archive
 * is garbage-collected when the session ends (design §8.3 Decision B:
 * session-level lifecycle).
 *
 * <p><b>Per-compaction-event addressing</b>: each {@link #put} mints a fresh
 * {@code snapshotId} of the form {@code snap:<sessionId>:<ts>:<n>} (timestamp
 * + monotonic counter, traceable to the originating session and event) and
 * stores a defensive copy of the message list under it. The same message list
 * archived twice yields two distinct ids — this archive tracks events, not
 * content (contrast the §8.2 content-addressed {@code InSessionCompactionArchive}).
 *
 * <p><b>Fail-fast</b> (Minimum Rules #24): null/empty message lists are
 * rejected with {@link IllegalArgumentException} — never silently stored.
 * A null/empty archive entry would masquerade as "compaction happened but
 * produced nothing", hiding a real failure.
 *
 * <p>Design ref: {@code ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md}
 * §8.3 (Decision B: first-version in-session memory impl, session-scoped).
 */
public final class InSessionCompactionSnapshotArchive implements ICompactionSnapshotArchive {

    private final Map<String, List<ChatMessage>> store = new HashMap<>();
    private final String sessionId;
    private long counter = 0;

    public InSessionCompactionSnapshotArchive(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public String put(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException(
                    "InSessionCompactionSnapshotArchive.put requires non-null, non-empty messages");
        }
        // Defensive copy so later mutation of the live message list cannot
        // corrupt the archived original (reversibility guarantee).
        String snapshotId = "snap:" + (sessionId != null ? sessionId : "anon")
                + ":" + System.currentTimeMillis() + ":" + (++counter);
        store.put(snapshotId, List.copyOf(messages));
        return snapshotId;
    }

    @Override
    public List<ChatMessage> get(String snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        return store.get(snapshotId);
    }

    /**
     * @return whether a snapshot is archived under this id (for test/diagnostics)
     */
    public boolean contains(String snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        return store.containsKey(snapshotId);
    }

    /**
     * @return the number of archived snapshots (for test/diagnostics)
     */
    public int size() {
        return store.size();
    }
}
