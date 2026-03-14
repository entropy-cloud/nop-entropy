/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
 */
public class Task implements Runnable, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(Task.class);

    /**
     * Enumeration of possible task states.
     */
    public enum State {
        /** Task is created but not yet running */
        CREATED,
        /** Task is currently executing */
        RUNNING,
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
            throw new IllegalArgumentException("JobVertex cannot be null");
        }
        if (taskIndex < 0) {
            throw new IllegalArgumentException("Task index must be non-negative, got: " + taskIndex);
        }
        if (taskIndex >= jobVertex.getParallelism()) {
            throw new IllegalArgumentException(
                "Task index " + taskIndex + " exceeds parallelism " + jobVertex.getParallelism());
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
        // Transition to RUNNING state
        if (!state.compareAndSet(State.CREATED, State.RUNNING)) {
            LOG.warn("Task {} cannot start - current state is {}",
                getTaskName(), state.get());
            return;
        }

        LOG.info("Starting task: {}", getTaskName());

        try {
            // Open all operator chains
            openOperatorChains();

            // Execute the invokable logic
            Invokable<?> invokable = jobVertex.getInvokable();
            invokable.invoke();

            // Transition to COMPLETED state
            state.set(State.COMPLETED);
            LOG.info("Task completed successfully: {}", getTaskName());

        } catch (Throwable t) {
            // Record the error and transition to FAILED state
            this.error = t;
            state.set(State.FAILED);
            LOG.error("Task failed: " + getTaskName(), t);

        } finally {
            // Always close operator chains
            closeOperatorChains();
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
                throw new RuntimeException(
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
        }
    }

    /**
     * Attempts to cancel the task.
     *
     * <p>If the task is in CREATED state, it transitions to CANCELED state
     * and will not execute. If the task is already running or completed,
     * this method has no effect.
     *
     * @return true if the task was canceled, false otherwise
     */
    public boolean cancel() {
        return state.compareAndSet(State.CREATED, State.CANCELED);
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
