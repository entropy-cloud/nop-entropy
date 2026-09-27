/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.StateSegmentDescriptor;
import io.nop.stream.core.checkpoint.incremental.SharedStateHandle;
import io.nop.stream.core.checkpoint.incremental.SharedStateRegistry;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.checkpoint.storage.ISegmentStore;

/**
 * Retention collaborator of {@link CheckpointCoordinator}: enforces
 * {@code maxRetainedCheckpoints} on both durable planes (checkpoint rows +
 * epoch manifests) and releases subsumed incremental segments.
 *
 * <p>Owns the serialization / trailing re-run state
 * ({@code retentionInProgress} / {@code retentionRecheckNeeded}); the retention
 * executor itself stays on the host (created lazily via the host, torn down by
 * the host's terminal {@code shutdown()}).
 *
 * <p>Shares host state via constructor-injected references
 * ({@code config}/{@code checkpointStorage}/{@code job}/{@code pipeline}/{@code checkpointSegments}).
 * {@code segmentStore} and {@code sharedStateRegistry} are deliberately read
 * LIVE from the host instead of injected: both are wired after coordinator
 * construction ({@code setSegmentStore} / {@code setIncrementalCheckpointEnabled}),
 * so a constructor-time snapshot would permanently capture {@code null} and
 * silently disable segment GC for incremental jobs.
 */
class RetentionCleaner {

    // Logs under the CheckpointCoordinator logger name — the coordinator's
    // retention diagnostics keep their original logger.
    private static final Logger LOG = LoggerFactory.getLogger(CheckpointCoordinator.class);

    private final CheckpointCoordinator host;
    private final CheckpointConfig config;
    private final ICheckpointStorage checkpointStorage;
    private final String jobId;
    private final String pipelineId;
    private final Map<Long, List<StateSegmentDescriptor>> checkpointSegments;

    /**
     * Serialization guard: true while a retention task is running
     * (or queued). Ensures at most one retention run executes / is queued at a time
     * — concurrent runs would race on duplicate {@code deleteCheckpoint} calls for
     * the same rows when the persist pool has multiple threads.
     */
    private final java.util.concurrent.atomic.AtomicBoolean retentionInProgress =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * Trailing re-run flag: set when a completion triggers retention
     * while a run is already in flight. The running task's finally consumes the flag
     * and schedules one more run, guaranteeing every completion is followed by a
     * retention run that starts later (eventual consistency of the ≤ maxRetained
     * invariant — the LAST completion's retention is never lost to coalescing).
     */
    private final java.util.concurrent.atomic.AtomicBoolean retentionRecheckNeeded =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    RetentionCleaner(CheckpointCoordinator host,
                     CheckpointConfig config,
                     ICheckpointStorage checkpointStorage,
                     String jobId,
                     String pipelineId,
                     Map<Long, List<StateSegmentDescriptor>> checkpointSegments) {
        this.host = host;
        this.config = config;
        this.checkpointStorage = checkpointStorage;
        this.jobId = jobId;
        this.pipelineId = pipelineId;
        this.checkpointSegments = checkpointSegments;
    }

    /**
     * Schedule the retention storage I/O
     * ({@link #cleanupOldCheckpoints()}) onto the dedicated retention executor so it
     * no longer executes synchronously inside a coordinator-monitor-holding stack.
     * MUST be called while holding the coordinator monitor (all call sites are inside
     * success callback) — the method itself performs no I/O and never blocks: an atomic CAS guard
     * plus an {@code ExecutorService.submit}.
     *
     * <p>Serialization + trailing re-run (D1(f)): while a retention run is in flight
     * ({@link #retentionInProgress}), further triggers only set
     * {@link #retentionRecheckNeeded}; the running task's finally block consumes the
     * flag and schedules exactly one more run. This guarantees (i) at most one
     * retention run executes at any time (no duplicate {@code deleteCheckpoint}
     * races), and (ii) every checkpoint completion is followed by a retention run
     * that started LATER than that completion's trigger (the last completion's
     * retention is never lost to coalescing — eventual consistency of the
     * {@code ≤ maxRetained} invariant).
     */
    void scheduleRetentionCleanup() {
        if (!retentionInProgress.compareAndSet(false, true)) {
            // Coalesce: a run is already in flight; its finally will re-run for us.
            retentionRecheckNeeded.set(true);
            return;
        }
        ExecutorService executor = host.getOrCreateRetentionExecutor();
        try {
            executor.submit(() -> {
                try {
                    cleanupOldCheckpoints();
                } finally {
                    retentionInProgress.set(false);
                    if (retentionRecheckNeeded.getAndSet(false)) {
                        // A completion arrived while this run was executing (or queued):
                        // schedule the trailing run that covers it. Recursive call is
                        // safe — the flag was just cleared, so the CAS succeeds (or a
                        // racing direct trigger submitted first, in which case we only
                        // re-arm the recheck flag it will observe).
                        scheduleRetentionCleanup();
                    }
                }
            });
        } catch (RejectedExecutionException ree) {
            // Terminal-shutdown race: the cleanup is dropped with an explicit WARN
            // (never silently swallowed). Storage rows beyond maxRetained are
            // harmless leftovers — the next coordinator instance's first completion
            // triggers retention again and converges (retention is idempotent).
            retentionInProgress.set(false);
            LOG.warn("Retention cleanup rejected for job {} (coordinator shutting down?)", jobId, ree);
        }
    }

    /**
     * Retention cleanup: enforce {@code maxRetainedCheckpoints} by deleting the OLDEST
     * durable checkpoint rows (storage returns rows sorted by checkpointId DESCENDING;
     * indices >= maxRetained are the oldest) and releasing their incremental segments.
     *
     * <p>Dual-plane retention: the SAME round also prunes the epoch-manifest
     * plane ({@code pruneEpochManifests}, keep-newest-N per (jobId, pipelineId),
     * after the checkpoint-plane deletion) so long-running jobs do not
     * accumulate manifest files/rows without bound.
     *
     * <p>Execution context: on the async completion paths this runs on
     * the dedicated {@code checkpoint-retention-<jobId>} thread WITHOUT the coordinator
     * monitor; on the sync-fallback path ({@code asyncSnapshotEnabled=false}) it runs
     * inline on the ACK caller thread under the monitor (pinned pre-async semantics,
     * D1(d)). Thread-safety without the monitor rests on established invariants:
     * storage-internal locking, {@code checkpointSegments} being a
     * ConcurrentHashMap, and {@code SharedStateRegistryImpl} being per-key atomic —
     * the same guarantees the incremental path already relies on when calling
     * registry register/unregister from the persist-executor thread.
     *
     * <p>Failures are caught and logged at WARN (never silently swallowed); retention
     * re-triggers naturally on every subsequent checkpoint completion, so transient
     * failures self-heal (D1(c)).
     */
    void cleanupOldCheckpoints() {
        int maxRetained = config.getMaxRetainedCheckpoints();
        try {
            List<CompletedCheckpoint> allCheckpoints = checkpointStorage.getAllCheckpoints(jobId);
            if (allCheckpoints.size() > maxRetained) {
                for (int i = maxRetained; i < allCheckpoints.size(); i++) {
                    CompletedCheckpoint old = allCheckpoints.get(i);
                    checkpointStorage.deleteCheckpoint(jobId, old.getPipelineId(), old.getCheckpointId());
                    LOG.debug("Deleted old checkpoint {}", old.getCheckpointId());
                    // Subsumption GC — release this checkpoint's segments from the
                    // shared-state registry and physically discard any that drop to zero refs.
                    gcSegmentsForCheckpoint(old.getCheckpointId());
                }
            }
            // Manifest retention: prune the manifest plane in the SAME
            // retention round, AFTER the checkpoint-plane deletion — unconditionally:
            // the manifest plane may exceed the bound independently (e.g. pre-fix
            // leftover manifests while the checkpoint plane already converged).
            pruneEpochManifestsForObservedPipelines(allCheckpoints, maxRetained);
        } catch (Exception e) {
            LOG.warn("Failed to cleanup old checkpoints", e);
        }
    }

    /**
     * Manifest retention (checkpoint-design §9.2 D2b/D4): prune the
     * epoch-manifest plane for every pipeline observed in the SAME
     * {@code getAllCheckpoints} read (unioned with this coordinator's own
     * pipelineId), keeping the newest {@code maxRetained} manifests per
     * {@code (jobId, pipelineId)}. Enumeration basis matches the checkpoint-plane
     * deletion (which deletes by {@code old.getPipelineId()} from the same read),
     * honoring the per-(jobId, pipelineId) bound for every pipeline the job
     * actually persists.
     *
     * <p>Per-pipeline failures are WARN-contained (never propagated out of the
     * retention round) and self-heal on the next completion — the same D1(c)
     * semantics as the checkpoint-plane deletion. Runs on the retention executor
     * thread (async paths, no monitor) or inline on the ACK caller thread
     * (sync-fallback, D1(d)) — never inside the success callback's monitor-holding stack.
     */
    private void pruneEpochManifestsForObservedPipelines(List<CompletedCheckpoint> allCheckpoints, int maxRetained) {
        Set<String> pipelineIds = new HashSet<>();
        pipelineIds.add(pipelineId);
        for (CompletedCheckpoint cp : allCheckpoints) {
            if (cp.getPipelineId() != null) {
                pipelineIds.add(cp.getPipelineId());
            }
        }
        for (String pid : pipelineIds) {
            try {
                List<Long> pruned = checkpointStorage.pruneEpochManifests(jobId, pid, maxRetained);
                if (!pruned.isEmpty()) {
                    LOG.debug("Pruned {} old epoch manifests for job {}/{} (retained bound {})",
                            pruned.size(), jobId, pid, maxRetained);
                }
            } catch (Exception e) {
                LOG.warn("Failed to prune epoch manifests for job {}/{}", jobId, pid, e);
            }
        }
    }

    /**
     * Subsumption GC for one checkpoint: unregister each of its segments from
     * the shared state registry (in-memory, fast) and off-load the discard of any
     * zero-reference handles to the persist executor so monitor throughput is not impacted
     * by segment-store file deletion. Per Design Decision: registry owns ref-count,
     * {@code ISegmentStore} owns file deletion.
     *
     * <p>On the async paths this is invoked from the retention-executor
     * thread WITHOUT the coordinator monitor — thread safety rests on
     * {@code checkpointSegments} (ConcurrentHashMap), the per-key atomic
     * {@code SharedStateRegistryImpl}, and the same lock-free registry usage the
     * incremental persist path already performs. The lazy persist-executor
     * access is safe on that thread: retention tasks are only scheduled from the success callback of the
     * async/incremental paths, which always created the persist executor (under monitor,
     * in {@code completePendingCheckpoint}) before the storage-I/O step, and {@code submit} of the retention
     * task establishes the happens-before edge that publishes the non-null field.
     */
    private void gcSegmentsForCheckpoint(long checkpointId) {
        if (host.sharedStateRegistry == null || host.segmentStore == null) {
            return;
        }
        List<StateSegmentDescriptor> segs = checkpointSegments.remove(checkpointId);
        if (segs == null || segs.isEmpty()) {
            return;
        }
        List<SharedStateHandle> toDiscard = new java.util.ArrayList<>();
        for (StateSegmentDescriptor seg : segs) {
            toDiscard.addAll(host.sharedStateRegistry.unregister(seg.getPath()));
        }
        if (toDiscard.isEmpty()) {
            return;
        }
        ExecutorService exec = host.getOrCreatePersistExecutor();
        for (SharedStateHandle handle : toDiscard) {
            final String hash = handle.getStateObjectId();
            try {
                exec.submit(() -> {
                    try {
                        host.segmentStore.discardSegment(hash);
                    } catch (Exception dex) {
                        LOG.warn("Failed to discard segment {} for job {}", hash, jobId, dex);
                    }
                    return null;
                });
            } catch (RejectedExecutionException ree) {
                // Shutdown race: discard inline best-effort rather than leaking the file.
                try {
                    host.segmentStore.discardSegment(hash);
                } catch (Exception dex) {
                    LOG.warn("Inline discard of segment {} failed for job {}", hash, jobId, dex);
                }
            }
        }
    }
}
