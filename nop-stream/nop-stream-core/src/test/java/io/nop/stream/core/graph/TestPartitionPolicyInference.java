package io.nop.stream.core.graph;

import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.jobgraph.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestPartitionPolicyInference {

    private final PartitionedPlanGenerator generator = new PartitionedPlanGenerator();

    @Test
    void testHashStrategyInferredFromPartitionerName() {
        JobEdge edge = new JobEdge("source", "sink", ResultPartitionType.PIPELINED,
                new StubHashPartitioner());

        PartitionPolicy policy = generator.inferPartitionPolicy(edge);
        assertEquals(PartitionPolicy.HASH, policy,
                "Partitioner with 'hash' in class name should infer HASH policy");
    }

    @Test
    void testForwardStrategyWhenNoPartitioner() {
        JobEdge edge = new JobEdge("source", "sink", ResultPartitionType.PIPELINED);

        PartitionPolicy policy = generator.inferPartitionPolicy(edge);
        assertEquals(PartitionPolicy.FORWARD, policy,
                "Edge without partitioner should infer FORWARD policy");
    }

    @Test
    void testUnknownPartitionerFallsBackToForward() {
        JobEdge edge = new JobEdge("source", "sink", ResultPartitionType.PIPELINED,
                new StubUnknownPartitioner());

        PartitionPolicy policy = generator.inferPartitionPolicy(edge);
        assertEquals(PartitionPolicy.FORWARD, policy,
                "Unknown partitioner class name should fallback to FORWARD policy");
    }

    @Test
    void testHashSubstringMatchInClassName() {
        JobEdge edge = new JobEdge("source", "sink", ResultPartitionType.PIPELINED,
                new MyHashKeySelector());

        PartitionPolicy policy = generator.inferPartitionPolicy(edge);
        assertEquals(PartitionPolicy.HASH, policy,
                "Class name containing 'hash' substring should match HASH policy");
    }

    private static class StubHashPartitioner implements IPartitioner<Object> {
        @Override
        public int partition(Object key, int numPartitions) {
            return key == null ? 0 : Math.abs(key.hashCode() % numPartitions);
        }
    }

    private static class StubUnknownPartitioner implements IPartitioner<Object> {
        @Override
        public int partition(Object key, int numPartitions) {
            return 0;
        }
    }

    private static class MyHashKeySelector implements IPartitioner<Object> {
        @Override
        public int partition(Object key, int numPartitions) {
            return key == null ? 0 : Math.abs(key.hashCode() % numPartitions);
        }
    }
}
