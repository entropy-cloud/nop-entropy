/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.shard;

import java.util.Collections;
import java.util.List;

public class EmptyShardSelector implements IShardSelector {
    public static final EmptyShardSelector INSTANCE = new EmptyShardSelector();

    @Override
    public boolean isSupportShard(String entityName) {
        return false;
    }

    @Override
    public ShardSelection selectShard(String entityName, String shardColumn, Object shardValue) {
        return null;
    }

    @Override
    public List<ShardSelection> selectShards(String entityName, String shardColumn, Object beginValue,
                                             Object endValue) {
        return Collections.emptyList();
    }
}