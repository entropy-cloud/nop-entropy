/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.graph;

import io.nop.commons.partition.IPartitioner;

/**
 * Marker implementation of {@link IPartitioner} representing forward (pointwise) partitioning.
 *
 * <p>Forward partitioning means data from one parallel instance is sent to the same
 * parallel instance of the downstream operator. This is the default partitioning strategy
 * and enables operator chaining.
 *
 * <p>Previously, forward partitioning was represented by {@code null} partitioner. This
 * class provides an explicit marker that:
 * <ul>
 *   <li>Can be distinguished from "no partitioner set"</li>
 *   <li>Is treated identically to {@code null} in chaining decisions</li>
 *   <li>Allows operators to explicitly document their partitioning strategy</li>
 * </ul>
 */
public class ForwardPartitioner implements IPartitioner<Object> {
    private static final long serialVersionUID = 1L;

    @Override
    public int partition(Object key, int numPartitions) {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ForwardPartitioner;
    }

    @Override
    public int hashCode() {
        return ForwardPartitioner.class.hashCode();
    }
}
