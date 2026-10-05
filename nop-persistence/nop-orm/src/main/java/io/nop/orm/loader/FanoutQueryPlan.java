/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.loader;

import io.nop.dao.shard.ShardSelection;

import java.util.List;

/**
 * fanout 查询执行计划（plan 2321 Phase 3）：资格判定通过后的一次性执行上下文。
 * 由 {@link ShardFanoutEngine#planQuery} 在 transform 之前产出，携带分片全集落点。
 */
public class FanoutQueryPlan {
    private final String entityName;
    private final List<ShardSelection> shards;

    public FanoutQueryPlan(String entityName, List<ShardSelection> shards) {
        this.entityName = entityName;
        this.shards = shards;
    }

    /**
     * fanout 绑定实体名（资格判定：单条 SQL 至多一个）。
     */
    public String getEntityName() {
        return entityName;
    }

    /**
     * 分片全集落点（含 querySpace），来自 IShardSelector.selectAllShards。
     */
    public List<ShardSelection> getShards() {
        return shards;
    }
}
