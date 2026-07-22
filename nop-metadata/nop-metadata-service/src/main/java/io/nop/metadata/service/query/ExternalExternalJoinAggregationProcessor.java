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
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.metadata.service.query.AggregationContext.*;
import static io.nop.metadata.service.query.AggregationHelper.*;

public class ExternalExternalJoinAggregationProcessor implements AggregationProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalExternalJoinAggregationProcessor.class);

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

        NopMetaTable leftTable = leftEp.table;
        NopMetaTable rightTable = rightEp.table;

        if (equalsStr(leftTable.getMetaTableId(), rightTable.getMetaTableId())) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_JOIN_SELF_JOIN)
                    .param("joinId", joinId).param("entityId", leftTable.getMetaTableId());
        }

        NopMetaDataSource dataSource = resolveSharedDataSourceOrThrow(leftTable, ctx, joinId);

        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        Set<String> leftCols = resolveTableColumnNames(leftTable, fieldDao, ctx);
        Set<String> rightCols = resolveTableColumnNames(rightTable, fieldDao, ctx);
        String leftJoinCol = resolveExternalFieldOrThrow(leftCols, join.getLeftField(), leftTable, "left", joinId);
        String rightJoinCol = resolveExternalFieldOrThrow(rightCols, join.getRightField(), rightTable, "right", joinId);
        FilterToSqlTranslator.validateIdentifier(leftJoinCol);
        FilterToSqlTranslator.validateIdentifier(rightJoinCol);

        JoinExternalSideResolver sideResolver = new JoinExternalSideResolver(
                leftTable, rightTable, leftCols, rightCols, joinId, table);
        List<JoinMeasureSpec> measures = loadExternalJoinMeasures(table, measureNames, ctx, sideResolver);
        List<JoinDimensionSpec> dims = loadExternalJoinDimensions(table, dimensionNames, ctx, sideResolver);

        Map<String, String> nameToExpr = buildJoinNameToExprTable(measures, dims, measureNames, dimensionNames, table);

        String leftFrom = externalTableFromForJoin(leftTable, "l");
        String rightFrom = externalTableFromForJoin(rightTable, "r");

        if (having != null) {
            MetaAggregationExecutor.preprocessHavingArithmetic(having, nameToExpr, table, measureNames, dimensionNames);
        }
        FilterToSqlTranslator.TranslatedFilter filterTf = filter == null ? null : ctx.filterTranslator().translate(filter);
        FilterToSqlTranslator.TranslatedFilter havingTf = having == null ? null
                : ctx.filterTranslator().translate(having,
                        nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
        String orderByClause = buildOrderByClause(orderBy, nameToExpr, table, measureNames, dimensionNames, "ORDER_BY");

        final List<Map<String, Object>>[] holder = newArrayHolder();
        final List<JoinMeasureSpec> _measures = measures;
        final List<JoinDimensionSpec> _dims = dims;
        ctx.connectionService().withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    String dialect = safeProductName(metaData);
                    if (dialect == null || !SUPPORTED_DIALECTS.contains(dialect)) {
                        throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_UNSUPPORTED_DIALECT)
                                .param("databaseProductName", String.valueOf(dialect))
                                .param("metaTableId", table.getMetaTableId());
                    }
                    for (JoinMeasureSpec m : _measures) {
                        if (m.isExpression()) {
                            ExpressionMeasureValidator.checkDialectSupported(m.validatedExpression, dialect,
                                    table.getMetaTableId(), m.alias);
                        }
                    }
                    StringBuilder sql = buildExternalExternalJoinSql(_measures, _dims, leftFrom, rightFrom,
                            leftJoinCol, rightJoinCol, join, filterTf, havingTf, orderByClause, dialect, limit, offset);
                    List<Object> params = new ArrayList<>();
                    for (JoinMeasureSpec m : _measures) {
                        if (m.expressionParams != null) {
                            params.addAll(m.expressionParams);
                        }
                    }
                    if (filterTf != null && filterTf.getSql() != null && !filterTf.getSql().isEmpty()) {
                        params.addAll(filterTf.getParams());
                    }
                    if (havingTf != null && havingTf.getSql() != null && !havingTf.getSql().isEmpty()) {
                        params.addAll(havingTf.getParams());
                    }
                    if (limit != null) {
                        params.add(limit);
                    }
                    if (offset != null && offset > 0) {
                        params.add(offset);
                    }
                    final String sqlText = sql.toString();
                    LOG.info("queryAggregation external<->external JOIN SQL: {}", sqlText);
                    holder[0] = executeJdbcQuery(conn, sqlText, params, limit, offset, table.getMetaTableId());
                });
        return holder[0] == null ? new ArrayList<>() : holder[0];
    }

    public static List<JoinMeasureSpec> loadExternalJoinMeasures(NopMetaTable table, List<String> names,
                                                                   MetaQueryContext ctx, JoinExternalSideResolver resolver) {
        return loadJoinMeasuresWithResolver(table, names, ctx, resolver::resolve,
                resolver.leftColumns(), resolver.rightColumns());
    }

    public static List<JoinDimensionSpec> loadExternalJoinDimensions(NopMetaTable table, List<String> names,
                                                                      MetaQueryContext ctx, JoinExternalSideResolver resolver) {
        return loadJoinDimensionsWithResolver(table, names, ctx, resolver::resolve);
    }
}
