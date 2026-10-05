/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.ddl;

import io.nop.dao.shard.IShardSelector;
import io.nop.orm.model.IEntityModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * plan 2320 Phase 3：DDL N表展开的表名展开工具。
 *
 * <p><b>入口裁定（实现时裁定并保持DdlSqlCreator公共语义不变）</b>：无既有表名替换API。
 * 实体模型包装（clone视图）与JDK动态代理两条路均不可行——ddl.xlib模板经XPL反射访问具体类
 * 属性（uniqueKeys/compositePk等），且视图{@code init()}会向共享的frozen关系/列实例回写owner
 * （not-allow-change）。最终采用<b>显式物理表名入口</b>：DdlSqlCreator新增带
 * {@code physicalTableName}的重载，模板经{@code shardTableName ?? table.tableName}回退——
 * 不传时输出与既有方法逐字节一致。三个建表调用点共用本工具做表名全集展开。</p>
 *
 * <p>未绑定策略（或selector为null）的实体，{@link #shardTableNames} 返回空列表——
 * 调用点维持既有单表建表/升级行为（未配置分片体系零变更）。</p>
 */
public class ShardDdlHelper {

    private ShardDdlHelper() {
    }

    /**
     * 实体是否需要N表展开（绑定了可枚举分片名全集的策略）。
     */
    public static boolean isShardExpanded(IShardSelector selector, IEntityModel entityModel) {
        return !shardTableNames(selector, entityModel).isEmpty();
    }

    /**
     * 实体的分片物理表名全集（按策略的table-pattern规则）。未绑定策略/无分片名全集时返回空。
     */
    public static List<String> shardTableNames(IShardSelector selector, IEntityModel entityModel) {
        if (selector == null || entityModel == null || !entityModel.isUseShard())
            return Collections.emptyList();
        List<String> shardNames = selector.getShardNames(entityModel.getName());
        if (shardNames.isEmpty())
            return Collections.emptyList();
        List<String> ret = new ArrayList<>(shardNames.size());
        for (String shardName : shardNames) {
            ret.add(selector.getShardTableName(entityModel.getName(), entityModel.getTableName(), shardName));
        }
        return ret;
    }
}
