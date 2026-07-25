/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * Represents a runnable task that executes a JobVertex in the streaming job.
 *
 * <p>A Task wraps a {@link JobVertex} and implements {@link Runnable} to enable
 * execution in a thread pool. Each task instance corresponds to one parallel
 * instance of a JobVertex and executes the vertex's {@link Invokable} logic.
 *
 * <p>The task lifecycle follows these states:
 * <ol>
 *   <li>CREATED: Task is initialized but not yet running</li>
 *   <li>RUNNING: Task is currently executing</li>
 *   <li>COMPLETED: Task finished successfully</li>
 *   <li>FAILED: Task encountered an error during execution</li>
 *   <li>CANCELED: Task was canceled before completion</li>
 * </ol>
 *
 * <p><strong>Execution Flow:</strong>
 * <ol>
 *   <li>Open all operator chains in the JobVertex</li>
 *   <li>Invoke the Invokable's logic (which processes the stream)</li>
 *   <li>Close all operator chains</li>
 * </ol>
 *
 * <p><strong>Thread Safety:</strong> Task instances are designed to be executed
 * by a single thread. The state transitions are thread-safe using atomic operations.
 *
 * @see JobVertex
 * @see Invokable
 * @see OperatorChain
 * @see TaskExecutor
 *
 * <p>Graph Path 执行单元：封装 JobVertex 在线程池中的运行逻辑
 */
@Internal
public class Task implements Runnable, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(Task.class);

    /**
     * Enumeration of possible task states.
     *
     * <p>State machine (G54/G58 unified transition model — see {@link TaskStateTransition}):
     * <ul>
     *   <li>{@code CREATED → SCHEDULED → DEPLOYING → RUNNING → COMPLETED} (normal deployment)</li>
     *   <li>{@code FAILED/non-terminal → RECOVERING → SCHEDULED → DEPLOYING → RUNNING} (recovery)</li>
     *   <li>{@code non-terminal → CANCELING → CANCELED} (cancel)</li>
     *   <li>{@code RUNNING/SCHEDULED/DEPLOYING/RECOVERING → FAILED} (failure)</li>
     * </ul>
     * Terminal states (COMPLETED/FAILED/CANCELED) are absorbing; transitions out throw.
     */
    public enum State {
        /** Task is created but not yet scheduled */
        CREATED,
        /** Task has been scheduled to a node */
        SCHEDULED,
        /** Task is being deployed (operator chain opening) */
        DEPLOYING,
        /** Task is currently executing */
        RUNNING,
        /** Task is recovering from a previous failure */
        RECOVERING,
        /** Task is being cooperatively canceled (intermediate) */
        CANCELING,
        /** Task completed successfully */
        COMPLETED,
        /** Task failed with an error */
        FAILED,
        /** Task was canceled */
        CANCELED
    }

    /**
     * The JobVertex that this task executes.
     */
    private final JobVertex jobVertex;

    /**
     * The index of this task instance (0 to parallelism-1).
     */
    private final int taskIndex;

    /**
     * The current state of this task, managed atomically for thread safety.
     */
    private final AtomicReference<State> state;

    /**
     * The error that caused the task to fail, if any.
     */
    private volatile Throwable error;

    /**
     * Constructs a new Task for the given JobVertex.
     *
     * @param jobVertex the JobVertex to execute (must not be null)
     * @param taskIndex the index of this task instance (0 to parallelism-1)
     * @throws IllegalArgumentException if jobVertex is null or taskIndex is invalid
     */
    public Task(JobVertex jobVertex, int taskIndex) {
        if (jobVertex == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "jobVertex");
        }
        if (taskIndex < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "taskIndex").param(ARG_DETAIL, "must be non-negative, got: " + taskIndex);
        }
        if (taskIndex >= jobVertex.getParallelism()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "taskIndex")
                .param(ARG_DETAIL, "Task index " + taskIndex + " exceeds parallelism " + jobVertex.getParallelism());
        }

        this.jobVertex = jobVertex;
        this.taskIndex = taskIndex;
        this.state = new AtomicReference<>(State.CREATED);
        this.error = null;
    }

    /**
     * Executes the task by running the JobVertex's Invokable.
     *
     * <p>This method follows the execution lifecycle:
     * <ol>
     *   <li>Transition to RUNNING state</li>
     *   <li>Open all operator chains</li>
     *   <li>Invoke the Invokable logic</li>
     *   <li>Close all operator chains</li>
     *   <li>Transition to COMPLETED state</li>
     * </ol>
     *
     * <p>If any step fails, the task transitions to FAILED state and the error is recorded.
     * The operator chains are always closed, even if an error occurs.
     */
    @Override
    public void run() {
        // Honor terminal / cancel states set before invocation
        State initial = state.get();
        if (initial == State.CANCELED || initial == State.COMPLETED || initial == State.FAILED) {
            LOG.warn("Task {} cannot start - already in terminal state {}", getTaskName(), initial);
            return;
        }
        if (initial == State.CANCELING) {
            // cancel() was invoked before run(); finalize the cancel closure
            state.compareAndSet(State.CANCELING, State.CANCELED);
            LOG.info("Task {} finalized CANCELING → CANCELED at run() entry", getTaskName());
            return;
        }

        // CREATED → SCHEDULED → DEPLOYING → RUNNING transition chain
        if (!transitionFromCreatedTo(State.SCHEDULED)) {
            LOG.warn("Task {} cannot advance to SCHEDULED - state is {}", getTaskName(), state.get());
            return;
        }

        LOG.info("Starting task: {}", getTaskName());

        try {
            // SCHEDULED → DEPLOYING (opening operator chains)
            if (!compareAndTransition(State.SCHEDULED, State.DEPLOYING)) {
                LOG.warn("Task {} state changed from SCHEDULED during deployment to {}",
                        getTaskName(), state.get());
                return;
            }

            openOperatorChains();

            // DEPLOYING → RUNNING (about to invoke)
            if (!compareAndTransition(State.DEPLOYING, State.RUNNING)) {
                LOG.warn("Task {} state changed from DEPLOYING to {} before invoke",
                        getTaskName(), state.get());
                return;
            }

            Invokable<?> invokable = jobVertex.getInvokable();
            invokable.invoke();

            // RUNNING → COMPLETED (success path)
            if (!compareAndTransition(State.RUNNING, State.COMPLETED)) {
                // Could have transitioned to CANCELING or FAILED in the meantime
                State current = state.get();
                if (current == State.CANCELING) {
                    state.compareAndSet(State.CANCELING, State.CANCELED);
                    LOG.info("Task {} finalized CANCELING → CANCELED after invoke", getTaskName());
                } else {
                    LOG.warn("Task {} state changed from RUNNING during execution to {}",
                            getTaskName(), current);
                }
            } else {
                LOG.info("Task completed successfully: {}", getTaskName());
            }

        } catch (Throwable t) {
            this.error = t;
            // RUNNING/SCHEDULED/DEPLOYING → FAILED (validate-then-CAS); fallback to set for safety
            State current = state.get();
            if (current == State.RUNNING || current == State.SCHEDULED || current == State.DEPLOYING) {
                state.set(State.FAILED);
            } else {
                // Already terminal (CANCELED/CANCELING); leave as-is so we don't overwrite cancel
                if (current == State.CANCELING) {
                    state.compareAndSet(State.CANCELING, State.CANCELED);
                }
            }
            LOG.error("Task failed: " + getTaskName(), t);

        } finally {
            try {
                closeOperatorChains();
            } catch (Exception closeEx) {
                if (this.error == null) {
                    this.error = closeEx;
                    state.compareAndSet(State.RUNNING, State.FAILED);
                } else {
                    this.error.addSuppressed(closeEx);
                }
            }
        }
    }

    /**
     * Opens all operator chains in the JobVertex.
     *
     * @throws RuntimeException if any operator chain fails to open
     */
    private void openOperatorChains() {
        List<OperatorChain> chains = jobVertex.getOperatorChains();
        LOG.debug("Opening {} operator chains for task: {}", chains.size(), getTaskName());

        for (int i = 0; i < chains.size(); i++) {
            try {
                chains.get(i).open();
                LOG.debug("Opened operator chain {} for task: {}", i, getTaskName());
            } catch (Exception e) {
                // Close already opened chains before propagating exception
                for (int j = 0; j < i; j++) {
                    try {
                        chains.get(j).close();
                    } catch (Exception closeEx) {
                        e.addSuppressed(closeEx);
                    }
                }
                throw new StreamException(
                    "Failed to open operator chain " + i + " for task: " + getTaskName(), e);
            }
        }
    }

    /**
     * Closes all operator chains in the JobVertex.
     * Exceptions during closing are logged but not propagated to ensure all chains get closed.
     */
    private void closeOperatorChains() {
        List<OperatorChain> chains = jobVertex.getOperatorChains();
        LOG.debug("Closing {} operator chains for task: {}", chains.size(), getTaskName());

        Exception firstException = null;

        // Close chains in reverse order
        for (int i = chains.size() - 1; i >= 0; i--) {
            try {
                chains.get(i).close();
                LOG.debug("Closed operator chain {} for task: {}", i, getTaskName());
            } catch (Exception e) {
                if (firstException == null) {
                    firstException = e;
                } else {
                    firstException.addSuppressed(e);
                }
                LOG.error("Failed to close operator chain {} for task: {}",
                    i, getTaskName(), e);
            }
        }

        if (firstException != null) {
            LOG.error("Errors occurred while closing operator chains for task: {}",
                getTaskName(), firstException);
            if (this.error != null) {
                this.error.addSuppressed(firstException);
            } else {
                this.error = firstException;
            }
        }
    }

    /**
     * Attempts to cancel the task (G58: unified cancel semantics).
     *
     * <p>Cancels the task by transitioning through {@code CANCELING} (the cooperative
     * intermediate state) toward {@code CANCELED}. For non-RUNNING states (no active
     * execution thread), the cancel closure itself advances {@code CANCELING → CANCELED}
     * atomically. For RUNNING state, the state stays in {@code CANCELING} and the run
     * loop is responsible for the {@code CANCELING → CANCELED} finalization.
     *
     * @return true if a cancel transition was performed (state changed), false if the
     *         task was already in a terminal state (COMPLETED/FAILED/CANCELED)
     */
    public boolean cancel() {
        while (true) {
            State current = state.get();
            if (current == State.COMPLETED || current == State.FAILED || current == State.CANCELED) {
                return false;
            }
            if (current == State.CANCELING) {
                // Another thread already canceled; idempotent
                return false;
            }
            // Validate the transition is legal for the current state
            if (!TaskStateTransition.isLegalTransition(current, State.CANCELING)) {
                return false;
            }
            if (!state.compareAndSet(current, State.CANCELING)) {
                // CAS failed because another thread moved state; retry
                continue;
            }
            // For non-RUNNING states there is no execution thread to finalize the
            // CANCELING → CANCELED transition, so the cancel closure does it.
            if (current != State.RUNNING) {
                state.compareAndSet(State.CANCELING, State.CANCELED);
            }
            return true;
        }
    }

    /**
     * Forcibly marks the task as FAILED (used by external recovery paths). Validates
     * the transition; no-op if the task is already in a terminal state.
     */
    public void markFailed(Throwable cause) {
        if (cause != null && this.error == null) {
            this.error = cause;
        }
        while (true) {
            State current = state.get();
            if (current == State.COMPLETED || current == State.FAILED || current == State.CANCELED) {
                return;
            }
            if (!TaskStateTransition.isLegalTransition(current, State.FAILED)) {
                return;
            }
            if (state.compareAndSet(current, State.FAILED)) {
                return;
            }
        }
    }

    /**
     * Transitions to the SCHEDULED state from CREATED (initial schedule step).
     * Returns false if another thread already advanced the state.
     */
    private boolean transitionFromCreatedTo(State target) {
        State current = state.get();
        if (current == target) {
            return true;
        }
        if (!TaskStateTransition.isLegalTransition(current, target)) {
            // Illegal; current may have advanced past CREATED. Allow if it's already
            // at or beyond target on the deployment path.
            return current == State.SCHEDULED
                    || current == State.DEPLOYING
                    || current == State.RUNNING;
        }
        return state.compareAndSet(current, target);
    }

    /**
     * Validates + CAS transition. Returns false if the CAS failed.
     */
    private boolean compareAndTransition(State expected, State target) {
        TaskStateTransition.validateTransition(expected, target);
        return state.compareAndSet(expected, target);
    }

    /**
     * Gets the current state of the task.
     *
     * @return the current state
     */
    public State getState() {
        return state.get();
    }

    /**
     * Marks the task as scheduled (CREATED → SCHEDULED). Returns false if the
     * transition is illegal or lost a CAS race. Used by external orchestrators
     * (e.g. JobCoordinator) that drive the lifecycle explicitly.
     */
    public boolean markScheduled() {
        return casTransition(State.CREATED, State.SCHEDULED);
    }

    /**
     * Marks the task as recovering (RECOVERING entry from a non-terminal state).
     */
    public boolean markRecovering() {
        State current = state.get();
        if (!TaskStateTransition.isLegalTransition(current, State.RECOVERING)) {
            return false;
        }
        return state.compareAndSet(current, State.RECOVERING);
    }

    private boolean casTransition(State expected, State target) {
        TaskStateTransition.validateTransition(expected, target);
        return state.compareAndSet(expected, target);
    }

    /**
     * Gets the JobVertex that this task executes.
     *
     * @return the JobVertex
     */
    public JobVertex getJobVertex() {
        return jobVertex;
    }

    /**
     * Gets the index of this task instance.
     *
     * @return the task index (0 to parallelism-1)
     */
    public int getTaskIndex() {
        return taskIndex;
    }

    /**
     * Gets the error that caused the task to fail.
     *
     * @return the error, or null if the task has not failed
     */
    public Throwable getError() {
        return error;
    }

    /**
     * Checks if the task is in a terminal state (COMPLETED, FAILED, or CANCELED).
     *
     * @return true if the task is in a terminal state
     */
    public boolean isFinished() {
        State currentState = state.get();
        return currentState == State.COMPLETED ||
               currentState == State.FAILED ||
               currentState == State.CANCELED;
    }

    /**
     * Gets a human-readable name for this task.
     *
     * @return the task name in format "VertexName (index/taskIndex)"
     */
    public String getTaskName() {
        return String.format("%s (%d/%d)",
            jobVertex.getName(), taskIndex, jobVertex.getParallelism());
    }

    @Override
    public String toString() {
        return "Task{" +
            "vertexId='" + jobVertex.getId() + '\'' +
            ", taskIndex=" + taskIndex +
            ", state=" + state.get() +
            '}';
    }
}
