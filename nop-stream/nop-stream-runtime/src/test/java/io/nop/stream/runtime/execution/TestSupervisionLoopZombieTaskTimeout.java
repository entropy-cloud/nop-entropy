/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.execution.Subtask;
import io.nop.stream.core.execution.SubtaskTask;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.StreamSinkOperator;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 hardening (Phase 4): {@link SupervisionLoop#waitForTerminal} must fail loud
 * (throw {@code ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT}) when a task does not
 * reach a terminal state within the cooperative-cancel budget, instead of
 * silently WARN-ing and falling through — which previously let
 * {@code SupervisionLoop.restartRegion} rebuild + resubmit a second task instance
 * (a zombie: two producers writing the same {@code ResultPartition}, racing on
 * {@code currentMaterializationEpoch}, breaking exactly-once).
 *
 * <p>Anchored on the {@code TestSupervisionLoopRestartLimitConfig} fixture style:
 * a real {@link SubtaskTask} built from a minimal sink vertex. The task is left
 * in the CREATED (non-terminal) state to simulate a task stuck in a
 * non-interruptible section that never observes the cancel. Because
 * {@code waitForTerminal} now throws, the rebuild/submit phase that follows it in
 * {@code restartRegion} is never reached — no second task instance is created.
 */
class TestSupervisionLoopZombieTaskTimeout {

    /**
     * A task that never reaches a terminal state within a short budget must cause
     * {@code waitForTerminal} to throw {@code ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT}
     * (fail loud), not silently return.
     */
    @Test
    void waitForTerminal_timesOut_failsLoudWithZombieTimeoutError() {
        SubtaskTask stuckTask = buildMinimalNonTerminalTask();

        StreamException ex = assertThrows(StreamException.class,
                () -> SupervisionLoop.waitForTerminal(stuckTask, "sink-B-0", 100L),
                "waitForTerminal must fail loud on timeout, not silently fall through");

        assertNotNullErrorCode(ex);
        assertTrue(ex.getErrorCode().contains("supervision-zombie-task-timeout"),
                "error code must be the zombie-timeout code (distinct from restart-exhausted), got: "
                        + ex.getErrorCode());
        // Observability: the diagnostic params must carry the task identity so the
        // two supervision error codes stay distinguishable on an ops dashboard.
        assertTrue(ex.getMessage().contains("sink-B-0"),
                "error message must carry the task key for diagnostics: " + ex.getMessage());
    }

    /**
     * A task that DOES reach a terminal state within the budget must NOT throw —
     * zero-regression for the healthy cancel path.
     */
    @Test
    void waitForTerminal_taskFinishes_doesNotThrow() {
        SubtaskTask task = buildMinimalNonTerminalTask();
        // Transition to a terminal state (CANCELED) to simulate a cooperative cancel
        // that completed within the budget.
        task.cancel();

        // Must not throw.
        SupervisionLoop.waitForTerminal(task, "sink-B-0", 1000L);
        assertTrue(task.isFinished());
    }

    private static void assertNotNullErrorCode(StreamException ex) {
        assertTrue(ex.getErrorCode() != null && !ex.getErrorCode().isEmpty(),
                "StreamException must carry an error code: " + ex);
    }

    /**
     * Builds a minimal {@link SubtaskTask} in the CREATED (non-terminal) state,
     * anchored on the {@code TestSupervisionLoopRestartLimitConfig} sink-vertex
     * fixture. {@code isFinished()} returns false until a terminal transition.
     */
    private static SubtaskTask buildMinimalNonTerminalTask() {
        SinkFunction<String> sinkFn = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
            }
        };
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sinkFn);
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);
        JobVertex sinkVertex = new JobVertex("sink-B", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        TaskLocation loc = new TaskLocation("zombie-job", "pipeline-0", "sink-B", 0);
        Subtask subtask = new Subtask("sink-B", 0, loc, sinkInvokable, null);
        return new SubtaskTask(subtask, sinkVertex, Collections.singletonList(sinkChain));
    }
}
