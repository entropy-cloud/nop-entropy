package io.nop.stream.core.graph;

import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestPartitionedPlanGeneratorGetName {

    private final PartitionedPlanGenerator generator = new PartitionedPlanGenerator();

    @Test
    void testInferPartitionPolicyUsesClassNameNotSimpleName() {
        IPartitioner<Object> anonymousHash = new IPartitioner<Object>() {
            @Override
            public int partition(Object record, int numPartitions) {
                return 0;
            }
        };

        JobEdge edge = new JobEdge("src", "tgt", ResultPartitionType.PIPELINED, anonymousHash);

        PartitionPolicy policy = generator.inferPartitionPolicy(edge);
        assertEquals(PartitionPolicy.FORWARD, policy);
    }

    @Test
    void testInferPartitionPolicyWithNamedHashPartitioner() {
        class HashPartitioner implements IPartitioner<Object> {
            @Override
            public int partition(Object record, int numPartitions) {
                return 0;
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
