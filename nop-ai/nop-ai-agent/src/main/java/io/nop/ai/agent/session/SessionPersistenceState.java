package io.nop.ai.agent.session;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan 2295 (WI14): per-session bookkeeping of what the store has physically
 * persisted — the state the incremental save path diffs against. Keyed by
 * sessionId in its own map, deliberately <b>independent of the session
 * object cache</b>: the DB store bypasses the cache in tenant mode, and a
 * cache-anchored state would make the append path unreachable there.
 *
 * <p><b>Fingerprint semantics</b>: {@code prefixFingerprints[i]} is the
 * {@link ChatMessageFingerprints#fingerprint fingerprint} of the i-th message
 * as persisted. The append fast-path recomputes fingerprints of the incoming
 * prefix and requires per-position equality — reference equality is neither
 * required nor sufficient (guardrail rewrites mutate persisted instances in
 * place; compaction copies may be content-equal with fresh references).
 */
final class SessionPersistenceState {

    /** Fold threshold: append entries accumulate until this count, then the next save folds. */
    static final int FOLD_THRESHOLD = 64;

    /** Version of the base snapshot the journal currently extends. */
    long snapshotVersion;

    /** Fingerprints of the persisted prefix messages (position-aligned). */
    final List<Long> prefixFingerprints = new ArrayList<>();

    /** Number of journal entries currently accumulated since the last fold. */
    int journalEntryCount;

    boolean canAppend() {
        return journalEntryCount < FOLD_THRESHOLD;
    }

    /**
     * Reset to a freshly folded base: version bumped, journal empty, prefix
     * fingerprints recomputed from the folded message list.
     */
    void resetAfterFold(long newVersion, List<io.nop.ai.api.chat.messages.ChatMessage> messages) {
        this.snapshotVersion = newVersion;
        this.journalEntryCount = 0;
        this.prefixFingerprints.clear();
        for (io.nop.ai.api.chat.messages.ChatMessage message : messages) {
            this.prefixFingerprints.add(ChatMessageFingerprints.fingerprint(message));
        }
    }
}
