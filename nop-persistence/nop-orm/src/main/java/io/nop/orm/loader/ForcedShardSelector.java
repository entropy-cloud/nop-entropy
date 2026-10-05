/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.loader;

import io.nop.dao.shard.IShardSelector;
import io.nop.dao.shard.MissingShardPolicy;
import io.nop.dao.shard.ShardSelection;

import java.util.List;

/**
 * 每分片强制 selector（plan 2321，执行引擎设计 §3.2 评审 M5）：装饰 {@link IShardSelector}，
 * {@link #getMissingShardPolicy(String)} 返回 {@link MissingShardPolicy#DEFAULT_SHARD}、
 * {@link #selectShardWhenMissing(String, String)} 返回该分片的强制落点——骑
 * GenSqlTransformer 既有 default-shard 分支（addShardSelection + setQuerySpace），
 * per-shard transform 自然产出该分片的表名后缀与 querySpace。transform 路由链零改动。
 */
class ForcedShardSelector implements IShardSelector {
    private final IShardSelector delegate;
    private final ShardSelection forced;

    ForcedShardSelector(IShardSelector delegate, ShardSelection forced) {
        this.delegate = delegate;
        this.forced = forced;
    }

    @Override
    public ShardSelection selectShard(String entityName, String shardProp, Object shardValue) {
        // fanout 查询无分片值等值条件（资格收口保证），值路由主路径不参与；
        // 防御性委托保持既有语义
        return delegate.selectShard(entityName, shardProp, shardValue);
    }

    @Override
    public boolean hasStrategyConfig() {
        return delegate.hasStrategyConfig();
    }

    @Override
    public boolean isEntityBound(String entityName) {
        return delegate.isEntityBound(entityName);
    }

    @Override
    public MissingShardPolicy getMissingShardPolicy(String entityName) {
        // 强制转写为 DEFAULT_SHARD：缺分片值标记走既有 default-shard 回落分支
        return MissingShardPolicy.DEFAULT_SHARD;
    }

    @Override
    public ShardSelection selectShardWhenMissing(String entityName, String shardProp) {
        // 强制落点：本分片的 ShardSelection（querySpace + shardName 二元组）
        return forced;
    }

    @Override
    public List<String> getShardNames(String entityName) {
        return delegate.getShardNames(entityName);
    }

    @Override
    public List<ShardSelection> selectAllShards(String entityName) {
        return delegate.selectAllShards(entityName);
    }

    @Override
    public String getShardTableName(String entityName, String baseTableName, String shardName) {
        return delegate.getShardTableName(entityName, baseTableName, shardName);
    }
}
