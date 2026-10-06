/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.message.debezium.ChangeEvent;
import io.nop.orm.migration.TargetShardMapping.PhysicalTable;

import java.util.Map;

import static io.nop.orm.migration.MigrationErrors.ARG_ENTITY_ID;
import static io.nop.orm.migration.MigrationErrors.ARG_ENTITY_NAME;
import static io.nop.orm.migration.MigrationErrors.ARG_SOURCE_TABLE;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_NULL_SHARD_KEY;

/**
 * 增量 shard-router（设计 §4.4）：ChangeEvent 行内分片键值 → 新策略目标物理表。
 *
 * <p>输入是 after/key image 中的分片键值（c/u/r 取 after，d 取 key image），调用与新策略
 * {@code ConfigShardSelector} 同族的策略算法计算落点——只在搬迁管道内消费，不触碰运行期路由。
 * 分片键 null 无条件 fail-fast（m3，与 FULL 同契约）：错误进入流任务使其 FAILED
 * （R5 语义：collect 异常 → pendingError → 任务失败、offset 不前推）。</p>
 */
public class ShardChangeEventRouter {

    private final TargetShardMapping mapping;

    public ShardChangeEventRouter(TargetShardMapping mapping) {
        this.mapping = mapping;
    }

    /**
     * 事件路由到的目标物理表。
     */
    public PhysicalTable route(ChangeEvent event) {
        if (mapping.isRelocate())
            return new PhysicalTable("", mapping.getBaseTableName());

        Map<String, Object> image = shardImage(event);
        Object shardValue = image == null ? null : caseInsensitiveGet(image, mapping.getShardColumnName());
        if (shardValue == null) {
            // NopException 是 RuntimeException：直接抛出使流任务 FAILED（错误码与 FULL 一致）
            throw new NopException(ERR_MIG_NULL_SHARD_KEY)
                    .param(ARG_ENTITY_NAME, mapping.getEntityName())
                    .param(ARG_ENTITY_ID, displayKey(event))
                    .param(ARG_SOURCE_TABLE, sourceTableOf(event));
        }
        return mapping.routeShardValue(shardValue);
    }

    /**
     * 事件是否属于目标物理表（回退形态的 per-shard filter 谓词）。
     */
    public boolean belongsTo(ChangeEvent event, PhysicalTable target) {
        return target.equals(route(event));
    }

    private static Map<String, Object> shardImage(ChangeEvent event) {
        if ("d".equals(event.getOperation()))
            return event.getKey() != null ? event.getKey() : event.getBefore();
        return event.getAfter() != null ? event.getAfter() : event.getKey();
    }

    private static Object caseInsensitiveGet(Map<String, Object> row, String name) {
        if (name == null || row == null)
            return null;
        Object direct = row.get(name);
        if (direct != null)
            return direct;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name))
                return entry.getValue();
        }
        return null;
    }

    private static String displayKey(ChangeEvent event) {
        return event.getKey() == null ? "-" : String.valueOf(event.getKey());
    }

    private static String sourceTableOf(ChangeEvent event) {
        return event.getMetadata() == null ? "-" : String.valueOf(event.getMetadata().getTable());
    }
}
