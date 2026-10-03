/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointPlan;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.model.JoinType;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.operators.join.EquiJoinOperator;
import io.nop.stream.runtime.operators.windowing.OverWindowOperator;
import io.nop.stream.runtime.operators.windowing.WindowOperator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI21 (§八 13): every transactional (2PC) operator must be a
 * {@link CheckpointParticipant}. The 2PC discovery rule is
 * {@code CheckpointPlanBuilder}: an operator is marked 2PC iff its UDF is a
 * {@code TwoPhaseCommitSinkFunction} — and {@code TwoPhaseCommitSinkFunction
 * implements CheckpointParticipant}, so the assembly-level assertion is that a
 * plan containing a 2PC sink registers its participants ({vertexId}-{taskIndex})
 * in the checkpoint plan. The non-transactional operators (window / over-window /
 * CEP / equi-join) carry no 2PC UDF — their durable state rides the keyed-state
 * channel, not the participant channel; pinned by explicit class-level negative
 * checks (no reflective class scan — the operator set is small and enumerated).
 */
public class TestTransactionalOperatorIsCheckpointParticipant {

    public static class Mock2PC extends io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction<String> {
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
    }

    public static class Mock2PCOperator extends AbstractUdfStreamOperator<String, Mock2PC> {
        Mock2PCOperator() {
            super(new Mock2PC());
        }
    }

    public static class MockInvokable implements Invokable<Void> {
        @Override
        public void invoke() throws Exception {
        }
    }

    private JobVertex build2PCVertex(String id, int parallelism) {
        List<io.nop.stream.core.jobgraph.OperatorChain> chains = Collections.singletonList(
                new io.nop.stream.core.jobgraph.OperatorChain(
                        Collections.singletonList(new Mock2PCOperator())));
        return new JobVertex(id, "test", parallelism, chains, new MockInvokable());
    }

    @Test
    public void twoPhaseSinkPlanRegistersParticipants() {
        JobVertex vertex = build2PCVertex("v1", 2);
        JobGraph jobGraph = new JobGraph("wi21-2pc");
        jobGraph.addVertex(vertex);

        CheckpointPlan plan = CheckpointPlanBuilder.build(GraphExecutionPlan.build(jobGraph), "job1", "pipe1");
        List<String> participants = plan.getCheckpointParticipants();
        assertNotNull(participants);
        assertEquals(2, participants.size(), "one participant per subtask of the 2PC vertex");
        assertTrue(participants.contains("v1-0") && participants.contains("v1-1"),
                "participant keys are {vertexId}-{taskIndex}: " + participants);
    }

    @Test
    public void nonTransactionalOperatorsRideKeyedStateNotParticipants() {
        // class-level negatives — none of the four non-transactional operators
        // implements CheckpointParticipant (their durable state flows through the
        // keyed-state lineage in AbstractStreamOperator.snapshotState)
        assertFalse(CheckpointParticipant.class.isAssignableFrom(WindowOperator.class),
                "WindowOperator is not transactional");
        assertFalse(CheckpointParticipant.class.isAssignableFrom(OverWindowOperator.class),
                "OverWindowOperator is not transactional");
        assertFalse(CheckpointParticipant.class.isAssignableFrom(EquiJoinOperator.class),
                "EquiJoinOperator is not transactional");
        try {
            Class<?> cep = Class.forName("io.nop.stream.cep.operator.CepOperator");
            assertFalse(CheckpointParticipant.class.isAssignableFrom(cep),
                    "CepOperator is not transactional");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("CepOperator must be on the runtime test classpath", e);
        }
        // and the positive side by type: the 2PC base itself is a participant
        assertTrue(CheckpointParticipant.class
                .isAssignableFrom(io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction.class));
        // JoinType.isOuter stays untouched by this WI (WI8d audit M-2 anchor)
        assertTrue(JoinType.LEFT.isOuter());
        assertFalse(CheckpointParticipant.class.isAssignableFrom(AbstractStreamOperator.class),
                "the abstract operator base carries no participant semantics");
    }
}
