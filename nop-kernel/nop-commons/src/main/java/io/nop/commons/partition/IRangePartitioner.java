/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.partition;

import java.util.List;

public interface IRangePartitioner<K> {
    List<PartitionResult<K>> partitionRange(K beginKey, K endKey, boolean includeEnd, int numPartitions);
}