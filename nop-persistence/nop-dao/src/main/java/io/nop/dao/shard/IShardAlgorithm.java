/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.shard;

import java.util.List;

/**
 * 内置分片算法 SPI（plan 2320，设计文档 §3.3）。
 *
 * <p>算法只负责"分片值 → 分片名"的映射；表名规则与 querySpace 映射由
 * {@link ShardStrategy} 统一处理。null 或类型不兼容的分片值必须显式抛错，不允许静默返回。</p>
 */
public interface IShardAlgorithm {
    /**
     * @param shardProp  分片键列名
     * @param shardValue 分片键值（保证非null；类型兼容性由算法自行校验）
     * @return 分片名，非空
     */
    String selectShard(String shardProp, Object shardValue);

    /**
     * 分片名全集（DDL N表展开用）。无法静态枚举时抛
     * {@code ERR_DAO_SHARD_CONFIG_INVALID}。
     */
    List<String> shardNames();
}
