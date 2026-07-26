package io.nop.stream.core.graph;

import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionPolicyAware;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestPartitionPolicyInference {

    private final PartitionedPlanGenerator generator = new PartitionedPlanGenerator();

    @Test
    void testAwarePartitionerUsesDeclaredPolicy() {
        // Partitioner that explicitly declares HASH via PartitionPolicyAware.
        JobEdge edge = new JobEdge("source", "sink", ResultPartitionType.PIPELINED,
                new StubHashPartitioner());

        PartitionPolicy policy = generator.inferPartitionPolicy(edge);
        assertEquals(PartitionPolicy.HASH, policy,
                "PartitionPolicyAware partitioner must use its declared HASH policy");
    }

    @Test
    void testForwardStrategyWhenNoPartitioner() {
        JobEdge edge = new JobEdge("source", "sink", ResultPartitionType.PIPELINED);

        PartitionPolicy policy = generator.inferPartitionPolicy(edge);
        assertEquals(PartitionPolicy.FORWARD, policy,
                "Edge without partitioner should infer FORWARD policy");
    }

    @Test
    void testUnknownPartitionerFailsFast() {
        // After AR-3 fix: a partitioner that does NOT implement PartitionPolicyAware
        // must throw rather than silently default to FORWARD (or HASH by name match).
        JobEdge edge = new JobEdge("source", "sink", ResultPartitionType.PIPELINED,
                new StubUnknownPartitioner());

        StreamException ex = assertThrows(StreamException.class,
                () -> generator.inferPartitionPolicy(edge));
        assertTrue(ex.getMessage().contains("PartitionPolicyAware"),
                "Exception must explain the PartitionPolicyAware requirement");
    }

    @Test
    void testClassNameSubstringNoLongerMatches() {
        // The class name contains "Hash" but the partitioner does not implement
        // PartitionPolicyAware, so inference must fail-fast (no silent HASH by name).
        JobEdge edge = new JobEdge("source", "sink", ResultPartitionType.PIPELINED,
                new MyHashKeySelector());

        assertThrows(StreamException.class, () -> generator.inferPartitionPolicy(edge));
    }

    @Test
    void testForwardPartitionerDeclaresForwardPolicy() {
        // ForwardPartitioner now implements PartitionPolicyAware returning FORWARD.
        JobEdge edge = new JobEdge("source", "sink", ResultPartitionType.PIPELINED,
                new ForwardPartitioner());

        PartitionPolicy policy = generator.inferPartitionPolicy(edge);
        assertEquals(PartitionPolicy.FORWARD, policy,
                "ForwardPartitioner must declare FORWARD policy via PartitionPolicyAware");
    }

    private static class StubHashPartitioner implements IPartitioner<Object>, PartitionPolicyAware {
        @Override
        public int partition(Object key, int numPartitions) {
            return key == null ? 0 : Math.abs(key.hashCode() % numPartitions);
        }

        @Override
        public PartitionPolicy getPartitionPolicy() {
            return PartitionPolicy.HASH;
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
