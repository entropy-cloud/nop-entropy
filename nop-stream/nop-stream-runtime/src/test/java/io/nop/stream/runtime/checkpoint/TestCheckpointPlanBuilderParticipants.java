package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointPlan;
import io.nop.stream.core.execution.*;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointPlanBuilderParticipants {

    @Test
    void testAutoDetectGeneratesPerSubtaskParticipantIds() {
        JobVertex vertex = build2PCVertex("v1", 2);
        JobGraph jobGraph = new JobGraph("test-job");
        jobGraph.addVertex(vertex);

        GraphExecutionPlan plan = GraphExecutionPlan.build(jobGraph);
        CheckpointPlan cp = CheckpointPlan.build(plan, "job1", "pipe1");

        List<String> participants = cp.getCheckpointParticipants();
        assertEquals(2, participants.size());
        assertTrue(participants.contains("v1-0"));
        assertTrue(participants.contains("v1-1"));
    }

    @Test
    void testAutoDetectSingleParallelismParticipantId() {
        JobVertex vertex = build2PCVertex("v1", 1);
        JobGraph jobGraph = new JobGraph("test-job");
        jobGraph.addVertex(vertex);

        GraphExecutionPlan plan = GraphExecutionPlan.build(jobGraph);
        CheckpointPlan cp = CheckpointPlan.build(plan, "job1", "pipe1");

        List<String> participants = cp.getCheckpointParticipants();
        assertEquals(1, participants.size());
        assertEquals("v1-0", participants.get(0));
    }

    @Test
    void testBreakExitsAllChainsNotJustOperators() {
        List<OperatorChain> chains = new ArrayList<>();
        chains.add(new OperatorChain(Collections.singletonList(new Mock2PCOperator())));
        chains.add(new OperatorChain(Collections.singletonList(new Mock2PCOperator())));

        JobVertex vertex = new JobVertex("v1", "test", 1, chains, new MockInvokable());
        JobGraph jobGraph = new JobGraph("test-job");
        jobGraph.addVertex(vertex);

        GraphExecutionPlan plan = GraphExecutionPlan.build(jobGraph);
        CheckpointPlan cp = CheckpointPlan.build(plan, "job1", "pipe1");

        List<String> participants = cp.getCheckpointParticipants();
        assertEquals(1, participants.size());
    }

    private JobVertex build2PCVertex(String id, int parallelism) {
        List<OperatorChain> chains = new ArrayList<>();
        chains.add(new OperatorChain(Collections.singletonList(new Mock2PCOperator())));
        return new JobVertex(id, "test", parallelism, chains, new MockInvokable());
    }

    private static class Mock2PC extends TwoPhaseCommitSinkFunction<String> {
        @Override
        public void invoke(String value, Context context) throws Exception {}
    }

    private static class Mock2PCOperator extends AbstractUdfStreamOperator<String, Mock2PC> {
        Mock2PCOperator() {
            super(new Mock2PC());
        }
    }

    private static class MockInvokable implements Invokable<Void> {
        @Override
        public void invoke() throws Exception {}
    }
}
