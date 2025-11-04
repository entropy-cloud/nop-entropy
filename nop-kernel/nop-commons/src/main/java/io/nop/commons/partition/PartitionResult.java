/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.partition;

public class PartitionResult<K> {
    private final K beginKey;
    private final K endKey;
    private final boolean includeEnd;
    private final int partition;

    public PartitionResult(K beginKey, K endKey, boolean includeEnd, int partition) {
        this.beginKey = beginKey;
        this.endKey = endKey;
        this.includeEnd = includeEnd;
        this.partition = partition;
    }

    public K getBeginKey() {
        return beginKey;
    }

    public K getEndKey() {
        return endKey;
    }

    public boolean isIncludeEnd() {
        return includeEnd;
    }

    public int getPartition() {
        return partition;
    }
}
