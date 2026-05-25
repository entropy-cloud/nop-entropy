/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;

public class SubtaskTask implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(SubtaskTask.class);

    public enum State {
        CREATED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELED
    }

    private final Subtask subtask;
    private final JobVertex jobVertex;
    private final AtomicReference<State> state;
    private volatile Throwable error;

    public SubtaskTask(Subtask subtask, JobVertex jobVertex) {
        if (subtask == null) {
            throw new IllegalArgumentException("Subtask cannot be null");
        }
        if (jobVertex == null) {
            throw new IllegalArgumentException("JobVertex cannot be null");
        }
        this.subtask = subtask;
        this.jobVertex = jobVertex;
        this.state = new AtomicReference<>(State.CREATED);
    }

    @Override
    public void run() {
        if (!state.compareAndSet(State.CREATED, State.RUNNING)) {
            LOG.warn("SubtaskTask {} cannot start - current state is {}", getTaskName(), state.get());
            return;
        }

        LOG.info("Starting subtask: {}", getTaskName());

        try {
            openOperatorChains();

            subtask.getInvokable().invoke();

            state.set(State.COMPLETED);
            LOG.info("Subtask completed successfully: {}", getTaskName());
        } catch (Throwable t) {
            this.error = t;
            state.set(State.FAILED);
            LOG.error("Subtask failed: " + getTaskName(), t);
        } finally {
            closeOperatorChains();
        }
    }

    public boolean cancel() {
        return state.compareAndSet(State.CREATED, State.CANCELED);
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
        List<OperatorChain> chains = jobVertex.getOperatorChains();
        for (int i = 0; i < chains.size(); i++) {
            try {
                chains.get(i).open();
            } catch (Exception e) {
                for (int j = 0; j < i; j++) {
                    try {
                        chains.get(j).close();
                    } catch (Exception closeEx) {
                        e.addSuppressed(closeEx);
                    }
                }
                throw new RuntimeException(
                    "Failed to open operator chain " + i + " for subtask: " + getTaskName(), e);
            }
        }
    }

    private void closeOperatorChains() {
        List<OperatorChain> chains = jobVertex.getOperatorChains();
        Exception firstException = null;
        for (int i = chains.size() - 1; i >= 0; i--) {
            try {
                chains.get(i).close();
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
        }
    }
}
