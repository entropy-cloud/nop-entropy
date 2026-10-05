/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.shard;

import io.nop.commons.util.StringHelper;

/**
 * 缺分片值策略（plan 2320，设计文档 §3.4）。
 *
 * <p>useShard 实体的 SQL 不含分片键等值条件（或条件值为null）时的路由判定：
 * <ul>
 * <li>{@link #FAIL}：策略绑定实体的缺省策略，抛 {@code ERR_ORM_SHARD_CONDITION_MISSING}
 * （由消费方 nop-orm 的 GenSqlTransformer 抛出），取代现状"静默落回基表名执行"的陷阱行为；</li>
 * <li>{@link #DEFAULT_SHARD}：显式回落到 {@code default-shard} 指定的分片；</li>
 * <li>{@link #NONE}：实体未绑定任何策略（未配置分片体系），维持既有 fall-through 语义，
 * 保证未配置分片体系的部署零行为变更。</li>
 * </ul>
 */
public enum MissingShardPolicy {
    NONE, FAIL, DEFAULT_SHARD;

    public static MissingShardPolicy parse(String text) {
        if (StringHelper.isEmpty(text))
            return FAIL;
        switch (text) {
            case "fail":
                return FAIL;
            case "default-shard":
                return DEFAULT_SHARD;
            default:
                throw new IllegalArgumentException("unknown missing-shard-policy: " + text
                        + " (expected: fail | default-shard)");
        }
    }
}
