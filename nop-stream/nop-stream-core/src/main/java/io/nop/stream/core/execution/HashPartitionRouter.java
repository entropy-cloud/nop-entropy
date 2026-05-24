/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.streamrecord.StreamRecord;

/**
 * HASH routing: uses a stable hash of the record key to select a partition.
 * Falls back to {@link IPartitioner} if provided, otherwise hashes the record value.
 */
class HashPartitionRouter implements PartitionRouter {

    private final int numPartitions;
    private final IPartitioner<?> partitioner;

    HashPartitionRouter(int numPartitions, IPartitioner<?> partitioner) {
        this.numPartitions = numPartitions;
        this.partitioner = partitioner;
    }

    @Override
    @SuppressWarnings("unchecked")
    public int selectChannel(StreamRecord<?> record) {
        if (partitioner != null) {
            int channel = ((IPartitioner<Object>) partitioner).partition(record.getValue(), numPartitions);
            return Math.abs(channel % numPartitions);
        }
        // Default: hash the record value
        return Math.abs(record.getValue().hashCode() % numPartitions);
    }

    @Override
    public int getNumberOfPartitions() {
        return numPartitions;
    }
}
