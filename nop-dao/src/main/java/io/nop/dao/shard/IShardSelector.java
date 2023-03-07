/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.shard;

import java.util.List;

public interface IShardSelector {
    /**
     * 判断是否支持shard
     *
     * @param entityName 实体名
     */
    boolean isSupportShard(String entityName);

    /**
     * 根据查询对象名以及查询时指定的属性值来确定最终选择的shard
     *
     * @param entityName 实体名
     * @return
     */
    ShardSelection selectShard(String entityName, String shardProp, Object shardValue);

    List<ShardSelection> selectShards(String entityName, String shardProp, Object beginValue, Object endValue);
}