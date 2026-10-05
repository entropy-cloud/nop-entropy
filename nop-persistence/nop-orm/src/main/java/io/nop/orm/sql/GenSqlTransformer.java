/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql;

import io.nop.commons.text.marker.IMarkedString;
import io.nop.commons.text.marker.MarkedString;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SyntaxMarker;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.IDialectProvider;
import io.nop.dao.shard.IShardSelector;
import io.nop.dao.shard.MissingShardPolicy;
import io.nop.dao.shard.ShardSelection;
import io.nop.dao.utils.DaoHelper;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.OrmErrors.ARG_COUNT;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_EXPECTED;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ARG_QUERY_SPACE;
import static io.nop.orm.OrmErrors.ARG_SQL;
import static io.nop.orm.OrmErrors.ERR_ORM_NOT_SUPPORT_MULTIPLE_QUERY_SPACE_IN_ONE_SQL;
import static io.nop.orm.OrmErrors.ERR_ORM_SHARD_CONDITION_MISSING;
import static io.nop.orm.OrmErrors.ERR_ORM_SQL_PARAM_COUNT_MISMATCH;

public class GenSqlTransformer {
    private final IOrmModel ormModel;
    private final IDialectProvider dialectProvider;
    private final IShardSelector shardSelector;

    private final ISqlCompileTool sqlCompiler;
    private final IEntityFilterProvider filterProvider;

    private Map<SyntaxMarker, ShardSelection> shardSelections;

    private String querySpace;

    /**
     * 先确定sql语句对应的querySpace，然后再从dialectProvider中获取dialect
     */
    private IDialect dialect;

    public GenSqlTransformer(IShardSelector shardSelector, IOrmModel ormModel,
                             IDialectProvider dialectProvider,
                             ISqlCompileTool sqlCompiler,
                             IEntityFilterProvider filterProvider) {
        this.shardSelector = shardSelector;
        this.ormModel = ormModel;
        this.dialectProvider = dialectProvider;
        this.sqlCompiler = sqlCompiler;
        this.filterProvider = filterProvider;
    }

    /**
     * 1. 将sql中的参数值替换为paramValues中给定的值，并按照要求进行类型转换 2. 识别所有的数据库表，根据shard配置将其转换为分区表名 3.
     * 对于启用了revision配置的表，自动增加revEndVer过滤条件 4. 对于启用了租户支持的表，自动增加租户过滤条件
     *
     * @param sql
     * @param paramValues
     * @return
     */
    public SQL.SqlBuilder transform(SQL sql, List<Object> paramValues) {

        validateParamCount(sql, paramValues);

        // plan 2320：分片值提取依赖paramValues（marker只携静态信息），须在校验后收集
        collectShardSelections(sql, paramValues);

        final SQL.SqlBuilder sb = SQL.begin();
        sb.name(sql.getName()).append(sql);

        sb.changeMarkerValues(paramValues);

        sb.transformMarker(marker -> {
            if (marker instanceof SyntaxMarker) {
                SyntaxMarker syntaxMarker = (SyntaxMarker) marker;
                switch (syntaxMarker.getType()) {
                    case TABLE:
                        return transformTable(syntaxMarker, sql);
                    case FILTER:
                        return transformFilter(syntaxMarker, sql);
                }
            }
            return null;
        });

        sb.querySpace(querySpace);
        return sb;
    }

    /**
     * plan 2320 Phase 2（取值语义重定义）：CompiledSql经QueryPlanCache跨执行共享，TABLE marker
     * 只携带静态信息——shardParamIndex为执行期paramValues的下标（ShardMarkerInjector编译期写入），
     * shardValue仅承载编译期字面量。执行期值一律从paramValues/marker现场取得，不落缓存。
     *
     * <p>缺分片值时按绑定策略的missing-shard-policy裁决：fail（缺省）抛
     * ERR_ORM_SHARD_CONDITION_MISSING；default-shard回落；NONE（实体未绑定策略，未配置
     * 分片体系）维持既有fall-through语义——未配置部署零行为变更。</p>
     */
    private void collectShardSelections(SQL sql, List<Object> paramValues) {
        sql.getMarkers().forEach(marker -> {
            if (marker instanceof SyntaxMarker) {
                SyntaxMarker syntaxMarker = (SyntaxMarker) marker;
                if (syntaxMarker.getType() == SyntaxMarker.SyntaxMarkerType.TABLE) {
                    // 有可能不是对应于实体对象
                    if (syntaxMarker.getEntityName() == null)
                        return;

                    IEntityModel entityModel = getEntityModel(syntaxMarker);
                    if (entityModel.isUseShard() && entityModel.getShardColumn() != null) {
                        String shardProp = entityModel.getShardColumn().getName();
                        Object shardValue = resolveShardValue(syntaxMarker, paramValues);

                        if (shardValue != null) {
                            ShardSelection shard = shardSelector.selectShard(entityModel.getName(), shardProp,
                                    shardValue);

                            if (shard != null) {
                                addShardSelection(syntaxMarker, shard);
                                setQuerySpace(shard.getQuerySpace(), sql);
                                return;
                            }
                            // selector返回null：该实体未绑定策略（如EmptyShardSelector），
                            // 维持既有基表名执行语义（零变更）
                        } else {
                            // 缺分片值：按绑定策略裁决
                            MissingShardPolicy policy = shardSelector.getMissingShardPolicy(entityModel.getName());
                            if (policy == MissingShardPolicy.FAIL) {
                                throw new OrmException(ERR_ORM_SHARD_CONDITION_MISSING)
                                        .param(ARG_ENTITY_NAME, entityModel.getName())
                                        .param(ARG_PROP_NAME, shardProp)
                                        .param(ARG_SQL, sql);
                            } else if (policy == MissingShardPolicy.DEFAULT_SHARD) {
                                ShardSelection shard = shardSelector.selectShardWhenMissing(entityModel.getName(),
                                        shardProp);
                                if (shard != null) {
                                    addShardSelection(syntaxMarker, shard);
                                    setQuerySpace(shard.getQuerySpace(), sql);
                                    return;
                                }
                                // 声明了default-shard策略但无法解析回落分片：显式失败而非静默跳过
                                throw new OrmException(ERR_ORM_SHARD_CONDITION_MISSING)
                                        .param(ARG_ENTITY_NAME, entityModel.getName())
                                        .param(ARG_PROP_NAME, shardProp)
                                        .param(ARG_SQL, sql);
                            }
                            // NONE：实体未绑定策略，维持既有fall-through
                        }
                    }
                    setQuerySpace(entityModel.getQuerySpace(), sql);
                }
            }
        });
        this.dialect = dialectProvider.getDialectForQuerySpace(querySpace);
    }

    /**
     * 从marker静态信息与执行期paramValues解析分片值。旧实现从marker列表按下标取Marker对象的
     * 语义是坏的（marker下标不是参数下标，且跨执行缓存下值不可缓存），已重定义。
     */
    private Object resolveShardValue(SyntaxMarker syntaxMarker, List<Object> paramValues) {
        if (syntaxMarker.getShardValue() != null)
            return syntaxMarker.getShardValue();
        int index = syntaxMarker.getShardParamIndex();
        if (index >= 0 && paramValues != null && index < paramValues.size())
            return paramValues.get(index);
        return null;
    }

    void addShardSelection(SyntaxMarker marker, ShardSelection shard) {
        if (this.shardSelections == null)
            shardSelections = new HashMap<>();
        shardSelections.put(marker, shard);
    }

    public IMarkedString transformTable(SyntaxMarker marker, IMarkedString sql) {
        ShardSelection shard = selectShard(marker);
        if (shard != null) {
            if (shard.getShardName() != null) {
                // {原表名}_{shardName}
                String tableName = marker.getMarkedText(sql.getText());
                tableName = dialect.unescapeSQLName(tableName);
                tableName = dialect.normalizeTableName(tableName + '_' + shard.getShardName());
                return new MarkedString(tableName);
            }
        }
        return null;
    }


    private IMarkedString transformFilter(SyntaxMarker marker, SQL sql) {
        // 缺省情况下自动删除filter marker
        if (filterProvider == null)
            return MarkedString.EMPTY;
        return filterProvider.getEntityFilter(marker, sql, sqlCompiler);
    }

    private IEntityModel getEntityModel(SyntaxMarker marker) {
        return ormModel.requireEntityModel(marker.getEntityName());
    }

    private ShardSelection selectShard(SyntaxMarker marker) {
        if (shardSelections == null)
            return null;

        ShardSelection shard = shardSelections.get(marker);
        return shard;
    }

    private void setQuerySpace(String querySpace, SQL sql) {
        querySpace = DaoHelper.normalizeQuerySpace(querySpace);
        if (this.querySpace == null) {
            this.querySpace = querySpace;
        } else if (!this.querySpace.equals(querySpace)) {
            throw new OrmException(ERR_ORM_NOT_SUPPORT_MULTIPLE_QUERY_SPACE_IN_ONE_SQL).param(ARG_SQL, sql)
                    .param(ARG_QUERY_SPACE, querySpace);
        }
    }

    void validateParamCount(SQL sql, List<Object> paramValues) {
        int paramCount = paramValues == null ? 0 : paramValues.size();
        int expectCount = sql.getValueMarkerCount();
        if (paramCount != expectCount)
            throw new OrmException(ERR_ORM_SQL_PARAM_COUNT_MISMATCH).param(ARG_SQL, sql)
                    .param(ARG_COUNT, paramCount).param(ARG_EXPECTED, expectCount);
    }
}
