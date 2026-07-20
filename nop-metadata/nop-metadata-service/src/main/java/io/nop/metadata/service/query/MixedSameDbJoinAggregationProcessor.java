/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.query;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import io.nop.metadata.service.NopMetadataErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.metadata.service.query.AggregationContext.*;

public class MixedSameDbJoinAggregationProcessor implements AggregationProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(MixedSameDbJoinAggregationProcessor.class);

    @Override
    public List<Map<String, Object>> execute(AggregationContext context) {
        NopMetaTable table = context.getTable();
        List<String> measureNames = context.getMeasureNames();
        List<String> dimensionNames = context.getDimensionNames();
        TreeBean filter = context.getFilter();
        String joinId = context.getJoinId();
        Long limit = context.getLimit();
        Long offset = context.getOffset();
        TreeBean having = context.getHaving();
        List<OrderFieldBean> orderBy = context.getOrderBy();
        MetaQueryContext ctx = context.ctx();
        NopMetaTableJoin join = context.getJoin();
        MetaJoinExecutor.Endpoint leftEp = context.getLeftEndpoint();
        MetaJoinExecutor.Endpoint rightEp = context.getRightEndpoint();

        boolean entityOnLeft = leftEp.isEntity();
        NopMetaEntity entityEndpoint = entityOnLeft ? leftEp.entity : rightEp.entity;
        NopMetaTable tableEndpoint = entityOnLeft ? rightEp.table : leftEp.table;

        String entityPhysicalTable = entityEndpoint.getTableName();
        if (entityPhysicalTable == null || entityPhysicalTable.trim().isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_MIXED_ENTITY_TABLE_EMPTY)
                    .param("joinId", joinId).param("entityId", String.valueOf(entityEndpoint.getMetaEntityId()));
        }
        FilterToSqlTranslator.validateIdentifier(entityPhysicalTable);
        String entitySchema = entityEndpoint.getDbSchema();
        if (entitySchema != null && !entitySchema.trim().isEmpty()) {
            FilterToSqlTranslator.validateIdentifier(entitySchema);
        } else {
            entitySchema = null;
        }

        NopMetaDataSource dataSource = resolveSharedDataSourceOrThrow(tableEndpoint, ctx, joinId);

        // 连接可达性实测
        if (!checkEntityTableVisible(dataSource, entitySchema, entityPhysicalTable, ctx)) {
            // 不可同库 → 走跨库内存 GROUP BY 路径（plan 1500-2 D10）
            return new CrossDbInMemoryAggregationProcessor().execute(context);
        }

        Map<String, String> entityPropToCol = resolveEntityColumns(entityEndpoint, ctx);

        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        Set<String> tableCols = resolveTableColumnNames(tableEndpoint, fieldDao, ctx);
        String entityJoinFieldProp = entityOnLeft ? join.getLeftField() : join.getRightField();
        String tableJoinFieldCol = entityOnLeft ? join.getRightField() : join.getLeftField();
        MetaJoinExecutor joinExecutor = context.joinExecutor();
        String entityJoinColumn = joinExecutor.resolveFieldToColumn(entityPropToCol, entityJoinFieldProp,
                entityEndpoint, entityOnLeft ? "left" : "right", joinId);
        String tableJoinColumn = resolveExternalFieldOrThrow(tableCols, tableJoinFieldCol, tableEndpoint,
                entityOnLeft ? "right" : "left", joinId);
        FilterToSqlTranslator.validateIdentifier(entityJoinColumn);
        FilterToSqlTranslator.validateIdentifier(tableJoinColumn);

        JoinMixedSideResolver sideResolver = new JoinMixedSideResolver(
                entityEndpoint, entityPropToCol, tableEndpoint, tableCols, entityOnLeft, joinId, table, ctx);
        List<JoinMeasureSpec> measures = loadExternalJoinMeasures(table, measureNames, ctx, sideResolver);
        List<JoinDimensionSpec> dims = loadExternalJoinDimensions(table, dimensionNames, ctx, sideResolver);

        Map<String, String> nameToExpr = buildJoinNameToExprTable(measures, dims, measureNames, dimensionNames, table);

        String entityAlias = entityOnLeft ? "l" : "r";
        String tableAlias = entityOnLeft ? "r" : "l";
        String entityFrom = buildEntityFromClause(entityPhysicalTable, entitySchema, entityAlias);
        String tableFrom = externalTableFromForJoin(tableEndpoint, tableAlias);

        if (having != null) {
            MetaAggregationExecutor.preprocessHavingArithmetic(having, nameToExpr, table, measureNames, dimensionNames);
        }
        FilterToSqlTranslator.TranslatedFilter filterTf =
                filter == null ? null : ctx.filterTranslator().translate(filter);
        FilterToSqlTranslator.TranslatedFilter havingTf = having == null ? null
                : ctx.filterTranslator().translate(having,
                        nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
        String orderByClause = buildOrderByClause(orderBy, nameToExpr, table, measureNames, dimensionNames, "ORDER_BY");

        final String _entityFrom = entityFrom;
        final String _tableFrom = tableFrom;
        final String _entityAlias = entityAlias;
        final String _tableAlias = tableAlias;
        final String _entityJoinColumn = entityJoinColumn;
        final String _tableJoinColumn = tableJoinColumn;
        final List<JoinMeasureSpec> _measures = measures;
        final List<JoinDimensionSpec> _dims = dims;
        final FilterToSqlTranslator.TranslatedFilter _filterTf = filterTf;
        final FilterToSqlTranslator.TranslatedFilter _havingTf = havingTf;
        final String _orderByClause = orderByClause;

        final List<Map<String, Object>>[] holder = newArrayHolder();
        ctx.connectionService().withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    String dialect = safeProductName(metaData);
                    if (dialect == null || !SUPPORTED_DIALECTS.contains(dialect)) {
                        throw new NopException(NopMetadataErrors.ERR_AGGR_UNSUPPORTED_DIALECT)
                                .param("databaseProductName", String.valueOf(dialect))
                                .param("metaTableId", table.getMetaTableId());
                    }
                    for (JoinMeasureSpec m : _measures) {
                        if (m.isExpression()) {
                            ExpressionMeasureValidator.checkDialectSupported(m.validatedExpression, dialect,
                                    table.getMetaTableId(), m.alias);
                        }
                    }
                    StringBuilder sql = buildMixedSameDbJoinSql(_measures, _dims, _entityFrom, _tableFrom,
                            _entityAlias, _tableAlias, _entityJoinColumn, _tableJoinColumn, join, _filterTf,
                            _havingTf, _orderByClause, dialect, limit, offset);
                    List<Object> params = new ArrayList<>();
                    for (JoinMeasureSpec m : _measures) {
                        if (m.expressionParams != null) {
                            params.addAll(m.expressionParams);
                        }
                    }
                    if (_filterTf != null && _filterTf.getSql() != null && !_filterTf.getSql().isEmpty()) {
                        params.addAll(_filterTf.getParams());
                    }
                    if (_havingTf != null && _havingTf.getSql() != null && !_havingTf.getSql().isEmpty()) {
                        params.addAll(_havingTf.getParams());
                    }
                    if (limit != null) {
                        params.add(limit);
                    }
                    if (offset != null && offset > 0) {
                        params.add(offset);
                    }
                    final String sqlText = sql.toString();
                    LOG.info("queryAggregation mixed same-DB entity<->external/sql JOIN SQL: {}", sqlText);
                    holder[0] = executeJdbcQuery(conn, sqlText, params, limit, offset, table.getMetaTableId());
                });
        return holder[0] == null ? new ArrayList<>() : holder[0];
    }

    private boolean checkEntityTableVisible(NopMetaDataSource dataSource, String entitySchema,
                                             String entityPhysicalTable, MetaQueryContext ctx) {
        if (entityPhysicalTable == null || entityPhysicalTable.isEmpty()) {
            return false;
        }
        final boolean[] visible = {false};
        ctx.connectionService().withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    visible[0] = isEntityTableVisible(metaData, entitySchema, entityPhysicalTable);
                });
        return visible[0];
    }

    public static List<JoinMeasureSpec> loadExternalJoinMeasures(NopMetaTable table, List<String> names,
                                                                   MetaQueryContext ctx, JoinMixedSideResolver resolver) {
        return loadJoinMeasuresWithResolver(table, names, ctx, resolver::resolve,
                resolver.leftColumns(), resolver.rightColumns());
    }

    public static List<JoinDimensionSpec> loadExternalJoinDimensions(NopMetaTable table, List<String> names,
                                                                      MetaQueryContext ctx, JoinMixedSideResolver resolver) {
        return loadJoinDimensionsWithResolver(table, names, ctx, resolver::resolve);
    }
}
