package io.nop.stream.core.graph;

import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionPolicyAware;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestPartitionedPlanGeneratorGetName {

    private final PartitionedPlanGenerator generator = new PartitionedPlanGenerator();

    @Test
    void testInferPartitionPolicyFailsForAnonymousNonAwarePartitioner() {
        // After AR-3 fix: anonymous IPartitioner that does NOT implement
        // PartitionPolicyAware must fail-fast (no silent FORWARD by name).
        IPartitioner<Object> anonymous = new IPartitioner<Object>() {
            @Override
            public int partition(Object record, int numPartitions) {
                return 0;
            }
        };

        JobEdge edge = new JobEdge("src", "tgt", ResultPartitionType.PIPELINED, anonymous);

        assertThrows(StreamException.class, () -> generator.inferPartitionPolicy(edge));
    }

    @Test
    void testInferPartitionPolicyWithAwareHashPartitioner() {
        // A partitioner whose class name contains "Hash" but which now correctly
        // implements PartitionPolicyAware to declare HASH.
        class HashPartitioner implements IPartitioner<Object>, PartitionPolicyAware {
            @Override
            public int partition(Object record, int numPartitions) {
                return 0;
            }

            @Override
            public PartitionPolicy getPartitionPolicy() {
                return PartitionPolicy.HASH;
            }
        }

        JobEdge edge = new JobEdge("src", "tgt", ResultPartitionType.PIPELINED, new HashPartitioner());

        PartitionPolicy policy = generator.inferPartitionPolicy(edge);
        assertEquals(PartitionPolicy.HASH, policy);
    }

    @Test
    void testInferPartitionPolicyWithNullPartitioner() {
        JobEdge edge = new JobEdge("src", "tgt", ResultPartitionType.PIPELINED);
        PartitionPolicy policy = generator.inferPartitionPolicy(edge);
        assertEquals(PartitionPolicy.FORWARD, policy);
    }
}
