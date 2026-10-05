/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.shard;

/**
 * 未配置分片体系时的缺省实现：所有实体均不做分片路由（selectShard返回null，
 * 消费方维持既有基表名执行语义），缺失值策略为NONE（不校验、不fail-fast）。
 *
 * <p>plan 2320 死面移除：随接口删除了 {@code isSupportShard} / {@code selectShards}。</p>
 */
public class EmptyShardSelector implements IShardSelector {
    public static final EmptyShardSelector INSTANCE = new EmptyShardSelector();

    @Override
    public ShardSelection selectShard(String entityName, String shardColumn, Object shardValue) {
        return null;
    }
}
