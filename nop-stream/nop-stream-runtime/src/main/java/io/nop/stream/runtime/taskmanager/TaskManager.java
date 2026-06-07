/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.*;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.jobgraph.OperatorChain;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService;
import io.nop.stream.runtime.rpc.IStreamTaskRpcService;
import io.nop.stream.runtime.transport.RemoteInputChannel;
import io.nop.stream.runtime.transport.RemoteResultPartition;

/**
 * TaskManager is the distributed runtime component on each worker node.
 *
 * <p>It is responsible for:
 * <ul>
 *   <li>Registering with the {@link ClusterRegistry} and sending periodic heartbeats</li>
 *   <li>Receiving task assignments and creating {@link StreamTaskInvokable} instances</li>
 *   <li>Running tasks in a local thread pool with {@link RemoteResultPartition}/{@link RemoteInputChannel}</li>
 *   <li>Handling checkpoint barrier signals from the coordinator</li>
 *   <li>Sending checkpoint ACKs back to the coordinator via control topic</li>
 *   <li>Enforcing fencing tokens to reject stale operations</li>
 * </ul>
 *
 * <p><strong>Fencing:</strong> Each job execution epoch has a fencing token. When the
 * coordinator performs global recovery, a new fencing token is issued. The TaskManager
 * rejects any operation carrying an old fencing token.
 */
@Internal
public class TaskManager implements IStreamTaskRpcService {

    private static final Logger LOG = LoggerFactory.getLogger(TaskManager.class);

    private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 5000L;
    private static final long DEFAULT_LEASE_TIMEOUT_MS = 15000L;

    private final String nodeId;
    private final String endpoint;
    private final int capacity;
    private final IMessageService messageService;
    private final ClusterRegistry clusterRegistry;

    private final Semaphore capacitySemaphore;

    private final ExecutorService taskExecutor;
    private final ScheduledExecutorService heartbeatExecutor;

    /** fencingToken → RunningTask */
    private final ConcurrentHashMap<String, RunningTask> runningTasks;

    /** taskKey → TaskResult for completed tasks (bounded to MAX_COMPLETED_TASKS) */
    private static final int MAX_COMPLETED_TASKS = 1000;
    private final ConcurrentHashMap<String, TaskResult> completedTasks;

    /** The currently active fencing token for this node (updated on global recovery) */
    private final AtomicReference<String> currentFencingToken;

    /** Control topic for sending ACKs via message service (fallback when no RPC service) */
    private final String controlTopic;

    /** RPC service for sending ACKs directly to coordinator */
    private volatile IStreamCoordinatorRpcService coordinatorRpcService;

    private volatile boolean running;

    public TaskManager(String nodeId,
                       String endpoint,
                       int capacity,
                       IMessageService messageService,
                       ClusterRegistry clusterRegistry,
                       String controlTopic) {
        this.nodeId = nodeId;
        this.endpoint = endpoint;
        this.capacity = capacity;
        this.messageService = messageService;
        this.clusterRegistry = clusterRegistry;
        this.controlTopic = controlTopic;
        this.capacitySemaphore = new Semaphore(Math.max(1, capacity));
        this.taskExecutor = Executors.newFixedThreadPool(Math.max(1, capacity), r -> {
            Thread t = new Thread(r, "tm-task-" + nodeId);
            t.setDaemon(true);
            return t;
        });
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tm-heartbeat-" + nodeId);
            t.setDaemon(true);
            return t;
        });
        this.runningTasks = new ConcurrentHashMap<>();
        this.completedTasks = new ConcurrentHashMap<>();
        this.currentFencingToken = new AtomicReference<>();
        this.running = false;
    }

    // ==================== Lifecycle ====================

    /**
     * Registers this node in the ClusterRegistry and starts the heartbeat loop.
     */
    public void start() {
        if (running) {
            LOG.warn("TaskManager {} already started", nodeId);
            return;
        }

        clusterRegistry.registerNode(nodeId, endpoint, capacity);
        running = true;

        heartbeatExecutor.scheduleAtFixedRate(
                this::heartbeat,
                DEFAULT_HEARTBEAT_INTERVAL_MS,
                DEFAULT_HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS);

        LOG.info("TaskManager {} started at endpoint {} with capacity {}", nodeId, endpoint, capacity);
    }

    /**
     * Shuts down the thread pool, cancels heartbeats, and unregisters from the ClusterRegistry.
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        heartbeatExecutor.shutdownNow();
        taskExecutor.shutdownNow();

        try {
            if (!taskExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("TaskExecutor did not terminate within 5 seconds for node {}", nodeId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while waiting for TaskExecutor termination for node {}", nodeId);
        }

        for (Map.Entry<String, RunningTask> entry : runningTasks.entrySet()) {
            entry.getValue().cancel();
        }
        runningTasks.clear();

        LOG.info("TaskManager {} stopped", nodeId);
    }

    // ==================== Heartbeat ====================

    /**
     * Renews the lease for this node in the ClusterRegistry.
     */
    public void heartbeat() {
        if (!running) {
            return;
        }
        try {
            boolean renewed = clusterRegistry.renewLease(nodeId, DEFAULT_LEASE_TIMEOUT_MS);
            if (!renewed) {
                LOG.warn("Failed to renew lease for node {}. Re-registering.", nodeId);
                clusterRegistry.registerNode(nodeId, endpoint, capacity);
            }
        } catch (Exception e) {
            LOG.error("Heartbeat failed for node {}", nodeId, e);
        }
    }

    // ==================== Task Assignment ====================

    /**
     * Receives a task assignment, creates the invokable, and starts execution.
     *
     * <p>The assignment must carry the current fencing token; otherwise it is rejected.
     *
     * @param assignment the task assignment from the coordinator
     */
    @Override
    public void receiveAssignment(TaskAssignment assignment) {
        if (!running) {
            LOG.warn("TaskManager {} not running, rejecting assignment", nodeId);
            return;
        }

        // Fencing token check
        String activeToken = currentFencingToken.get();
        if (activeToken != null && !activeToken.equals(assignment.getFencingToken())) {
            LOG.warn("Rejecting assignment with stale fencing token: expected={}, got={}",
                    activeToken, assignment.getFencingToken());
            return;
        }

        // AR-9: Use semaphore for capacity control instead of race-prone size check
        if (!capacitySemaphore.tryAcquire()) {
            LOG.warn("Node {} at capacity ({}/{}), rejecting assignment for {}/{}",
                    nodeId, capacity - capacitySemaphore.availablePermits(), capacity,
                    assignment.getVertexId(), assignment.getSubtaskIndex());
            return;
        }

        String taskKey = taskKey(assignment);
        if (runningTasks.containsKey(taskKey)) {
            LOG.warn("Task {} already running, ignoring duplicate assignment", taskKey);
            capacitySemaphore.release();
            return;
        }

        // Build the invokable — for now we create a placeholder that will be
        // populated by the coordinator via a separate deployment message.
        // In a full implementation, the coordinator sends the OperatorChain
        // serialized alongside the assignment.
        RunningTask runningTask = new RunningTask(
                assignment.getJobId(),
                assignment.getVertexId(),
                assignment.getSubtaskIndex(),
                assignment.getFencingToken(),
                assignment.getAttemptId());

        RunningTask existing = runningTasks.putIfAbsent(taskKey, runningTask);
        if (existing != null) {
            capacitySemaphore.release();
            return;
        }

        Future<?> future = taskExecutor.submit(runningTask);
        runningTask.setFuture(future);

        LOG.info("TaskManager {} accepted assignment for {}/{} (attempt={})",
                nodeId, assignment.getVertexId(), assignment.getSubtaskIndex(),
                assignment.getAttemptId());
    }

    /**
     * Receive and install a fully-built {@link StreamTaskInvokable} for a previously assigned task slot.
     *
     * <p>This is called after {@link #receiveAssignment} when the coordinator sends the
     * serialized operator chain and deployment plan.
     */
    public void installInvokable(String jobId, String vertexId, int subtaskIndex,
                                 StreamTaskInvokable invokable) {
        String taskKey = taskKey(jobId, vertexId, subtaskIndex);
        RunningTask runningTask = runningTasks.get(taskKey);
        if (runningTask == null) {
            LOG.warn("No running task slot for {}/{}/{}", jobId, vertexId, subtaskIndex);
            return;
        }
        runningTask.setInvokable(invokable);
    }

    // ==================== Checkpoint ====================

    /**
     * Handles a checkpoint barrier signal from the coordinator.
     *
     * <p>Injects the barrier into the source operator's pending barrier queue
     * (via {@link CheckpointBarrierTracker}).
     *
     * @param barrier       the checkpoint barrier
     * @param fencingToken  the fencing token of the current epoch
     */
    @Override
    public void triggerCheckpoint(CheckpointBarrier barrier, String fencingToken) {
        String activeToken = currentFencingToken.get();
        if (activeToken != null && !activeToken.equals(fencingToken)) {
            LOG.warn("Ignoring checkpoint signal with stale fencing token");
            return;
        }

        for (RunningTask task : runningTasks.values()) {
            if (task.getFencingToken().equals(fencingToken)) {
                task.triggerCheckpoint(barrier);
            }
        }
    }

    @Override
    public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
        String taskKey = taskKey(jobId, vertexId, subtaskIndex);
        RunningTask task = runningTasks.remove(taskKey);
        if (task != null) {
            task.cancel();
            if (task.semaphoreReleased.compareAndSet(false, true)) {
                capacitySemaphore.release();
            }
            LOG.info("Canceled task {}/{}/{}", jobId, vertexId, subtaskIndex);
        } else {
            LOG.warn("No running task to cancel for {}/{}/{}", jobId, vertexId, subtaskIndex);
        }
    }

    /**
     * Sends a checkpoint ACK to the coordinator via the control topic.
     *
     * @param checkpointId the checkpoint ID
     * @param snapshot     the task state snapshot
     */
    public void sendCheckpointAck(long checkpointId, TaskStateSnapshot snapshot) {
        CheckpointAckMessage ack = new CheckpointAckMessage(
                snapshot.getTaskLocation(),
                checkpointId,
                snapshot,
                currentFencingToken.get());

        try {
            if (coordinatorRpcService != null) {
                coordinatorRpcService.receiveCheckpointAck(ack);
            } else {
                throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                        "No coordinator RPC service available. "
                        + "All checkpoint ACKs require IStreamCoordinatorRpcService.");
            }
            LOG.debug("Sent checkpoint ACK for checkpoint {} from {}",
                    checkpointId, snapshot.getTaskLocation());
        } catch (Exception e) {
            LOG.error("Failed to send checkpoint ACK for checkpoint {}", checkpointId, e);
        }
    }

    // ==================== Fencing ====================

    /**
     * Updates the fencing token. Tasks with the old token are canceled.
     *
     * @param newToken the new fencing token
     */
    public void updateFencingToken(String newToken) {
        String oldToken = currentFencingToken.getAndSet(newToken);
        if (oldToken != null && !oldToken.equals(newToken)) {
            LOG.info("Fencing token updated from {} to {}. Canceling old tasks.", oldToken, newToken);
            runningTasks.entrySet().removeIf(entry -> {
                if (entry.getValue().getFencingToken().equals(oldToken)) {
                    entry.getValue().cancel();
                    if (entry.getValue().semaphoreReleased.compareAndSet(false, true)) {
                        capacitySemaphore.release();
                    }
                    return true;
                }
                return false;
            });
        }
    }

    // ==================== Status ====================

    public String getNodeId() {
        return nodeId;
    }

    public void setCoordinatorRpcService(IStreamCoordinatorRpcService coordinatorRpcService) {
        this.coordinatorRpcService = coordinatorRpcService;
    }

    public int getRunningTaskCount() {
        return runningTasks.size();
    }

    int availablePermits() {
        return capacitySemaphore.availablePermits();
    }

    public Map<String, TaskResult> getCompletedTaskResults() {
        return Collections.unmodifiableMap(completedTasks);
    }

    public boolean isRunning() {
        return running;
    }

    // ==================== Helpers ====================

    private String taskKey(TaskAssignment assignment) {
        return taskKey(assignment.getJobId(), assignment.getVertexId(), assignment.getSubtaskIndex());
    }

    private String taskKey(String jobId, String vertexId, int subtaskIndex) {
        return jobId + "/" + vertexId + "/" + subtaskIndex;
    }

    // ==================== Inner Classes ====================

    /**
     * A running task tracked by the TaskManager.
     */
    public class RunningTask implements Runnable {
        private final String jobId;
        private final String vertexId;
        private final int subtaskIndex;
        private final String fencingToken;
        private final String attemptId;
        private final TaskLocation taskLocation;
        private final CountDownLatch invokableLatch;

        private volatile StreamTaskInvokable invokable;
        private volatile Future<?> future;
        private volatile boolean canceled;
        private volatile Throwable error;
        private final AtomicBoolean semaphoreReleased = new AtomicBoolean(false);

        public RunningTask(String jobId, String vertexId, int subtaskIndex,
                           String fencingToken, String attemptId) {
            this.jobId = jobId;
            this.vertexId = vertexId;
            this.subtaskIndex = subtaskIndex;
            this.fencingToken = fencingToken;
            this.attemptId = attemptId;
            this.taskLocation = new TaskLocation(jobId, "pipeline-0", vertexId, subtaskIndex);
            this.invokableLatch = new CountDownLatch(1);
        }

        @Override
        public void run() {
            if (canceled) {
                LOG.info("Task {}/{}/{} was canceled before execution", jobId, vertexId, subtaskIndex);
                return;
            }

            LOG.info("Running task {}/{}/{} (attempt={})", jobId, vertexId, subtaskIndex, attemptId);

            try {
                // Wait for invokable to be installed if not yet available
                StreamTaskInvokable inv = waitForInvokable();
                if (inv == null || canceled) {
                    LOG.info("Task {}/{}/{} canceled while waiting for invokable", jobId, vertexId, subtaskIndex);
                    return;
                }

                inv.invoke();

                if (!canceled) {
                    LOG.info("Task {}/{}/{} completed successfully", jobId, vertexId, subtaskIndex);
                }
            } catch (Throwable t) {
                if (!canceled) {
                    this.error = t;
                    LOG.error("Task {}/{}/{} failed", jobId, vertexId, subtaskIndex, t);
                }
            } finally {
                String key = taskKey(jobId, vertexId, subtaskIndex);
                completedTasks.put(key, new TaskResult(jobId, vertexId, subtaskIndex,
                        error == null && !canceled, canceled, error));
                if (completedTasks.size() > MAX_COMPLETED_TASKS) {
                    Iterator<String> it = completedTasks.keySet().iterator();
                    if (it.hasNext()) {
                        it.next();
                        it.remove();
                    }
                }
                runningTasks.remove(key);
                if (semaphoreReleased.compareAndSet(false, true)) {
                    capacitySemaphore.release();
                }
            }
        }

        private StreamTaskInvokable waitForInvokable() throws InterruptedException {
            if (!invokableLatch.await(30, TimeUnit.SECONDS)) {
                LOG.warn("Timed out waiting for invokable for {}/{}/{}", jobId, vertexId, subtaskIndex);
                return null;
            }
            return invokable;
        }

        public void setInvokable(StreamTaskInvokable invokable) {
            this.invokable = invokable;
            invokableLatch.countDown();
        }

        public void setFuture(Future<?> future) {
            this.future = future;
        }

        public void cancel() {
            canceled = true;
            invokableLatch.countDown();
            if (future != null) {
                future.cancel(true);
            }
        }

        public void triggerCheckpoint(CheckpointBarrier barrier) {
            StreamTaskInvokable inv = this.invokable;
            if (inv == null) {
                LOG.debug("Cannot trigger checkpoint: invokable not yet installed for {}/{}/{}",
                        jobId, vertexId, subtaskIndex);
                return;
            }
            CheckpointBarrierTracker tracker = inv.getBarrierTracker();
            if (tracker == null) {
                LOG.debug("No barrier tracker for {}/{}/{}", jobId, vertexId, subtaskIndex);
                return;
            }
            try {
                tracker.triggerCheckpoint(barrier.getId(), barrier.getTimestamp(), barrier.getCheckpointType());
            } catch (Exception e) {
                LOG.error("Failed to trigger checkpoint on {}/{}/{}", jobId, vertexId, subtaskIndex, e);
            }
        }

        public String getFencingToken() {
            return fencingToken;
        }

        public String getJobId() { return jobId; }
        public String getVertexId() { return vertexId; }
        public int getSubtaskIndex() { return subtaskIndex; }
        public TaskLocation getTaskLocation() { return taskLocation; }
    }

    public static class TaskResult {
        private final String jobId;
        private final String vertexId;
        private final int subtaskIndex;
        private final boolean success;
        private final boolean canceled;
        private final Throwable error;

        public TaskResult(String jobId, String vertexId, int subtaskIndex,
                          boolean success, boolean canceled, Throwable error) {
            this.jobId = jobId;
            this.vertexId = vertexId;
            this.subtaskIndex = subtaskIndex;
            this.success = success;
            this.canceled = canceled;
            this.error = error;
        }

        public String getJobId() { return jobId; }
        public String getVertexId() { return vertexId; }
        public int getSubtaskIndex() { return subtaskIndex; }
        public boolean isSuccess() { return success; }
        public boolean isCanceled() { return canceled; }
        public Throwable getError() { return error; }
    }
}
