/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.partition;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

public class HashPartitioner<T> implements IPartitioner<T> {
    public static final HashPartitioner<Object> INSTANCE = new HashPartitioner<>();

    @Override
    public int partition(T key, int numPartitions) {
        if (key == null)
            return 0;
        int value = Hashing.murmur3_32_fixed().hashString(key.toString(), Charsets.UTF_8).asInt();
        return Math.abs(value % numPartitions);
    }
}