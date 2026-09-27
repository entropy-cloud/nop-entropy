/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import java.util.concurrent.ConcurrentLinkedDeque;

import io.nop.stream.runtime.checkpoint.metrics.CheckpointHistoryEntry;

/**
 * Bounded checkpoint observation history, recorded at
 * the same real completion/failure/abort paths that fire job events.
 * Newest first; capacity governed by the governance config (default 100).
 */
class CheckpointHistory {

    private static final int DEFAULT_CHECKPOINT_HISTORY_MAX_ENTRIES = 100;

    private final ConcurrentLinkedDeque<CheckpointHistoryEntry> checkpointHistory =
            new ConcurrentLinkedDeque<>();
    private volatile int checkpointHistoryMaxEntries = DEFAULT_CHECKPOINT_HISTORY_MAX_ENTRIES;

    void recordHistory(CheckpointHistoryEntry entry) {
        checkpointHistory.addFirst(entry);
        int max = checkpointHistoryMaxEntries;
        while (checkpointHistory.size() > max) {
            if (checkpointHistory.pollLast() == null) {
                break;
            }
        }
    }

    /**
     * Snapshot of the bounded checkpoint observation
     * history (newest first). Each entry carries status/duration/size and,
     * for FAILED/ABORTED entries, the failure cause.
     */
    java.util.List<CheckpointHistoryEntry> getCheckpointHistory() {
        return new java.util.ArrayList<>(checkpointHistory);
    }

    void setCheckpointHistoryMaxEntries(int maxEntries) {
        this.checkpointHistoryMaxEntries = Math.max(1, maxEntries);
    }

    int getCheckpointHistoryMaxEntries() {
        return checkpointHistoryMaxEntries;
    }

    /**
     * Drops the {@code count} OLDEST entries from the
     * observation history (governance sweep). Returns the number of entries
     * actually removed.
     */
    int pruneOldestCheckpointHistory(int count) {
        int removed = 0;
        while (removed < count && !checkpointHistory.isEmpty()) {
            if (checkpointHistory.pollLast() == null) {
                break;
            }
            removed++;
        }
        return removed;
    }
}
