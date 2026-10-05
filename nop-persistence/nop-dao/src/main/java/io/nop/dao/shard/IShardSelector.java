/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.shard;

import java.util.Collections;
import java.util.List;

/**
 * 分片路由扩展点（nop-dao 模块内扩展点，不属 nop-*-api 公共 API）。
 *
 * <p>双轨制（plan 2320，设计文档 §一.2）：
 * <ul>
 * <li>配置轨：{@link ConfigShardSelector} 按 {@code nop.orm.shard} 配置构建内置算法注册表；</li>
 * <li>自定义轨：直接实现本接口并装配为 {@code nopShardSelector} bean（或同名覆盖），覆盖配置轨。</li>
 * </ul>
 *
 * <p>接口同时承载两类契约：
 * <ol>
 * <li>分片值 → {@link ShardSelection} 解析（{@link #selectShard}）；</li>
 * <li>缺分片值解析契约（{@link #getMissingShardPolicy} / {@link #selectShardWhenMissing}），
 * 消费方为 nop-orm 的 GenSqlTransformer。</li>
 * </ol>
 *
 * <p>plan 2320 死面移除：原 {@code isSupportShard} 与 {@code selectShards}（范围多分片）
 * 均无任何调用方，且范围路由只有配合跨分片 fan-out 执行才有意义（设计已拒），故从接口删除。
 */
public interface IShardSelector {
    /**
     * 根据查询对象名以及查询时指定的属性值来确定最终选择的shard
     *
     * @param entityName 实体名
     * @return 未配置该实体的分片策略时返回null（消费方维持既有基表名执行语义）
     */
    ShardSelection selectShard(String entityName, String shardProp, Object shardValue);

    /**
     * 是否加载了分片策略配置（配置轨）。用于启动期绑定校验的门控：
     * 无任何分片配置时不做校验，保证未配置分片体系的部署零行为变更。
     */
    default boolean hasStrategyConfig() {
        return false;
    }

    /**
     * 指定实体是否绑定了分片策略（显式绑定或default-strategy回落）。
     * 仅当 {@link #hasStrategyConfig()} 为true时语义才有意义。
     */
    default boolean isEntityBound(String entityName) {
        return false;
    }

    /**
     * 缺分片值策略。NONE表示该实体未绑定策略（消费方维持既有fall-through语义）。
     */
    default MissingShardPolicy getMissingShardPolicy(String entityName) {
        return MissingShardPolicy.NONE;
    }

    /**
     * 缺分片值时的回落分片。仅当 {@link #getMissingShardPolicy(String)} 返回
     * {@link MissingShardPolicy#DEFAULT_SHARD} 时才会被调用。
     *
     * @return 未配置default-shard时返回null
     */
    default ShardSelection selectShardWhenMissing(String entityName, String shardProp) {
        return null;
    }

    /**
     * 实体分片名全集（DDL N表展开用）。未绑定策略或无法静态枚举时返回空列表（不展开）。
     */
    default List<String> getShardNames(String entityName) {
        return Collections.emptyList();
    }

    /**
     * 分片物理表名规则。缺省为{@code {基表名}_{分片名}}。
     */
    default String getShardTableName(String entityName, String baseTableName, String shardName) {
        return baseTableName + '_' + shardName;
    }
}
