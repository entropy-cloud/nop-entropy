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
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableDimension;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.metadata.service.query.AggregationContext.*;
import static io.nop.metadata.service.query.AggregationHelper.*;

public class EntityEntityJoinAggregationProcessor implements AggregationProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(EntityEntityJoinAggregationProcessor.class);

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

        NopMetaEntity leftEntity = leftEp.entity;
        NopMetaEntity rightEntity = rightEp.entity;
        MetaJoinExecutor joinExecutor = context.joinExecutor();

        joinExecutor.requireRegistered(leftEntity, "left", joinId, ctx);
        joinExecutor.requireRegistered(rightEntity, "right", joinId, ctx);

        if (equalsStr(leftEntity.getMetaEntityId(), rightEntity.getMetaEntityId())) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_SELF_JOIN)
                    .param("joinId", joinId).param("entityId", leftEntity.getMetaEntityId());
        }

        String leftPhysical = requireName(leftEntity.getTableName(), "leftTableName");
        String rightPhysical = requireName(rightEntity.getTableName(), "rightTableName");
        FilterToSqlTranslator.validateIdentifier(leftPhysical);
        FilterToSqlTranslator.validateIdentifier(rightPhysical);
        Map<String, String> leftPropToCol = resolveEntityColumns(leftEntity, ctx);
        Map<String, String> rightPropToCol = resolveEntityColumns(rightEntity, ctx);

        String leftJoinCol = joinExecutor.resolveFieldToColumn(leftPropToCol, join.getLeftField(),
                leftEntity, "left", joinId);
        String rightJoinCol = joinExecutor.resolveFieldToColumn(rightPropToCol, join.getRightField(),
                rightEntity, "right", joinId);
        FilterToSqlTranslator.validateIdentifier(leftJoinCol);
        FilterToSqlTranslator.validateIdentifier(rightJoinCol);

        String leftEntityId = leftEntity.getMetaEntityId();
        String rightEntityId = rightEntity.getMetaEntityId();
        JoinFieldResolver resolver = new JoinFieldResolver(leftEntityId, rightEntityId, joinId, table, ctx);
        List<JoinMeasureSpec> measures = loadJoinMeasures(table, measureNames, ctx, resolver);
        List<JoinDimensionSpec> dims = loadJoinDimensions(table, dimensionNames, ctx, resolver);

        Map<String, String> nameToExpr = buildJoinNameToExprTable(measures, dims, measureNames, dimensionNames, table);

        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> groupExprs = new ArrayList<>();
        for (int i = 0; i < dims.size(); i++) {
            JoinDimensionSpec d = dims.get(i);
            String expr = d.qualifiedCol;
            sql.append(i == 0 ? "" : ",").append(expr).append(" AS ").append(d.alias);
            groupExprs.add(expr);
        }
        for (JoinMeasureSpec m : measures) {
            sql.append(",").append(m.aggSql).append(" AS ").append(m.alias);
        }
        sql.append(" FROM ").append(leftPhysical).append(" l");
        String joinKeyword = _NopMetadataCoreConstants.JOIN_TYPE_INNER.equals(join.getJoinType())
                ? " INNER JOIN " : " LEFT JOIN ";
        sql.append(joinKeyword).append(rightPhysical).append(" r")
                .append(" ON l.").append(leftJoinCol)
                .append(" = r.").append(rightJoinCol);

        List<Object> params = new ArrayList<>();
        for (JoinMeasureSpec m : measures) {
            if (m.expressionParams != null) {
                params.addAll(m.expressionParams);
            }
        }
        if (filter != null) {
            TreeBean colFilter = rewriteFilterToColumns(filter, leftPropToCol);
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
        LOG.info("queryAggregation entity JOIN SQL: {}", sqlText);
        SQL sqlObj = SQL.begin().allowUnderscoreName(true).sql(sqlText, params.toArray()).end();
        try {
            return ctx.orm().executeQuery(sqlObj, null, AggregationHelper::collectRows);
        } catch (Exception e) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_COMPILE_FAILED, e)
                    .param("joinId", joinId)
                    .param("error", messageOf(e));
        }
    }

    private static List<JoinMeasureSpec> loadJoinMeasures(NopMetaTable table, List<String> names, MetaQueryContext ctx,
                                                           JoinFieldResolver resolver) {
        List<NopMetaTableMeasure> all = loadMeasures(table, names, ctx);
        Set<String> leftCols = resolver.resolveEntityColumns(resolver.leftEntityId());
        Set<String> rightCols = resolver.resolveEntityColumns(resolver.rightEntityId());
        List<JoinMeasureSpec> specs = new ArrayList<>();
        for (NopMetaTableMeasure m : all) {
            if (m.getExpression() != null && !m.getExpression().trim().isEmpty()) {
                ExpressionMeasureValidator.ValidatedExpression ve =
                        ExpressionMeasureValidator.validateStatic(m.getExpression(),
                                ExpressionMeasureValidator.ValidationOptions.joinStrict(leftCols, rightCols),
                                table.getMetaTableId(), m.getMeasureName());
                specs.add(new JoinMeasureSpec(safeAlias(m.getMeasureName()),
                        aggSqlOf(m.getAggFunc(), ve.sqlFragment, m.getMeasureName()),
                        "<expression>", ve.params, ve));
                continue;
            }
            JoinField f = resolver.resolve(m.getEntityFieldId(), m.getMeasureName(), m.getSide(), "measure");
            FilterToSqlTranslator.validateIdentifier(f.column);
            specs.add(new JoinMeasureSpec(safeAlias(m.getMeasureName()),
                    aggSqlOf(m.getAggFunc(), f.qualifiedColumn, m.getMeasureName()), f.qualifiedColumn));
        }
        return specs;
    }

    private static List<JoinDimensionSpec> loadJoinDimensions(NopMetaTable table, List<String> names, MetaQueryContext ctx,
                                                               JoinFieldResolver resolver) {
        List<NopMetaTableDimension> all = loadDimensions(table, names, ctx);
        List<JoinDimensionSpec> specs = new ArrayList<>();
        for (NopMetaTableDimension d : all) {
            JoinField f = resolver.resolve(d.getEntityFieldId(), d.getDimensionName(), d.getSide(), "dimension");
            FilterToSqlTranslator.validateIdentifier(f.column);
            specs.add(new JoinDimensionSpec(safeAlias(d.getDimensionName()), f.qualifiedColumn, f.column,
                    d.getDimensionType(), d.getGranularity()));
        }
        return specs;
    }
}
