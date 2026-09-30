/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph;

import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSinkOperator;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Plan 368 Phase 4 (audit R5-CON-01): the task-identity pipeline. The identity-aware
 * {@code copyForSubtask(TaskLocation)} / {@code deepCopy(TaskLocation)} overloads carry
 * the deployment {@code TaskLocation} (jobId + vertexId + taskIndex) into 2PC sink
 * copies, so sinks that key external state by job/vertex identity (JDBC ledger
 * namespace) receive it; the index-only dispatch keeps index-only subclasses unchanged.
 */
class TestTaskIdentityPipeline {

    /** Recording 2PC sink: captures how the identity pipeline dispatched the copy. */
    static class RecordingTwoPhaseSink extends TwoPhaseCommitSinkFunction<String> {
        private static final long serialVersionUID = 1L;

        TaskLocation lastLocation;
        int lastIndex = -1;

        @Override
        public void beginTransaction() {
        }

        @Override
        public void invoke(String value) {
        }

        @Override
        public void preCommit(long checkpointId) {
        }

        @Override
        public void commit(long checkpointId) {
        }

        @Override
        public void rollback() {
        }

        @Override
        public TwoPhaseCommitSinkFunction<String> copyForSubtask(int subtaskIndex) {
            RecordingTwoPhaseSink copy = new RecordingTwoPhaseSink();
            copy.lastIndex = subtaskIndex;
            return copy;
        }

        @Override
        public TwoPhaseCommitSinkFunction<String> copyForSubtask(TaskLocation location) {
            RecordingTwoPhaseSink copy = new RecordingTwoPhaseSink();
            copy.lastLocation = location;
            copy.lastIndex = location.getTaskIndex();
            return copy;
        }
    }

    /** Index-only 2PC sink: only overrides the int variant (file-sink dispatch shape). */
    static class IndexOnlyTwoPhaseSink extends TwoPhaseCommitSinkFunction<String> {
        private static final long serialVersionUID = 1L;

        int lastIndex = -1;

        @Override
        public void beginTransaction() {
        }

        @Override
        public void invoke(String value) {
        }

        @Override
        public void preCommit(long checkpointId) {
        }

        @Override
        public void commit(long checkpointId) {
        }

        @Override
        public void rollback() {
        }

        @Override
        public TwoPhaseCommitSinkFunction<String> copyForSubtask(int subtaskIndex) {
            IndexOnlyTwoPhaseSink copy = new IndexOnlyTwoPhaseSink();
            copy.lastIndex = subtaskIndex;
            return copy;
        }
    }

    @Test
    void testDeepCopyWithLocationForwardsIdentityIntoSinkCopy() {
        RecordingTwoPhaseSink sink = new RecordingTwoPhaseSink();
        OperatorChain chain = new OperatorChain(
                Collections.singletonList(new StreamSinkOperator<>((SinkFunction<String>) sink)));

        TaskLocation location = new TaskLocation("job-9", "pipeline-0", "sink-vertex", 2);
        OperatorChain copy = chain.deepCopy(location);

        assertNotSame(chain, copy);
        StreamOperator<?> copiedOp = copy.getOperators().get(0);
        assertSame(RecordingTwoPhaseSink.class, ((StreamSinkOperator<?>) copiedOp).getUserFunction().getClass());
        RecordingTwoPhaseSink copiedSink = (RecordingTwoPhaseSink) ((StreamSinkOperator<?>) copiedOp).getUserFunction();
        assertEquals("job-9", copiedSink.lastLocation.getJobId());
        assertEquals("sink-vertex", copiedSink.lastLocation.getVertexId());
        assertEquals(2, copiedSink.lastIndex);
    }

    @Test
    void testLocationVariantFallsBackToIndexDispatchForIndexOnlySinks() {
        IndexOnlyTwoPhaseSink sink = new IndexOnlyTwoPhaseSink();
        StreamSinkOperator<String> op = new StreamSinkOperator<>((SinkFunction<String>) sink);

        TaskLocation location = new TaskLocation("job-9", "pipeline-0", "file-sink", 1);
        StreamSinkOperator<?> copy = (StreamSinkOperator<?>) op.copyForSubtask(location);

        IndexOnlyTwoPhaseSink copiedSink = (IndexOnlyTwoPhaseSink) copy.getUserFunction();
        assertNotSame(sink, copiedSink);
        assertEquals(1, copiedSink.lastIndex,
                "index-only 2PC sinks must keep receiving the int-variant dispatch");
    }
}
