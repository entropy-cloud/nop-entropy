/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.partition;

public interface IPartitioner<K> {
    /**
     * Computes the partition for the given key.
     *
     * @param key           The key.
     * @param numPartitions The number of partitions to partition into.
     * @return The partition index.
     */
    int partition(K key, int numPartitions);
}