/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.common.state.CheckpointListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Checkpoint 协调器，负责触发、跟踪和完成 checkpoint。
 */
public class CheckpointCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointCoordinator.class);

    private final long jobId;
    private final int pipelineId;
    private final CheckpointIDCounter checkpointIdCounter;
    private final ICheckpointStorage checkpointStorage;
    private final CheckpointConfig config;

    private final ConcurrentHashMap<Long, PendingCheckpoint> pendingCheckpoints;
    private final AtomicInteger numPendingCheckpoints;
    private volatile CompletedCheckpoint latestCompletedCheckpoint;
    private final Set<Long> tasksToAcknowledge;

    private ScheduledExecutorService scheduler;
    private final ScheduledExecutorService timeoutScheduler;
    private volatile boolean isSchedulerStarted = false;

    private final List<CheckpointListener> listeners = new CopyOnWriteArrayList<>();

    public CheckpointCoordinator(
            long jobId,
            int pipelineId,
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

    public void startCheckpointScheduler() {
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
                        tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
                    } catch (Exception e) {
                        LOG.error("Failed to trigger checkpoint", e);
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

        Set<Long> tasksToAcknowledge = getTasksToAcknowledge();
        if (tasksToAcknowledge.isEmpty()) {
            LOG.debug("No tasks to acknowledge for checkpoint {}", checkpointId);
            return null;
        }

        PendingCheckpoint pending = new PendingCheckpoint(
                jobId, pipelineId, checkpointId, timestamp,
                checkpointType, tasksToAcknowledge);

        pendingCheckpoints.put(checkpointId, pending);
        numPendingCheckpoints.incrementAndGet();

        pending.getCompletableFuture().whenComplete((completed, error) -> {
            if (error == null && completed != null) {
                completePendingCheckpoint(completed);
            } else {
                abortPendingCheckpoint(pending, error != null ? error.getMessage() : "Unknown error");
            }
        });

        scheduleTimeout(pending);

        LOG.info("Triggered checkpoint {} for job {}", checkpointId, jobId);
        return pending;
    }

    public boolean acknowledgeTask(long taskId, long checkpointId, TaskStateSnapshot state) {
        PendingCheckpoint pending = pendingCheckpoints.get(checkpointId);
        if (pending == null) {
            LOG.warn("Received ACK for unknown checkpoint {} from task {}", checkpointId, taskId);
            return false;
        }

        pending.acknowledgeTask(taskId, state);
        LOG.debug("Task {} acknowledged checkpoint {}, pending tasks: {}",
                taskId, checkpointId, pending.getNumberOfNotAcknowledgedTasks());
        return true;
    }

    public void completePendingCheckpoint(CompletedCheckpoint completed) {
        long checkpointId = completed.getCheckpointId();
        PendingCheckpoint pending = pendingCheckpoints.get(checkpointId);
        if (pending == null) {
            LOG.debug("Skip completing checkpoint {} because it is no longer pending", checkpointId);
            return;
        }

        try {
            checkpointStorage.storeCheckPoint(completed);
        } catch (Exception e) {
            LOG.error("Failed to store checkpoint {}", checkpointId, e);
            abortPendingCheckpoint(pending, "Failed to store checkpoint");
            return;
        }

        if (!pendingCheckpoints.remove(checkpointId, pending)) {
            LOG.debug("Skip completing checkpoint {} because pending state changed", checkpointId);
            return;
        }

        latestCompletedCheckpoint = completed;
        decrementPendingCheckpointCount();

        cleanupOldCheckpoints();

        notifyCheckpointCompleted(checkpointId);

        LOG.info("Completed checkpoint {} for job {}, duration: {}ms",
                checkpointId, jobId, completed.getDuration());
    }

    public void abortPendingCheckpoint(PendingCheckpoint pending, String reason) {
        long checkpointId = pending.getCheckpointId();
        PendingCheckpoint removed = pendingCheckpoints.remove(checkpointId);
        if (removed == null) {
            LOG.debug("Skip aborting checkpoint {} because it is no longer pending", checkpointId);
            return;
        }

        removed.abort(reason);
        decrementPendingCheckpointCount();

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

    protected Set<Long> getTasksToAcknowledge() {
        return new HashSet<>(tasksToAcknowledge);
    }

    public void setTasksToAcknowledge(Collection<Long> taskIds) {
        tasksToAcknowledge.clear();
        if (taskIds != null) {
            for (Long taskId : taskIds) {
                if (taskId != null) {
                    tasksToAcknowledge.add(taskId);
                }
            }
        }
    }

    public void registerTask(long taskId) {
        tasksToAcknowledge.add(taskId);
    }

    public void unregisterTask(long taskId) {
        tasksToAcknowledge.remove(taskId);
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
                    checkpointStorage.deleteCheckpoint(jobId, pipelineId, old.getCheckpointId());
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

    public void shutdown() {
        stopCheckpointScheduler();

        timeoutScheduler.shutdownNow();

        for (PendingCheckpoint pending : pendingCheckpoints.values()) {
            pending.dispose();
        }
        pendingCheckpoints.clear();
        listeners.clear();

        LOG.info("Checkpoint coordinator shutdown for job {}", jobId);
    }
}
