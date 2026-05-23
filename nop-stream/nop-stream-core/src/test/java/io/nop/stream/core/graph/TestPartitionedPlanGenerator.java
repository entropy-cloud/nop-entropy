package io.nop.stream.core.graph;

import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.jobgraph.*;
import io.nop.stream.core.operators.StreamMap;
import io.nop.stream.core.common.functions.MapFunction;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestPartitionedPlanGenerator {

    @Test
    void testNullJobGraphThrows() {
        PartitionedPlanGenerator generator = new PartitionedPlanGenerator();
        assertThrows(IllegalArgumentException.class, () -> generator.generate(null, null));
    }

    @Test
    void testGenerateFromJobGraph() {
        JobGraph jobGraph = new JobGraph("test-job");

        StreamMap<String, String> op = new StreamMap<>((MapFunction<String, String>) x -> x);
        OperatorChain chain = new OperatorChain(Collections.singletonList(op));
        Invokable<?> invokable = () -> {};

        JobVertex source = new JobVertex("source", "Source Op", 1,
                Collections.singletonList(chain), invokable);
        JobVertex sink = new JobVertex("sink", "Sink Op", 1,
                Collections.singletonList(chain), invokable);
        jobGraph.addVertex(source);
        jobGraph.addVertex(sink);
        jobGraph.addEdge(new JobEdge("source", "sink", ResultPartitionType.PIPELINED));

        PartitionedPlanGenerator generator = new PartitionedPlanGenerator();
        PartitionedPlan plan = generator.generate(jobGraph, null);

        assertEquals("test-job", plan.getJobId());
        assertEquals(2, plan.getVertexPlans().size());
        assertEquals(1, plan.getEdgePlans().size());
        assertEquals(PartitionPolicy.FORWARD, plan.getEdgePlans().get(0).getPartitionPolicy());
        assertTrue(plan.getCheckpointAckSet().contains("source"));
    }
}
