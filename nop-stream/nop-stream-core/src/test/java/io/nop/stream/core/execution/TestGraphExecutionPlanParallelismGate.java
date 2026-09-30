/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.common.functions.source.ParallelismCheckable;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.StreamSourceOperator;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 368 Phase 4 (audit R5-CON-05): the deployment-time parallelism gate. A source
 * function implementing {@link ParallelismCheckable} must be consulted at plan build
 * time with the vertex's effective parallelism; a function without per-subtask sharding
 * fails the deployment loudly instead of duplicating its full dataset once per subtask.
 */
class TestGraphExecutionPlanParallelismGate {

    /** Source that cannot honor parallelism > 1 (the batch-loader shape). */
    static class SingleWriterSource implements SourceFunction<String>, ParallelismCheckable {
        private static final long serialVersionUID = 1L;

        int reportedParallelism = -1;

        @Override
        public void run(SourceContext<String> ctx) {
        }

        @Override
        public void cancel() {
        }

        @Override
        public void validateParallelism(int parallelism) {
            reportedParallelism = parallelism;
            if (parallelism > 1) {
                throw new StreamException("single-writer source does not support parallelism "
                        + parallelism);
            }
        }
    }

    private static JobGraph graphWithSource(int parallelism, SingleWriterSource source) {
        OperatorChain chain = new OperatorChain(
                Collections.singletonList(new StreamSourceOperator<>(source)));
        JobVertex vertex = new JobVertex("src", "src", parallelism,
                Collections.singletonList(chain), (Invokable<Void>) () -> {
                });
        JobGraph graph = new JobGraph("parallelism-gate-test");
        graph.addVertex(vertex);
        return graph;
    }

    @Test
    void testPlanBuildRejectsParallelismAboveOne() {
        SingleWriterSource source = new SingleWriterSource();
        JobGraph graph = graphWithSource(2, source);

        StreamException e = assertThrows(StreamException.class, () -> GraphExecutionPlan.build(graph),
                "plan build must fail fast for a source that rejects parallelism > 1");
        assertTrue(String.valueOf(e.getMessage()).contains("single-writer source"),
                "failure must name the offending source constraint");
        assertEquals(2, source.reportedParallelism,
                "the gate must be consulted with the vertex's effective parallelism");
    }

    @Test
    void testPlanBuildAllowsParallelismOne() {
        SingleWriterSource source = new SingleWriterSource();
        JobGraph graph = graphWithSource(1, source);

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);
        assertEquals(1, plan.getSubtasks("src").size());
    }
}
