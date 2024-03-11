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
import io.nop.dao.shard.ShardSelection;
import io.nop.dao.utils.DaoHelper;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.OrmErrors.ARG_COUNT;
import static io.nop.orm.OrmErrors.ARG_EXPECTED;
import static io.nop.orm.OrmErrors.ARG_QUERY_SPACE;
import static io.nop.orm.OrmErrors.ARG_SQL;
import static io.nop.orm.OrmErrors.ERR_ORM_NOT_SUPPORT_MULTIPLE_QUERY_SPACE_IN_ONE_SQL;
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

        collectShardSelections(sql);

        final SQL.SqlBuilder sb = SQL.begin();
        sb.name(sql.getName()).append(sql);

        this.validateParamCount(sql, paramValues);

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

    private void collectShardSelections(SQL sql) {
        sql.getMarkers().forEach(marker -> {
            if (marker instanceof SyntaxMarker) {
                SyntaxMarker syntaxMarker = (SyntaxMarker) marker;
                if (syntaxMarker.getType() == SyntaxMarker.SyntaxMarkerType.TABLE) {
                    // 有可能不是对应于实体对象
                    if (syntaxMarker.getEntityName() == null)
                        return;

                    IEntityModel entityModel = getEntityModel(syntaxMarker);
                    if (entityModel.isUseShard() && entityModel.getShardColumn() != null) {
                        if (syntaxMarker.hasShardParam()) {
                            Object shardValue;
                            if (syntaxMarker.getShardParamIndex() > 0) {
                                shardValue = sql.getMarker(syntaxMarker.getShardParamIndex());
                            } else {
                                shardValue = syntaxMarker.getShardValue();
                            }
                            String shardProp = entityModel.getShardColumn().getName();
                            ShardSelection shard = shardSelector.selectShard(entityModel.getName(), shardProp,
                                    shardValue);

                            if (shard != null) {
                                addShardSelection(syntaxMarker, shard);
                                setQuerySpace(shard.getQuerySpace(), sql);
                                return;
                            }
                        }
                    }
                    setQuerySpace(entityModel.getQuerySpace(), sql);
                }
            }
        });
        this.dialect = dialectProvider.getDialectForQuerySpace(querySpace);
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
                tableName = dialect.escapeSQLName(tableName + '_' + shard.getShardName());
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

    //
    // private IMarkedString transformFilter(SyntaxMarker marker, SQL sql) {
    // IEntityModel entityModel = getEntityModel(marker);
    // SQL.SqlBuilder sb = null;
    // if (entityModel.isUseTenant()) {
    // if (sb == null) {
    // sb = SQL.begin();
    // }
    //
    // IColumnModel col = entityModel.getColumnByPropId(entityModel.getTenantPropId(), true);
    // sb.and();
    // GenSqlHelper.appendEq(sb, dialect, marker.getAlias(), col, null, ContextProvider.currentTenantId());
    // }
    //
    // if (entityModel.isUseRevision()) {
    // if (sb == null) {
    // sb = SQL.begin();
    // }
    //
    // IColumnModel col = entityModel.getColumnByPropId(entityModel.getNopRevEndVarPropId(), true);
    // sb.and();
    // GenSqlHelper.appendEq(sb, dialect, marker.getAlias(), col, null, OrmConstants.NOP_VER_MAX_VALUE);
    // }
    // return sb;
    // }

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