/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.concurrent.atomic.AtomicInteger;

import io.nop.stream.core.streamrecord.StreamRecord;

/**
 * REBALANCE routing: round-robin across all downstream partitions.
 * Each source subtask maintains its own round-robin counter.
 */
class RebalancePartitionRouter implements PartitionRouter {

    private final int numPartitions;
    private final AtomicInteger roundRobinCounter;

    RebalancePartitionRouter(int numPartitions, int sourceSubtaskIndex) {
        this.numPartitions = numPartitions;
        // Start from the source subtask index to distribute initial load
        this.roundRobinCounter = new AtomicInteger(sourceSubtaskIndex);
    }

    @Override
    public int selectChannel(StreamRecord<?> record) {
        return Math.abs(roundRobinCounter.getAndIncrement() % numPartitions);
    }

    @Override
    public int getNumberOfPartitions() {
        return numPartitions;
    }
}
