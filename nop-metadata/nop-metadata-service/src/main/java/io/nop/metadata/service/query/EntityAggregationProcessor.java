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
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableDimension;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import io.nop.metadata.service.tableref.TableReference;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.metadata.service.query.AggregationContext.*;
import static io.nop.metadata.service.query.AggregationHelper.*;

public class EntityAggregationProcessor implements AggregationProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(EntityAggregationProcessor.class);

    @Override
    public List<Map<String, Object>> execute(AggregationContext context) {
        NopMetaTable table = context.getTable();
        List<String> measureNames = context.getMeasureNames();
        List<String> dimensionNames = context.getDimensionNames();
        TreeBean filter = context.getFilter();
        Long limit = context.getLimit();
        Long offset = context.getOffset();
        TreeBean having = context.getHaving();
        List<OrderFieldBean> orderBy = context.getOrderBy();
        MetaQueryContext ctx = context.ctx();

        IEntityDao<NopMetaEntity> entityDao = ctx.daoProvider().daoFor(NopMetaEntity.class);
        NopMetaEntity entity = entityDao.getEntityById(table.getBaseEntityId());
        if (entity == null || entity.getEntityName() == null || entity.getEntityName().isEmpty()
                || !ctx.orm().isValidEntityName(entity.getEntityName())) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_ENTITY_NOT_REGISTERED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("entityName", entity == null ? null : entity.getEntityName());
        }
        String physicalTable = requireName(entity.getTableName(), "tableName");
        FilterToSqlTranslator.validateIdentifier(physicalTable);

        Map<String, String> propToCol = resolveEntityColumns(entity, ctx);

        List<MeasureSpec> measures = loadEntityMeasures(table, measureNames, ctx, propToCol);
        List<DimensionSpec> dims = loadEntityDimensions(table, dimensionNames, ctx, propToCol);

        Map<String, String> nameToExpr = buildNameToExprTable(measures, dims, measureNames, dimensionNames, table);

        boolean needsBypass = false;
        for (DimensionSpec d : dims) {
            if (_NopMetadataCoreConstants.DIMENSION_TYPE_TEMPORAL.equals(d.dimensionType) && d.granularity != null) {
                needsBypass = true;
                break;
            }
        }
        if (!needsBypass) {
            for (MeasureSpec m : measures) {
                if (m.isExpression()) {
                    needsBypass = true;
                    break;
                }
            }
        }
        if (needsBypass) {
            return executeEntityAggregationBypassEql(table, entity, physicalTable, measures, dims, filter, having,
                    orderBy, limit, offset, nameToExpr, measureNames, dimensionNames, propToCol, ctx);
        }
        return executeEntityAggregationViaEql(table, physicalTable, measures, dims, filter, having, orderBy,
                limit, offset, nameToExpr, measureNames, dimensionNames, propToCol, ctx);
    }

    private List<Map<String, Object>> executeEntityAggregationViaEql(NopMetaTable table, String physicalTable,
                                                                      List<MeasureSpec> measures, List<DimensionSpec> dims,
                                                                      TreeBean filter, TreeBean having,
                                                                      List<OrderFieldBean> orderBy, Long limit,
                                                                      Long offset, Map<String, String> nameToExpr,
                                                                      List<String> measureNames,
                                                                      List<String> dimensionNames,
                                                                      Map<String, String> propToCol,
                                                                      MetaQueryContext ctx) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> groupExprs = new ArrayList<>();
        for (int i = 0; i < dims.size(); i++) {
            DimensionSpec d = dims.get(i);
            FilterToSqlTranslator.validateIdentifier(d.column);
            String expr = d.column;
            sql.append(i == 0 ? "" : ",").append(expr).append(" AS ").append(d.alias);
            groupExprs.add(expr);
        }
        for (MeasureSpec m : measures) {
            sql.append(",").append(m.aggSql).append(" AS ").append(m.alias);
        }
        sql.append(" FROM ").append(physicalTable);

        List<Object> params = new ArrayList<>();
        for (MeasureSpec m : measures) {
            if (m.expressionParams != null) {
                params.addAll(m.expressionParams);
            }
        }
        if (filter != null) {
            TreeBean colFilter = rewriteFilterToColumns(filter, propToCol);
            FilterToSqlTranslator.TranslatedFilter tf = ctx.filterTranslator().translate(colFilter);
            if (tf.getSql() != null && !tf.getSql().isEmpty()) {
                sql.append(" WHERE ").append(tf.getSql());
                params.addAll(tf.getParams());
            }
        }
        sql.append(" GROUP BY ").append(String.join(",", groupExprs));
        if (having != null) {
            MetaAggregationExecutor.preprocessHavingArithmetic(having, nameToExpr, table, measureNames, dimensionNames);
            FilterToSqlTranslator.TranslatedFilter hf = ctx.filterTranslator().translate(having,
                    nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
            if (hf.getSql() != null && !hf.getSql().isEmpty()) {
                sql.append(" HAVING ").append(hf.getSql());
                params.addAll(hf.getParams());
            }
        }
        String orderByClause = buildOrderByClause(orderBy, nameToExpr, table, measureNames, dimensionNames, "ORDER_BY");
        if (!orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }
        SqlPagination.appendLimitOffset(sql, limit, offset, null);
        if (limit != null) {
            params.add(limit);
        }
        if (offset != null && offset > 0) {
            params.add(offset);
        }

        String sqlText = sql.toString();
        LOG.info("queryAggregation entity SQL: {}", sqlText);
        SQL sqlObj = SQL.begin().allowUnderscoreName(true).sql(sqlText, params.toArray()).end();
        return ctx.orm().executeQuery(sqlObj, null, AggregationHelper::collectRows);
    }

    private List<Map<String, Object>> executeEntityAggregationBypassEql(NopMetaTable table, NopMetaEntity entity,
                                                                         String physicalTable, List<MeasureSpec> measures,
                                                                         List<DimensionSpec> dims, TreeBean filter,
                                                                         TreeBean having, List<OrderFieldBean> orderBy,
                                                                         Long limit, Long offset,
                                                                         Map<String, String> nameToExpr,
                                                                         List<String> measureNames,
                                                                         List<String> dimensionNames,
                                                                         Map<String, String> propToCol,
                                                                         MetaQueryContext ctx) {
        String entityQuerySpace = entity.getQuerySpace();
        if (entityQuerySpace == null || entityQuerySpace.trim().isEmpty()) {
            entityQuerySpace = DaoConstants.DEFAULT_QUERY_SPACE;
        }
        TableReference ref = new TableReference(TableReference.Kind.ENTITY, table.getMetaTableId(),
                physicalTable, null, null, entity, entityQuerySpace, null);

        return ctx.tableRefExecutor().execute(ref, (conn, metaData, productName) -> {
            if (productName == null || !SUPPORTED_DIALECTS.contains(productName)) {
                throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_UNSUPPORTED_DIALECT)
                        .param("databaseProductName", String.valueOf(productName))
                        .param("metaTableId", table.getMetaTableId());
            }
            for (MeasureSpec m : measures) {
                if (m.isExpression()) {
                    ExpressionMeasureValidator.checkDialectSupported(m.validatedExpression, productName,
                            table.getMetaTableId(), m.alias);
                }
            }
            StringBuilder sql = new StringBuilder("SELECT ");
            List<String> groupExprs = new ArrayList<>();
            for (int i = 0; i < dims.size(); i++) {
                DimensionSpec d = dims.get(i);
                String expr;
                if (_NopMetadataCoreConstants.DIMENSION_TYPE_TEMPORAL.equals(d.dimensionType) && d.granularity != null) {
                    expr = GranularityBucketing.translate(d.granularity, d.column, productName, d.alias);
                } else {
                    FilterToSqlTranslator.validateIdentifier(d.column);
                    expr = d.column;
                }
                sql.append(i == 0 ? "" : ",").append(expr).append(" AS ").append(d.alias);
                groupExprs.add(expr);
            }
            for (MeasureSpec m : measures) {
                sql.append(",").append(m.aggSql).append(" AS ").append(m.alias);
            }
            sql.append(" FROM ").append(physicalTable);

            List<Object> params = new ArrayList<>();
            for (MeasureSpec m : measures) {
                if (m.expressionParams != null) {
                    params.addAll(m.expressionParams);
                }
            }
            if (filter != null) {
                TreeBean colFilter = rewriteFilterToColumns(filter, propToCol);
                FilterToSqlTranslator.TranslatedFilter tf = ctx.filterTranslator().translate(colFilter);
                if (tf.getSql() != null && !tf.getSql().isEmpty()) {
                    sql.append(" WHERE ").append(tf.getSql());
                    params.addAll(tf.getParams());
                }
            }
            sql.append(" GROUP BY ").append(String.join(",", groupExprs));
            if (having != null) {
                MetaAggregationExecutor.preprocessHavingArithmetic(having, nameToExpr, table, measureNames, dimensionNames);
                FilterToSqlTranslator.TranslatedFilter hf = ctx.filterTranslator().translate(having,
                        nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
                if (hf.getSql() != null && !hf.getSql().isEmpty()) {
                    sql.append(" HAVING ").append(hf.getSql());
                    params.addAll(hf.getParams());
                }
            }
            String orderByClause = buildOrderByClause(orderBy, nameToExpr, table, measureNames, dimensionNames, "ORDER_BY");
            if (!orderByClause.isEmpty()) {
                sql.append(" ORDER BY ").append(orderByClause);
            }
            SqlPagination.appendLimitOffset(sql, limit, offset, productName);
            String sqlText = sql.toString();
            LOG.info("queryAggregation entity bypass-EQL SQL: {}", sqlText);
            return executeJdbcQuery(conn, sqlText, params, limit, offset, table.getMetaTableId());
        });
    }

    public static List<MeasureSpec> loadEntityMeasures(NopMetaTable table, List<String> names, MetaQueryContext ctx,
                                                        Map<String, String> propToCol) {
        List<NopMetaTableMeasure> all = loadMeasures(table, names, ctx);
        Set<String> columnSet = new LinkedHashSet<>(propToCol.values());
        List<MeasureSpec> specs = new ArrayList<>();
        for (NopMetaTableMeasure m : all) {
            if (m.getExpression() != null && !m.getExpression().trim().isEmpty()) {
                ExpressionMeasureValidator.ValidatedExpression ve =
                        ExpressionMeasureValidator.validateStatic(m.getExpression(),
                                ExpressionMeasureValidator.ValidationOptions.singleTableStrict(columnSet),
                                table.getMetaTableId(), m.getMeasureName());
                specs.add(new MeasureSpec(safeAlias(m.getMeasureName()),
                        aggSqlOf(m.getAggFunc(), ve.sqlFragment, m.getMeasureName()),
                        ve.params, ve));
                continue;
            }
            String column = resolveEntityFieldColumn(m.getEntityFieldId(), m.getMeasureName(), table, ctx, propToCol);
            FilterToSqlTranslator.validateIdentifier(column);
            specs.add(new MeasureSpec(safeAlias(m.getMeasureName()),
                    aggSqlOf(m.getAggFunc(), column, m.getMeasureName())));
        }
        return specs;
    }

    public static List<DimensionSpec> loadEntityDimensions(NopMetaTable table, List<String> names, MetaQueryContext ctx,
                                                            Map<String, String> propToCol) {
        List<NopMetaTableDimension> all = loadDimensions(table, names, ctx);
        List<DimensionSpec> specs = new ArrayList<>();
        for (NopMetaTableDimension d : all) {
            String column = resolveEntityFieldColumn(d.getEntityFieldId(), d.getDimensionName(), table, ctx, propToCol);
            FilterToSqlTranslator.validateIdentifier(column);
            specs.add(new DimensionSpec(safeAlias(d.getDimensionName()), column, d.getDimensionType(), d.getGranularity()));
        }
        return specs;
    }
}
