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
import io.nop.stream.core.streamrecord.StreamRecord;

/**
 * FORWARD routing: selects the partition at the same index as the source subtask.
 * When source and target parallelism are equal (1:1), each source sends to exactly one target.
 * When parallelism differs, maps source subtask index into the target partition space via modulo.
 */
class ForwardPartitionRouter implements PartitionRouter {

    private final int numPartitions;

    ForwardPartitionRouter(int numPartitions) {
        this.numPartitions = numPartitions;
    }

    @Override
    public int selectChannel(StreamRecord<?> record) {
        // FORWARD always uses partition 0 for local single-edge semantics.
        // For multi-partition FORWARD, the GraphExecutionPlan wires each source subtask
        // to exactly one target partition per edge, so there is only 1 partition in the array.
        return 0;
    }

    @Override
    public int getNumberOfPartitions() {
        return numPartitions;
    }
}
