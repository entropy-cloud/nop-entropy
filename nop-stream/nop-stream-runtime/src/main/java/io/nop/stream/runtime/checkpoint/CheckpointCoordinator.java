/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.EpochState;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.runtime.checkpoint.metrics.CheckpointMetrics;

@Internal
public class CheckpointCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointCoordinator.class);

    /**
     * Reason a {@link #tryTriggerPendingCheckpoint(CheckpointType)} call did not produce a
     * new {@link PendingCheckpoint}. Exposed via {@link TriggerOutcome} so that the scheduler
     * loop can distinguish back-pressure (throttle / concurrent-limit rejection) from real
     * failures, ensuring {@link #consecutiveTriggerFailures} only inflates on genuine
     * trigger errors (Plan 2026-07-25-2300-1 Phase 1: 「失败计数器不被节流/拒绝污染」).
     */
    public enum TriggerRejectionReason {
        /** A new PendingCheckpoint was created. Only valid when paired with a non-null pending. */
        TRIGGERED,
        /**
         * Rejected because the wall-clock gap since the last checkpoint completion is shorter
         * than {@link CheckpointConfig#getMinPause()} (last-completed semantics). This is
         * expected back-pressure — NOT a failure.
         */
        THROTTLED_MIN_PAUSE,
        /**
         * Rejected because {@code numPendingCheckpoints >= config.getMaxConcurrentCheckpoints()}.
         * Expected back-pressure — NOT a failure.
         */
        REJECTED_MAX_CONCURRENT,
        /**
         * Rejected because there are no registered tasks to acknowledge. A genuine trigger
         * failure (the coordinator is misconfigured or all tasks have unregistered).
         */
        NO_TASKS_TO_ACK
    }

    /**
     * Result of a trigger attempt. {@link #pending()} is non-null iff {@link #reason()} is
     * {@link TriggerRejectionReason#TRIGGERED}.
     */
    public static final class TriggerOutcome {
        private final TriggerRejectionReason reason;
        private final PendingCheckpoint pending;

        private TriggerOutcome(TriggerRejectionReason reason, PendingCheckpoint pending) {
            this.reason = reason;
            this.pending = pending;
        }

        static TriggerOutcome triggered(PendingCheckpoint pending) {
            return new TriggerOutcome(TriggerRejectionReason.TRIGGERED, pending);
        }

        static TriggerOutcome rejected(TriggerRejectionReason reason) {
            return new TriggerOutcome(reason, null);
        }

        public TriggerRejectionReason reason() {
            return reason;
        }

        public PendingCheckpoint pending() {
            return pending;
        }

        /** Convenience: true when this is a back-pressure rejection, not a real failure. */
        public boolean isBackPressure() {
            return reason == TriggerRejectionReason.THROTTLED_MIN_PAUSE
                    || reason == TriggerRejectionReason.REJECTED_MAX_CONCURRENT;
        }
    }

    private final String jobId;
    private final String pipelineId;
    private final CheckpointIDCounter checkpointIdCounter;
    private final ICheckpointStorage checkpointStorage;
    private final CheckpointConfig config;

    private final ConcurrentHashMap<Long, PendingCheckpoint> pendingCheckpoints;
    private final AtomicInteger numPendingCheckpoints;
    private volatile CompletedCheckpoint latestCompletedCheckpoint;
    private volatile Set<TaskLocation> tasksToAcknowledge;

    /**
     * Wall-clock timestamp (ms) at which the most recent checkpoint entered the COMPLETED
     * durable state via {@link #onCompletePersistSuccess}. Used to enforce last-completed
     * {@code minPause} gating in {@link #tryTriggerPendingCheckpoint}: the next trigger is
     * allowed only after {@code now - lastCompletedTimestamp >= config.getMinPause()}.
     *
     * <p>Semantics rationale (Plan 2026-07-25-2300-1 Phase 1): the anchor is the
     * <em>completion</em> instant, not the trigger instant, matching
     * {@code checkpoint-design.md} §配置表「两次 checkpoint 之间的最小间隔」and Flink's
     * {@code minPauseBetweenCheckpoints}. The first trigger after coordinator construction
     * (no prior completion) is never throttled; if a prior checkpoint is still in-flight,
     * {@code maxConcurrentCheckpoints} gating decides — minPause does not duplicate that
     * decision.
     *
     * <p>Volatile because the field is written under the coordinator monitor inside
     * {@code onCompletePersistSuccess} but may be read from {@code tryTriggerPendingCheckpoint}
     * (also under the monitor) and from observers. Writes happen-before the matching monitor
     * exit, so volatile-read here is sufficient for visibility without re-acquiring the lock.
     */
    private volatile long lastCompletedTimestamp = 0L;

    private ScheduledExecutorService scheduler;
    private final ScheduledExecutorService timeoutScheduler;
    private volatile boolean isSchedulerStarted = false;

    /**
     * Dedicated executor for checkpoint persistence (storeCheckPoint + storeEpochManifest).
     * Lazily created on first use (see {@link #getOrCreatePersistExecutor()}) so that
     * coordinator instances which never complete a checkpoint do not spawn extra threads.
     * Lifecycle is decoupled from {@link #startCheckpointScheduler()}/{@link #stopCheckpointScheduler()}
     * (which are restartable) per N2: only the terminal {@link #shutdown()} tears it down,
     * and {@link RejectedExecutionException} submitted after shutdown is handled inline.
     */
    private ExecutorService persistExecutor;
    private final AtomicInteger persistExecutorThreadIndex = new AtomicInteger(0);
    private volatile boolean isShutdown = false;

    private final List<CheckpointListener> listeners = new CopyOnWriteArrayList<>();
    private final List<CheckpointParticipant> participants = new CopyOnWriteArrayList<>();
    private final CheckpointMetrics metrics = new CheckpointMetrics();

    private static final int DEFAULT_COMMIT_RETRIES = 3;
    private static final int CONSECUTIVE_FAILURE_THRESHOLD = 3;
    private final ConcurrentSkipListMap<Long, Set<CheckpointParticipant>> failedCommitParticipants = new ConcurrentSkipListMap<>();
    private final ConcurrentHashMap<Long, Boolean> checkpointSuccessMap = new ConcurrentHashMap<>();

    private final AtomicInteger consecutiveTriggerFailures = new AtomicInteger(0);

    private volatile java.util.function.Consumer<Long> abortHandler;

    public CheckpointCoordinator(
            String jobId,
            String pipelineId,
            CheckpointIDCounter checkpointIdCounter,
            ICheckpointStorage checkpointStorage,
            CheckpointConfig config) {
        this.jobId = jobId;
        this.pipelineId = pipelineId;
        this.checkpointIdCounter = checkpointIdCounter;
        this.checkpointStorage = checkpointStorage;
        this.config = config;
        this.pendingCheckpoints = new ConcurrentHashMap<>();
        this.numPendingCheckpoints = new AtomicInteger(0);
        this.tasksToAcknowledge = ConcurrentHashMap.newKeySet();
        // G31 (Plan 2026-07-25-2300-1): the Coordinator layer honors the configured
        // maxConcurrentCheckpoints value directly (see tryTriggerPendingCheckpoint gating).
        // Task-side multi-epoch barrier tracking (CheckpointBarrierTracker / InputGate
        // simultaneously tracking multiple in-flight checkpoints) is still single-barrier
        // and belongs to Stage 45 — see checkpoint-design.md §2.8 and §13.2 forward-looking
        // invariant. This constructor therefore no longer emits a stale "downgrade to 1"
        // warning that would mislead operators about Coordinator behavior.
        if (config.getMaxConcurrentCheckpoints() > 1) {
            LOG.info("maxConcurrentCheckpoints={} configured for job {}: Coordinator layer honors "
                    + "the configured value; task-side multi-epoch barrier tracking is single-barrier "
                    + "until Stage 45 (see checkpoint-design.md §2.8).",
                    config.getMaxConcurrentCheckpoints(), jobId);
        }
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "checkpoint-timeout-" + jobId);
            t.setDaemon(true);
            return t;
        });
    }

    public void addListener(CheckpointListener listener) {
        listeners.add(listener);
    }

    public void removeListener(CheckpointListener listener) {
        listeners.remove(listener);
    }

    public void addParticipant(CheckpointParticipant participant) {
        participants.add(participant);
    }

    public void removeParticipant(CheckpointParticipant participant) {
        participants.remove(participant);
    }

    public List<CheckpointParticipant> getParticipants() {
        return Collections.unmodifiableList(participants);
    }

    public void setAbortHandler(java.util.function.Consumer<Long> handler) {
        this.abortHandler = handler;
    }

    public synchronized void startCheckpointScheduler() {
        if (isSchedulerStarted) {
            return;
        }

        if (!config.isCheckpointEnabled()) {
            LOG.info("Checkpoint is disabled for job {}", jobId);
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "checkpoint-coordinator-" + jobId);
            t.setDaemon(true);
            return t;
        });

        long interval = config.getCheckpointInterval();
        scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        TriggerOutcome outcome = tryTriggerCheckpointWithReason(CheckpointType.CHECKPOINT);
                        switch (outcome.reason()) {
                            case TRIGGERED:
                                consecutiveTriggerFailures.set(0);
                                break;
                            case THROTTLED_MIN_PAUSE:
                            case REJECTED_MAX_CONCURRENT:
                                // Expected back-pressure: do NOT inflate the failure counter
                                // (Plan 2026-07-25-2300-1 Phase 1: 「失败计数器不被节流/拒绝污染」).
                                // Logging already happened inside tryTriggerCheckpointWithReason
                                // at DEBUG level with the back-pressure reason.
                                break;
                            case NO_TASKS_TO_ACK:
                            default:
                                // Real trigger failure: no tasks to ack means the coordinator
                                // is misconfigured or every task has unregistered.
                                int failures = consecutiveTriggerFailures.incrementAndGet();
                                if (failures == CONSECUTIVE_FAILURE_THRESHOLD) {
                                    LOG.error("Checkpoint trigger failed {} consecutive times for job {} (reason={})",
                                            failures, jobId, outcome.reason());
                                } else {
                                    LOG.warn("Checkpoint trigger rejected for job {} (reason={}, consecutive failures={})",
                                            jobId, outcome.reason(), failures);
                                }
                                break;
                        }
                    } catch (Exception e) {
                        int failures = consecutiveTriggerFailures.incrementAndGet();
                        if (failures >= CONSECUTIVE_FAILURE_THRESHOLD) {
                            LOG.error("Checkpoint trigger failed {} consecutive times for job {}",
                                    failures, jobId, e);
                        } else {
                            LOG.warn("Failed to trigger checkpoint for job {} (attempt {})", jobId, failures, e);
                        }
                    }
                },
                interval,
                interval,
                TimeUnit.MILLISECONDS);

        isSchedulerStarted = true;
        LOG.info("Checkpoint scheduler started for job {} with interval {}ms", jobId, interval);
    }

    public void stopCheckpointScheduler() {
        if (!isSchedulerStarted || scheduler == null) {
            return;
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        isSchedulerStarted = false;
        LOG.info("Checkpoint scheduler stopped for job {}", jobId);
    }

    public synchronized PendingCheckpoint tryTriggerPendingCheckpoint(CheckpointType checkpointType) {
        return tryTriggerCheckpointWithReason(checkpointType).pending();
    }

    /**
     * Trigger a new pending checkpoint and report the precise outcome. The scheduler loop
     * uses this method so that {@code THROTTLED_MIN_PAUSE} and {@code REJECTED_MAX_CONCURRENT}
     * back-pressure does NOT inflate {@link #consecutiveTriggerFailures} (Plan 2026-07-25-2300-1
     * Phase 1). Callers that only need the PendingCheckpoint reference can use
     * {@link #tryTriggerPendingCheckpoint(CheckpointType)} instead.
     *
     * <p>Gating order: (1) maxConcurrent → (2) minPause (last-completed) → (3) tasks-to-ack.
     * Each rejection is observable (DEBUG/WARN log + distinct {@link TriggerRejectionReason})
     * so callers can distinguish back-pressure from genuine failures — there is no silent
     * {@code continue} / null-as-default (Plan rule #24).
     */
    public synchronized TriggerOutcome tryTriggerCheckpointWithReason(CheckpointType checkpointType) {
        int effectiveMaxConcurrent = config.getMaxConcurrentCheckpoints();
        if (numPendingCheckpoints.get() >= effectiveMaxConcurrent) {
            LOG.debug("Cannot trigger checkpoint: too many pending checkpoints ({}/{})",
                    numPendingCheckpoints.get(), effectiveMaxConcurrent);
            return TriggerOutcome.rejected(TriggerRejectionReason.REJECTED_MAX_CONCURRENT);
        }

        // G31 / minPause(last-completed): once at least one checkpoint has completed, the
        // next trigger is allowed only after `now - lastCompletedTimestamp >= minPause`.
        // First-ever trigger (no prior completion) is never throttled. minPause == 0 disables
        // the gate. The anchor is completion (not trigger) to match checkpoint-design.md
        // §配置表 and Flink minPauseBetweenCheckpoints.
        //
        // Scope: only the regular periodic CHECKPOINT type is throttled. Savepoints and
        // terminal checkpoints (COMPLETED_POINT_TYPE / TERMINAL_SAVEPOINT / EXPORTED_SAVEPOINT)
        // are explicit user- or job-driven actions and must not be delayed by minPause —
        // matching Flink semantics where minPauseBetweenCheckpoints applies only to periodic
        // checkpoints, not savepoints. The maxConcurrent gate above continues to apply to all
        // types so that terminal / savepoint snapshots still serialize against in-flight work.
        long minPause = config.getMinPause();
        long lastCompletedAt = lastCompletedTimestamp;
        if (minPause > 0 && lastCompletedAt > 0 && checkpointType == CheckpointType.CHECKPOINT) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastCompletedAt;
            if (elapsed < minPause) {
                LOG.debug("Cannot trigger checkpoint: minPause throttle (elapsed={}ms < minPause={}ms since last completion {})",
                        elapsed, minPause, lastCompletedAt);
                return TriggerOutcome.rejected(TriggerRejectionReason.THROTTLED_MIN_PAUSE);
            }
        }

        long checkpointId = checkpointIdCounter.getAndIncrement();
        long timestamp = System.currentTimeMillis();

        Set<TaskLocation> tasksToAck = getTasksToAcknowledge();
        if (tasksToAck.isEmpty()) {
            LOG.debug("No tasks to acknowledge for checkpoint {}", checkpointId);
            return TriggerOutcome.rejected(TriggerRejectionReason.NO_TASKS_TO_ACK);
        }

        PendingCheckpoint pending = new PendingCheckpoint(
                jobId, pipelineId, checkpointId, timestamp,
                checkpointType, tasksToAck);

        pendingCheckpoints.put(checkpointId, pending);
        numPendingCheckpoints.incrementAndGet();

        scheduleTimeout(pending);

        LOG.info("Triggered checkpoint {} for job {}", checkpointId, jobId);
        return TriggerOutcome.triggered(pending);
    }

    public synchronized boolean acknowledgeTask(TaskLocation taskLocation, long checkpointId, TaskStateSnapshot state) {
        PendingCheckpoint pending = pendingCheckpoints.get(checkpointId);
        if (pending == null) {
            LOG.warn("Received ACK for unknown checkpoint {} from task {}", checkpointId, taskLocation);
            return false;
        }

        // N1: status guard. In async-snapshot mode there is a window between
        // CAS(RUNNING->COMPLETED) (段1) and pendingCheckpoints.remove (段3a/3b) during
        // which the pending entry is still registered with status COMPLETED. A duplicate
        // or stale ACK arriving in that window must return false (matching the sync-mode
        // semantics where a duplicate ACK finds the entry already removed) instead of
        // throwing from PendingCheckpoint.acknowledgeTask.
        if (pending.getStatus().get() != PendingCheckpoint.Status.RUNNING) {
            LOG.debug("Received ACK for checkpoint {} in non-RUNNING state {}, ignoring",
                    checkpointId, pending.getStatus().get());
            return false;
        }

        pending.acknowledgeTask(taskLocation, state);
        LOG.debug("Task {} acknowledged checkpoint {}, pending tasks: {}",
                taskLocation, checkpointId, pending.getNumberOfNotAcknowledgedTasks());

        if (pending.isFullyAcknowledged()) {
            completePendingCheckpoint(pending.toCompletedCheckpoint());
        }

        return true;
    }

    public synchronized void completePendingCheckpoint(CompletedCheckpoint completed) {
        long checkpointId = completed.getCheckpointId();
        PendingCheckpoint pending = pendingCheckpoints.get(checkpointId);
        if (pending == null) {
            LOG.debug("Skip completing checkpoint {} because it is no longer pending", checkpointId);
            return;
        }

        if (!pending.getStatus().compareAndSet(PendingCheckpoint.Status.RUNNING, PendingCheckpoint.Status.COMPLETED)) {
            LOG.debug("Skip completing checkpoint {} because status is {}", checkpointId, pending.getStatus().get());
            return;
        }

        // 段1: build immutable manifest snapshot while holding monitor. buildEpochManifest
        // captures currentFingerprint; building it here (under monitor) preserves the same
        // fingerprint-observation ordering as the pre-async implementation. The manifest and
        // the completed checkpoint are immutable, so they can be safely handed to the persist
        // executor without holding the monitor during I/O.
        final EpochManifest manifest = buildEpochManifest(completed);

        if (!config.isAsyncSnapshotEnabled()) {
            // sync fallback: 段2 (storage I/O) + 段3 (completion side effects) execute inline
            // on the ACK caller thread under the monitor — pre-async behavior preserved.
            completePersistSynchronously(completed, pending, manifest);
            return;
        }

        // async path: hand 段2 + 段3 to the dedicated persist executor. The ACK caller thread
        // returns as soon as the task is queued, so storage I/O no longer blocks the
        // coordinator's responsiveness (abort registration, timeout scheduling, trigger
        // bookkeeping).
        ExecutorService executor = getOrCreatePersistExecutor();
        try {
            executor.submit(() -> executePersistAsync(completed, pending, manifest));
        } catch (RejectedExecutionException ree) {
            // Executor was shut down (terminal shutdown racing with a final ACK). Execute
            // 段3b inline — we still hold the monitor from this synchronized method.
            LOG.warn("Persist executor rejected checkpoint {}, failing inline", checkpointId, ree);
            onCompletePersistFailure(completed, pending, "submit persist task", ree);
        }
    }

    /**
     * Sync-fallback path: runs entirely on the ACK caller thread under the coordinator
     * monitor. Equivalent to the pre-async implementation of {@code completePendingCheckpoint}.
     */
    private synchronized void completePersistSynchronously(
            CompletedCheckpoint completed, PendingCheckpoint pending, EpochManifest manifest) {
        long checkpointId = completed.getCheckpointId();

        try {
            checkpointStorage.storeCheckPoint(completed);
        } catch (Exception e) {
            onCompletePersistFailure(completed, pending, "Failed to store checkpoint", e);
            return;
        }

        try {
            checkpointStorage.storeEpochManifest(jobId, pipelineId, manifest);
            LOG.debug("Stored EpochManifest for epoch {}", checkpointId);
        } catch (Exception e) {
            onCompletePersistFailure(completed, pending,
                    "Failed to store EpochManifest for checkpoint " + checkpointId, e);
            return;
        }

        onCompletePersistSuccess(completed, pending);
    }

    /**
     * Async path body, executed on a {@code checkpoint-persist-<jobId>-<n>} thread.
     * 段2 performs storage I/O WITHOUT holding the monitor (operating on immutable snapshot
     * data). 段3a/3b re-acquire the monitor so that decrementPendingCheckpointCount stays
     * atomic with tryTriggerPendingCheckpoint's concurrent-check (§13.2).
     */
    private void executePersistAsync(
            CompletedCheckpoint completed, PendingCheckpoint pending, EpochManifest manifest) {
        long checkpointId = completed.getCheckpointId();

        try {
            checkpointStorage.storeCheckPoint(completed);
        } catch (Exception e) {
            synchronized (this) {
                onCompletePersistFailure(completed, pending, "Failed to store checkpoint", e);
            }
            return;
        }

        try {
            checkpointStorage.storeEpochManifest(jobId, pipelineId, manifest);
            LOG.debug("Stored EpochManifest for epoch {}", checkpointId);
        } catch (Exception e) {
            synchronized (this) {
                onCompletePersistFailure(completed, pending,
                        "Failed to store EpochManifest for checkpoint " + checkpointId, e);
            }
            return;
        }

        synchronized (this) {
            onCompletePersistSuccess(completed, pending);
        }
    }

    /**
     * 段3a: success callback. Caller MUST hold the coordinator monitor.
     *
     * <p>Preserves the exact side-effect ordering of the pre-async implementation:
     * pendingCheckpoints.remove → forceComplete (DURABLE) → latestCompletedCheckpoint →
     * decrementPendingCheckpointCount → metrics → cleanup → retryFailedCommits →
     * notifyParticipantsFinishCommit(true) (commit, after forceComplete per §12 invariant 5)
     * → notifyCheckpointCompleted → checkpointSuccessMap.remove → consecutiveTriggerFailures.reset.
     */
    private void onCompletePersistSuccess(CompletedCheckpoint completed, PendingCheckpoint pending) {
        long checkpointId = completed.getCheckpointId();

        if (!pendingCheckpoints.remove(checkpointId, pending)) {
            LOG.debug("Skip completing checkpoint {} because pending state changed", checkpointId);
            return;
        }

        // AR-19: Complete the future only after successful storage, so storage failure
        // does not leave a ghost checkpoint that callers already acted on.
        pending.forceComplete();

        latestCompletedCheckpoint = completed;
        // G31 / minPause(last-completed): anchor the next-trigger throttle clock at the
        // instant this checkpoint became durable. Set before decrement so a racing trigger
        // (also under monitor) sees the new anchor when numPending drops to 0.
        lastCompletedTimestamp = System.currentTimeMillis();
        decrementPendingCheckpointCount();

        metrics.incrementCompletedCheckpoints();
        metrics.updateLatestCheckpoint(completed.estimateSize(), completed.getDuration());

        cleanupOldCheckpoints();

        // Retry previously failed commits before processing current epoch
        retryFailedCommits();

        // Notify participants first: finishCommit in reverse topology order.
        // §12 invariant 5: commit happens only after manifest is durable (forceComplete above).
        notifyParticipantsFinishCommit(checkpointId, true);

        notifyCheckpointCompleted(checkpointId);

        checkpointSuccessMap.remove(checkpointId);

        consecutiveTriggerFailures.set(0);

        LOG.info("Completed checkpoint {} for job {}, duration: {}ms",
                checkpointId, jobId, completed.getDuration());
    }

    /**
     * 段3b: failure callback. Caller MUST hold the coordinator monitor.
     *
     * <p>The pending checkpoint status is forced to FAILED (NOT aborted via
     * {@link #abortPendingCheckpoint}, whose RUNNING→ABORTED CAS would fail because 段1
     * already transitioned to COMPLETED). finishCommit(false) keeps prepared sink
     * transactions for subsuming, matching the pre-async failure semantics.
     */
    private void onCompletePersistFailure(CompletedCheckpoint completed, PendingCheckpoint pending,
                                          String failMessage, Exception cause) {
        long checkpointId = completed.getCheckpointId();
        LOG.error("Failed checkpoint {} for job {}: {}", checkpointId, jobId, failMessage, cause);
        metrics.recordFailure(failMessage);
        pending.getStatus().set(PendingCheckpoint.Status.FAILED);
        pendingCheckpoints.remove(checkpointId, pending);
        decrementPendingCheckpointCount();
        notifyParticipantsFinishCommit(checkpointId, false);
        notifyCheckpointAborted(checkpointId);
        LOG.warn("Failed checkpoint {} for job {}: {}", checkpointId, jobId, failMessage, cause);
    }

    private ExecutorService getOrCreatePersistExecutor() {
        if (persistExecutor == null) {
            persistExecutor = createPersistExecutor();
        }
        return persistExecutor;
    }

    private ExecutorService createPersistExecutor() {
        int poolSize = Math.max(1, config.getAsyncSnapshotThreadPoolSize());
        return Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "checkpoint-persist-" + jobId + "-" + persistExecutorThreadIndex.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void abortPendingCheckpoint(PendingCheckpoint pending, String reason) {
        long checkpointId = pending.getCheckpointId();

        if (!pending.getStatus().compareAndSet(PendingCheckpoint.Status.RUNNING, PendingCheckpoint.Status.ABORTED)) {
            LOG.debug("Skip aborting checkpoint {} because status is {}", checkpointId, pending.getStatus().get());
            return;
        }

        PendingCheckpoint removed = pendingCheckpoints.remove(checkpointId);
        if (removed == null) {
            LOG.debug("Skip aborting checkpoint {} because it is no longer pending", checkpointId);
            return;
        }

        removed.abort(reason);
        decrementPendingCheckpointCount();

        metrics.recordAborted("Aborted: " + reason);

        // Notify participants about abort: finishCommit(false) keeps prepared transactions for subsuming
        notifyParticipantsFinishCommit(checkpointId, false);

        notifyCheckpointAborted(checkpointId);

        java.util.function.Consumer<Long> handler = this.abortHandler;
        if (handler != null) {
            try {
                handler.accept(checkpointId);
            } catch (Exception e) {
                LOG.error("Abort handler failed for checkpoint {}", checkpointId, e);
            }
        }

        LOG.warn("Aborted checkpoint {} for job {}: {}", checkpointId, jobId, reason);
    }

    public CompletedCheckpoint restoreFromCheckpoint() throws Exception {
        CompletedCheckpoint checkpoint = checkpointStorage.getLatestCheckpoint(jobId, pipelineId);
        if (checkpoint != null) {
            checkpoint.setRestored(true);
            latestCompletedCheckpoint = checkpoint;
            LOG.info("Restored checkpoint {} for job {}", checkpoint.getCheckpointId(), jobId);
        }
        return checkpoint;
    }

    public CompletedCheckpoint getLatestCheckpoint() {
        return latestCompletedCheckpoint;
    }

    public PendingCheckpoint getPendingCheckpoint(long checkpointId) {
        return pendingCheckpoints.get(checkpointId);
    }

    /**
     * Reports a task-level snapshot failure for the given {@code checkpointId} and
     * routes it to {@link #abortPendingCheckpoint(PendingCheckpoint, String)} so that
     * the matching {@link PendingCheckpoint} is aborted (rather than being silently
     * marked complete by a later successful ACK).
     *
     * <p>P1-11 closure: prior to this entry the {@link io.nop.stream.core.execution.CheckpointBarrierTracker}
     * had only a success channel and silently treated failed snapshots as successful
     * ACKs, which corrupted checkpoint state. {@code GraphModelCheckpointExecutor}
     * wires the tracker's abort callback to this method.
     *
     * @param taskLocation the task that reported the snapshot failure (for diagnostics)
     * @param checkpointId the checkpoint whose operator snapshot failed
     * @param error        the snapshot error
     */
    public void reportTaskCheckpointFailure(TaskLocation taskLocation, long checkpointId, Exception error) {
        PendingCheckpoint pending = getPendingCheckpoint(checkpointId);
        if (pending == null) {
            LOG.debug("reportTaskCheckpointFailure: no pending checkpoint {} (already completed/aborted?) for task {}",
                    checkpointId, taskLocation);
            return;
        }
        String reason = "Operator snapshot failure reported by task " + taskLocation
                + ": " + (error == null ? "(no cause)" : error.getMessage());
        LOG.error("Aborting checkpoint {} due to task snapshot failure from {}",
                checkpointId, taskLocation, error);
        abortPendingCheckpoint(pending, reason);
    }

    public int getNumberOfPendingCheckpoints() {
        return numPendingCheckpoints.get();
    }

    public void incrementTriggerFailures() {
        int failures = consecutiveTriggerFailures.incrementAndGet();
        int threshold = config.getMaxConsecutiveCheckpointFailures();
        if (failures >= threshold) {
            LOG.error("Checkpoint trigger failed {} consecutive times for job {} (threshold={})",
                    failures, jobId, threshold);
        } else {
            LOG.warn("Checkpoint trigger failed for job {} (consecutive failures={})", jobId, failures);
        }
    }

    public int getConsecutiveTriggerFailures() {
        return consecutiveTriggerFailures.get();
    }

    public CheckpointMetrics getMetrics() {
        return metrics;
    }

    protected Set<TaskLocation> getTasksToAcknowledge() {
        return new HashSet<>(tasksToAcknowledge);
    }

    public void setTasksToAcknowledge(Collection<TaskLocation> taskLocations) {
        Set<TaskLocation> newSet = ConcurrentHashMap.newKeySet();
        if (taskLocations != null) {
            for (TaskLocation loc : taskLocations) {
                if (loc != null) {
                    newSet.add(loc);
                }
            }
        }
        this.tasksToAcknowledge = newSet;
    }

    public synchronized void registerTask(TaskLocation taskLocation) {
        this.tasksToAcknowledge.add(taskLocation);
    }

    public synchronized void unregisterTask(TaskLocation taskLocation) {
        this.tasksToAcknowledge.remove(taskLocation);
    }

    private void scheduleTimeout(PendingCheckpoint pending) {
        if (timeoutScheduler.isShutdown()) {
            return;
        }

        timeoutScheduler.schedule(() -> {
            if (!pending.getCompletableFuture().isDone()) {
                abortPendingCheckpoint(pending, "Timeout");
            }
        }, config.getCheckpointTimeout(), TimeUnit.MILLISECONDS);
    }

    private void decrementPendingCheckpointCount() {
        numPendingCheckpoints.updateAndGet(count -> count > 0 ? count - 1 : 0);
    }

    private void cleanupOldCheckpoints() {
        int maxRetained = config.getMaxRetainedCheckpoints();
        try {
            List<CompletedCheckpoint> allCheckpoints = checkpointStorage.getAllCheckpoints(jobId);
            if (allCheckpoints.size() > maxRetained) {
                for (int i = maxRetained; i < allCheckpoints.size(); i++) {
                    CompletedCheckpoint old = allCheckpoints.get(i);
                    checkpointStorage.deleteCheckpoint(jobId, old.getPipelineId(), old.getCheckpointId());
                    LOG.debug("Deleted old checkpoint {}", old.getCheckpointId());
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to cleanup old checkpoints", e);
        }
    }

    private void notifyCheckpointCompleted(long checkpointId) {
        for (CheckpointListener listener : listeners) {
            try {
                listener.notifyCheckpointComplete(checkpointId);
            } catch (Exception e) {
                LOG.error("Failed to notify checkpoint completion to listener", e);
            }
        }
    }

    private void notifyCheckpointAborted(long checkpointId) {
        for (CheckpointListener listener : listeners) {
            try {
                listener.notifyCheckpointAborted(checkpointId);
            } catch (Exception e) {
                LOG.error("Failed to notify checkpoint abortion to listener", e);
            }
        }
    }

    private void notifyParticipantsFinishCommit(long checkpointId, boolean success) {
        checkpointSuccessMap.put(checkpointId, success);
        for (int i = participants.size() - 1; i >= 0; i--) {
            CheckpointParticipant participant = participants.get(i);
            try {
                participant.finishCommit(checkpointId, success);
            } catch (Exception e) {
                LOG.error("finishCommit({}) failed for participant {} on checkpoint {}, deferring to retry cycle",
                        success, i, checkpointId, e);
                failedCommitParticipants.computeIfAbsent(checkpointId, k -> ConcurrentHashMap.newKeySet()).add(participant);
            }
        }
    }

    private void retryFailedCommits() {
        if (failedCommitParticipants.isEmpty()) return;

        Iterator<Map.Entry<Long, Set<CheckpointParticipant>>> it = failedCommitParticipants.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Set<CheckpointParticipant>> entry = it.next();
            long failedEpoch = entry.getKey();
            Set<CheckpointParticipant> failedParts = entry.getValue();
            Set<CheckpointParticipant> stillFailing = ConcurrentHashMap.newKeySet();
            boolean originalSuccess = checkpointSuccessMap.getOrDefault(failedEpoch, true);

            for (CheckpointParticipant participant : failedParts) {
                if (participants.contains(participant)) {
                    try {
                        participant.finishCommit(failedEpoch, originalSuccess);
                        LOG.info("Retried finishCommit for participant on epoch {} with success={} succeeded", failedEpoch, originalSuccess);
                    } catch (Exception e) {
                        LOG.warn("Retry finishCommit for participant on epoch {} still failing", failedEpoch, e);
                        stillFailing.add(participant);
                    }
                }
            }

            if (stillFailing.isEmpty()) {
                it.remove();
                checkpointSuccessMap.remove(failedEpoch);
            } else {
                it.remove();
                failedCommitParticipants.put(failedEpoch, stillFailing);
            }
        }
    }

    public void shutdown() {
        isShutdown = true;
        stopCheckpointScheduler();

        // N2/N3: persist executor lifecycle is tied to terminal shutdown() only, not to the
        // restartable stopCheckpointScheduler(). awaitTermination mirrors trigger scheduler
        // discipline so in-flight persist tasks (段2 storage writes) get a brief grace window
        // to finish before shutdownNow interrupts them.
        ExecutorService pe = persistExecutor;
        if (pe != null) {
            pe.shutdown();
            try {
                if (!pe.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                    pe.shutdownNow();
                }
            } catch (InterruptedException e) {
                pe.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        timeoutScheduler.shutdownNow();
        try {
            if (!timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("Timeout scheduler did not terminate within 5 seconds for job {}", jobId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (PendingCheckpoint pending : pendingCheckpoints.values()) {
            long checkpointId = pending.getCheckpointId();
            notifyParticipantsFinishCommit(checkpointId, false);
            notifyCheckpointAborted(checkpointId);
            pending.dispose();
        }
        pendingCheckpoints.clear();
        numPendingCheckpoints.set(0);
        listeners.clear();
        participants.clear();
        failedCommitParticipants.clear();
        checkpointSuccessMap.clear();
        abortHandler = null;

        LOG.info("Checkpoint coordinator shutdown for job {}", jobId);
    }

    /**
     * Build an EpochManifest from a CompletedCheckpoint.
     */
    private EpochManifest buildEpochManifest(CompletedCheckpoint completed) {
        return new EpochManifest(
                completed.getCheckpointId(),
                completed.getJobId(),
                completed.getPipelineId(),
                completed.getCompletedTimestamp(),
                completed.getCheckpointType(),
                EpochState.COMMITTED,
                completed.getTaskStates(),
                currentFingerprint,
                null  // segments - will be populated when segment-based storage is integrated
        );
    }

    /**
     * Try to restore from EpochManifest first, fall back to CompletedCheckpoint.
     */
    public EpochManifest restoreLatestEpochManifest() throws Exception {
        return checkpointStorage.loadLatestEpochManifest(jobId, pipelineId);
    }

    // --- Fingerprint management ---

    private volatile StreamModelFingerprint currentFingerprint;

    public void setCurrentFingerprint(StreamModelFingerprint fingerprint) {
        this.currentFingerprint = fingerprint;
    }

    public StreamModelFingerprint getCurrentFingerprint() {
        return currentFingerprint;
    }
}