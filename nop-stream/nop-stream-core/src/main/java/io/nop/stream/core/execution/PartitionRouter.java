/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.common.state.shard.StateShard;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.streamrecord.StreamRecord;

/**
 * Strategy interface for selecting which output partition to send a record to.
 *
 * <p>Three built-in strategies:
 * <ul>
 *   <li>{@link PartitionPolicy#FORWARD}: one-to-one, sourceSubtask i sends to targetSubtask i</li>
 *   <li>{@link PartitionPolicy#HASH}: hash by key, routed via {@link StateShard#stableHash}</li>
 *   <li>{@link PartitionPolicy#REBALANCE}: round-robin across all downstream subtasks</li>
 * </ul>
 */
public interface PartitionRouter {

    /**
     * Select the target partition index for the given record.
     *
     * @param record the record to route
     * @return partition index in [0, numPartitions)
     */
    int selectChannel(StreamRecord<?> record);

    /**
     * Returns the number of output partitions this router manages.
     */
    int getNumberOfPartitions();

    // ---- Factory ----

    static PartitionRouter create(PartitionPolicy policy, int numPartitions,
                                  IPartitioner<?> partitioner, int sourceSubtaskIndex) {
        if (policy == null) {
            policy = PartitionPolicy.FORWARD;
        }
        switch (policy) {
            case HASH:
                return new HashPartitionRouter(numPartitions, partitioner);
            case REBALANCE:
                return new RebalancePartitionRouter(numPartitions, sourceSubtaskIndex);
            case FORWARD:
            default:
                return new ForwardPartitionRouter(numPartitions);
        }
    }
}
