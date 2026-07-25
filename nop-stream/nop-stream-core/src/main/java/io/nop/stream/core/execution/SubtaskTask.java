/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INIT_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

public class SubtaskTask implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(SubtaskTask.class);

    /**
     * SubtaskTask lifecycle states. Shares the unified transition model defined in
     * {@link TaskStateTransition} (G54/G58).
     */
    public enum State {
        CREATED,
        SCHEDULED,
        DEPLOYING,
        RUNNING,
        RECOVERING,
        CANCELING,
        COMPLETED,
        FAILED,
        CANCELED
    }

    private final Subtask subtask;
    private final JobVertex jobVertex;
    private final List<OperatorChain> operatorChains;
    private final AtomicReference<State> state;
    private volatile Throwable error;
    private volatile Thread executingThread;

    public SubtaskTask(Subtask subtask, JobVertex jobVertex) {
        this(subtask, jobVertex, Collections.singletonList(subtask.getInvokable().getOperatorChain()));
    }

    public SubtaskTask(Subtask subtask, JobVertex jobVertex, List<OperatorChain> operatorChains) {
        if (subtask == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "subtask");
        }
        if (jobVertex == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "jobVertex");
        }
        this.subtask = subtask;
        this.jobVertex = jobVertex;
        this.operatorChains = operatorChains != null ? operatorChains : jobVertex.getOperatorChains();
        this.state = new AtomicReference<>(State.CREATED);
    }

    @Override
    public void run() {
        State initial = state.get();
        if (initial == State.CANCELED || initial == State.COMPLETED || initial == State.FAILED) {
            LOG.warn("SubtaskTask {} cannot start - already in terminal state {}", getTaskName(), initial);
            return;
        }
        if (initial == State.CANCELING) {
            // cancel() before run(); finalize the cancel closure
            state.compareAndSet(State.CANCELING, State.CANCELED);
            LOG.info("Subtask {} finalized CANCELING → CANCELED at run() entry", getTaskName());
            return;
        }

        // CREATED → SCHEDULED → DEPLOYING → RUNNING chain (G54)
        if (!state.compareAndSet(State.CREATED, State.SCHEDULED)) {
            LOG.warn("SubtaskTask {} cannot advance to SCHEDULED - state is {}",
                    getTaskName(), state.get());
            return;
        }

        LOG.info("Starting subtask: {}", getTaskName());
        executingThread = Thread.currentThread();

        try {
            // SCHEDULED → DEPLOYING
            if (!compareAndTransition(State.SCHEDULED, State.DEPLOYING)) {
                LOG.warn("Subtask {} state changed from SCHEDULED to {}",
                        getTaskName(), state.get());
                return;
            }

            openOperatorChains();

            // DEPLOYING → RUNNING
            if (!compareAndTransition(State.DEPLOYING, State.RUNNING)) {
                LOG.warn("Subtask {} state changed from DEPLOYING to {}",
                        getTaskName(), state.get());
                return;
            }

            while (state.get() == State.RUNNING) {
                subtask.getInvokable().invoke();
                break;
            }

            if (state.get() == State.RUNNING) {
                state.set(State.COMPLETED);
                LOG.info("Subtask completed successfully: {}", getTaskName());
            } else if (state.get() == State.CANCELING) {
                state.set(State.CANCELED);
                LOG.info("Subtask canceled: {}", getTaskName());
            }
        } catch (Throwable t) {
            this.error = t;
            State s = state.get();
            if (s == State.CANCELING) {
                state.set(State.CANCELED);
                LOG.info("Subtask canceled with error: {}", getTaskName());
            } else {
                state.set(State.FAILED);
                LOG.error("Subtask failed: " + getTaskName(), t);
            }
        } finally {
            executingThread = null;
            closeOperatorChains();
        }
    }

    public boolean cancel() {
        while (true) {
            State current = state.get();
            if (current == State.COMPLETED || current == State.FAILED || current == State.CANCELED) {
                return false;
            }
            if (current == State.CANCELING) {
                return false;
            }
            if (!TaskStateTransition.isLegalTransition(current, State.CANCELING)) {
                return false;
            }
            if (!state.compareAndSet(current, State.CANCELING)) {
                continue;
            }
            if (current == State.RUNNING) {
                Thread t = executingThread;
                if (t != null) {
                    t.interrupt();
                }
            } else {
                // No execution thread to finalize; cancel closure advances to CANCELED
                state.compareAndSet(State.CANCELING, State.CANCELED);
            }
            return true;
        }
    }

    private boolean compareAndTransition(State expected, State target) {
        TaskStateTransition.validateTransition(expected, target);
        return state.compareAndSet(expected, target);
    }

    public State getState() {
        return state.get();
    }

    public Throwable getError() {
        return error;
    }

    public Subtask getSubtask() {
        return subtask;
    }

    public boolean isFinished() {
        State s = state.get();
        return s == State.COMPLETED || s == State.FAILED || s == State.CANCELED;
    }

    public String getTaskName() {
        return String.format("%s [subtask %d]", subtask.getVertexId(), subtask.getTaskIndex());
    }

    private void openOperatorChains() {
        for (int i = 0; i < operatorChains.size(); i++) {
            try {
                operatorChains.get(i).open();
            } catch (Exception e) {
                for (int j = 0; j < i; j++) {
                    try {
                        operatorChains.get(j).close();
                    } catch (Exception closeEx) {
                        e.addSuppressed(closeEx);
                    }
                }
                throw new StreamException(ERR_STREAM_INIT_ERROR, e)
                    .param(ARG_DETAIL, "Failed to open operator chain " + i + " for subtask: " + getTaskName());
            }
        }
    }

    private void closeOperatorChains() {
        Exception firstException = null;
        for (int i = operatorChains.size() - 1; i >= 0; i--) {
            try {
                operatorChains.get(i).close();
            } catch (Exception e) {
                if (firstException == null) {
                    firstException = e;
                } else {
                    firstException.addSuppressed(e);
                }
            }
        }
        if (firstException != null) {
            LOG.error("Errors closing operator chains for subtask: {}", getTaskName(), firstException);
            if (this.error != null) {
                this.error.addSuppressed(firstException);
            } else {
                this.error = firstException;
            }
        }
    }
}
