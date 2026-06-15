package io.nop.ai.agent.reliability;

import java.util.ArrayList;
import java.util.List;
import io.nop.ai.agent.engine.NopAiAgentException;

/**
 * Compaction-aware truncation helper (plan 188). Shared by
 * {@link FileBackedCheckpointManager#loadSessionFromDisk} and
 * {@link DBCheckpointManager#loadSessionFromDb} to truncate the active
 * restore set ({@code bySession}) so that it starts from the most recent
 * {@link CheckpointType#COMPACTION} checkpoint (inclusive).
 *
 * <p><b>Why truncate</b>: a {@code COMPACTION} checkpoint marks the boundary
 * where the session's message history was replaced by a compacted form.
 * Pre-compaction checkpoints carry {@code messageCount} values that exceed
 * the compacted session's {@code messageCount}, violating the documented
 * invariant {@code checkpoint.messageCount <= session.messageCount}. Keeping
 * them in the active restore set (returned by {@code getCheckpoints} /
 * consumed by restore/audit tools traversing the full history) would break
 * any consumer that validates this invariant.
 *
 * <p><b>Truncation rule</b>: scan the ascending-seq checkpoint list, find
 * the index of the <b>last</b> {@code COMPACTION} checkpoint, and keep the
 * sub-list from that index onward (inclusive — the {@code COMPACTION}
 * checkpoint is the first post-compaction baseline and a legal restore
 * starting point). When no {@code COMPACTION} checkpoint is present, no
 * truncation is performed (full list returned — backward compatible with
 * sessions that never compacted, e.g. {@code NoOpContextCompactor} default).
 *
 * <p><b>Inclusive COMPACTION</b>: the {@code COMPACTION} checkpoint itself
 * is retained because its {@code messageCount} reflects the post-compaction
 * message list and is a valid resume point for
 * {@code buildBaseExecutionContext}. Dropping it would lose the audit record
 * of the compaction event itself.
 *
 * <p><b>Scope</b>: this helper only operates on the in-memory active restore
 * set. The persistent journal.md / DB rows remain the complete audit
 * history and are never modified. The {@code byWatermark} index of
 * {@link FileBackedCheckpointManager} is populated with the full list
 * (pre + post compaction) before truncation so {@code getCheckpoint(old
 * watermark)} still resolves pre-compaction checkpoints for audit/debug.
 * {@link DBCheckpointManager} additionally has a DB direct-query fallback
 * for any watermark not in the warm cache.
 */
public final class CompactionAwareTruncation {

    private CompactionAwareTruncation() {
    }

    /**
     * Return a new list containing the active restore subset of the given
     * ascending-seq checkpoint list. The returned list starts from the most
     * recent {@link CheckpointType#COMPACTION} checkpoint (inclusive). When
     * no {@code COMPACTION} checkpoint is present, a copy of the full input
     * list is returned (no truncation — backward compatible).
     *
     * <p>The input list is never mutated. The returned list is mutable (so
     * the caller can wrap it in {@link java.util.Collections#synchronizedList})
     * and preserves the ascending-seq order of the input.
     *
     * @param checkpoints the full ascending-seq checkpoint list loaded from
     *                    persistent storage; never null
     * @return a new mutable list truncated to the most recent
     *         {@code COMPACTION} checkpoint (inclusive), or a full copy when
     *         no {@code COMPACTION} checkpoint is present
     */
    public static List<Checkpoint> truncateToLatestCompaction(List<Checkpoint> checkpoints) {
        if (checkpoints == null) {
            throw new NopAiAgentException("CompactionAwareTruncation: checkpoints must not be null");
        }

        int lastCompactionIdx = -1;
        for (int i = 0; i < checkpoints.size(); i++) {
            if (checkpoints.get(i).getType() == CheckpointType.COMPACTION) {
                lastCompactionIdx = i;
            }
        }

        if (lastCompactionIdx < 0) {
            // No COMPACTION checkpoint — backward compatible, return full copy.
            return new ArrayList<>(checkpoints);
        }

        // Inclusive: keep the COMPACTION checkpoint itself as the post-compaction
        // baseline plus everything after it.
        return new ArrayList<>(checkpoints.subList(lastCompactionIdx, checkpoints.size()));
    }
}
