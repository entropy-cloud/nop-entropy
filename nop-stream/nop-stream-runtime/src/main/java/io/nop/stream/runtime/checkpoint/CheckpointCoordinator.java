/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.runtime.checkpoint.metrics.CheckpointMetrics;

@Internal
public class CheckpointCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointCoordinator.class);

    private final String jobId;
    private final String pipelineId;
    private final CheckpointIDCounter checkpointIdCounter;
    private final ICheckpointStorage checkpointStorage;
    private final CheckpointConfig config;

    private final ConcurrentHashMap<Long, PendingCheckpoint> pendingCheckpoints;
    private final AtomicInteger numPendingCheckpoints;
    private volatile CompletedCheckpoint latestCompletedCheckpoint;
    private volatile Set<TaskLocation> tasksToAcknowledge;

    private ScheduledExecutorService scheduler;
    private final ScheduledExecutorService timeoutScheduler;
    private volatile boolean isSchedulerStarted = false;

    private final List<CheckpointListener> listeners = new CopyOnWriteArrayList<>();
    private final List<CheckpointParticipant> participants = new CopyOnWriteArrayList<>();
    private final CheckpointMetrics metrics = new CheckpointMetrics();

    private static final int DEFAULT_COMMIT_RETRIES = 3;
    private static final int CONSECUTIVE_FAILURE_THRESHOLD = 3;
    private final ConcurrentSkipListMap<Long, Set<CheckpointParticipant>> failedCommitParticipants = new ConcurrentSkipListMap<>();
    private final ConcurrentHashMap<Long, Boolean> checkpointSuccessMap = new ConcurrentHashMap<>();

    private final AtomicInteger consecutiveTriggerFailures = new AtomicInteger(0);

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
                        PendingCheckpoint result = tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                        if (result != null) {
                            consecutiveTriggerFailures.set(0);
                        } else {
                            int failures = consecutiveTriggerFailures.incrementAndGet();
                            if (failures == CONSECUTIVE_FAILURE_THRESHOLD) {
                                LOG.error("Checkpoint trigger failed {} consecutive times for job {}",
                                        failures, jobId);
                            }
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

    public PendingCheckpoint tryTriggerPendingCheckpoint(CheckpointType checkpointType) {
        if (numPendingCheckpoints.get() >= config.getMaxConcurrentCheckpoints()) {
            LOG.debug("Cannot trigger checkpoint: too many pending checkpoints ({})",
                    numPendingCheckpoints.get());
            return null;
        }

        long checkpointId = checkpointIdCounter.getAndIncrement();
        long timestamp = System.currentTimeMillis();

        Set<TaskLocation> tasksToAck = getTasksToAcknowledge();
        if (tasksToAck.isEmpty()) {
            LOG.debug("No tasks to acknowledge for checkpoint {}", checkpointId);
            return null;
        }

        PendingCheckpoint pending = new PendingCheckpoint(
                jobId, pipelineId, checkpointId, timestamp,
                checkpointType, tasksToAck);

        pendingCheckpoints.put(checkpointId, pending);
        numPendingCheckpoints.incrementAndGet();

        scheduleTimeout(pending);

        LOG.info("Triggered checkpoint {} for job {}", checkpointId, jobId);
        return pending;
    }

    public synchronized boolean acknowledgeTask(TaskLocation taskLocation, long checkpointId, TaskStateSnapshot state) {
        PendingCheckpoint pending = pendingCheckpoints.get(checkpointId);
        if (pending == null) {
            LOG.warn("Received ACK for unknown checkpoint {} from task {}", checkpointId, taskLocation);
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

    public void completePendingCheckpoint(CompletedCheckpoint completed) {
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

        try {
            checkpointStorage.storeCheckPoint(completed);
        } catch (Exception e) {
            LOG.error("Failed to store checkpoint {}", checkpointId, e);
            pending.getStatus().set(PendingCheckpoint.Status.ABORTED);
            pendingCheckpoints.remove(checkpointId, pending);
            decrementPendingCheckpointCount();
            metrics.incrementFailedCheckpoints();
            notifyParticipantsFinishCommit(checkpointId, false);
            notifyCheckpointAborted(checkpointId);
            LOG.warn("Aborted checkpoint {} for job {}: {}", checkpointId, jobId, "Failed to store checkpoint");
            return;
        }

        // Build and persist EpochManifest
        try {
            EpochManifest manifest = buildEpochManifest(completed);
            checkpointStorage.storeEpochManifest(jobId, pipelineId, manifest);
            LOG.debug("Stored EpochManifest for epoch {}", checkpointId);
        } catch (Exception e) {
            LOG.error("Failed to store EpochManifest for checkpoint {}, aborting checkpoint", checkpointId, e);
            pending.getStatus().set(PendingCheckpoint.Status.ABORTED);
            pendingCheckpoints.remove(checkpointId, pending);
            decrementPendingCheckpointCount();
            metrics.incrementFailedCheckpoints();
            notifyParticipantsFinishCommit(checkpointId, false);
            notifyCheckpointAborted(checkpointId);
            LOG.warn("Aborted checkpoint {} for job {}: {}", checkpointId, jobId, "Failed to store EpochManifest for checkpoint " + checkpointId);
            return;
        }

        if (!pendingCheckpoints.remove(checkpointId, pending)) {
            LOG.debug("Skip completing checkpoint {} because pending state changed", checkpointId);
            return;
        }

        // AR-19: Complete the future only after successful storage, so storage failure
        // does not leave a ghost checkpoint that callers already acted on.
        pending.forceComplete();

        latestCompletedCheckpoint = completed;
        decrementPendingCheckpointCount();

        metrics.incrementCompletedCheckpoints();
        metrics.updateLatestCheckpoint(completed.estimateSize(), completed.getDuration());

        cleanupOldCheckpoints();

        // Retry previously failed commits before processing current epoch
        retryFailedCommits();

        // Notify participants first: finishCommit in reverse topology order
        notifyParticipantsFinishCommit(checkpointId, true);

        notifyCheckpointCompleted(checkpointId);

        checkpointSuccessMap.remove(checkpointId);

        LOG.info("Completed checkpoint {} for job {}, duration: {}ms",
                checkpointId, jobId, completed.getDuration());
    }

    public void abortPendingCheckpoint(PendingCheckpoint pending, String reason) {
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

        metrics.incrementFailedCheckpoints();

        // Notify participants about abort: finishCommit(false) keeps prepared transactions for subsuming
        notifyParticipantsFinishCommit(checkpointId, false);

        notifyCheckpointAborted(checkpointId);

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

    public int getNumberOfPendingCheckpoints() {
        return numPendingCheckpoints.get();
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
        stopCheckpointScheduler();

        timeoutScheduler.shutdownNow();

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
