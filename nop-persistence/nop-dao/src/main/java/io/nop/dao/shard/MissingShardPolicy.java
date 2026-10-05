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
 * 缺分片值策略（plan 2320，设计文档 §3.4；第四取值 plan 2321，执行引擎设计 §3.8）。
 *
 * <p>useShard 实体的 SQL 不含分片键等值条件（或条件值为null）时的路由判定：
 * <ul>
 * <li>{@link #FAIL}：策略绑定实体的缺省策略，抛 {@code ERR_ORM_SHARD_CONDITION_MISSING}
 * （由消费方 nop-orm 的 GenSqlTransformer 抛出），取代现状"静默落回基表名执行"的陷阱行为；</li>
 * <li>{@link #DEFAULT_SHARD}：显式回落到 {@code default-shard} 指定的分片；</li>
 * <li>{@link #FANOUT}：按分片全集展开执行（执行引擎，plan 2321）。仅 SELECT 且满足引擎
 * 资格收口；非 SELECT 或越界形态归 {@code ERR_ORM_SHARD_FANOUT_NOT_SUPPORT}。全局开关
 * {@code nop.orm.shard.fanout-enabled} 关闭时运行期退化为 fail（不静默降级单分片）。
 * 策略泄漏进 transform 而引擎未接管时由 GenSqlTransformer 兜底显式失败；</li>
 * <li>{@link #NONE}：实体未绑定任何策略（未配置分片体系），维持既有 fall-through 语义，
 * 保证未配置分片体系的部署零行为变更。</li>
 * </ul>
 */
public enum MissingShardPolicy {
    NONE, FAIL, DEFAULT_SHARD, FANOUT;

    public static MissingShardPolicy parse(String text) {
        if (StringHelper.isEmpty(text))
            return FAIL;
        switch (text) {
            case "fail":
                return FAIL;
            case "default-shard":
                return DEFAULT_SHARD;
            case "fanout":
                return FANOUT;
            default:
                throw new IllegalArgumentException("unknown missing-shard-policy: " + text
                        + " (expected: fail | default-shard | fanout)");
        }
    }
}
