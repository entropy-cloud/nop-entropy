
package io.nop.metadata.service.query;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityDimension;
import io.nop.metadata.dao.entity.NopMetaEntityMeasure;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import io.nop.metadata.service.quality.MetaQualityRuleExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.metadata.service.query.AggregationContext.*;
import static io.nop.metadata.service.query.AggregationHelper.*;

public class ExternalAggregationProcessor implements AggregationProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalAggregationProcessor.class);

    @Override
    public List<Map<String, Object>> execute(AggregationContext context) {
        NopMetaEntity table = context.getTable();
        List<String> measureNames = context.getMeasureNames();
        List<String> dimensionNames = context.getDimensionNames();
        TreeBean filter = context.getFilter();
        Long limit = context.getLimit();
        Long offset = context.getOffset();
        TreeBean having = context.getHaving();
        List<OrderFieldBean> orderBy = context.getOrderBy();
        MetaQueryContext ctx = context.ctx();

        IEntityDao<NopMetaDataSource> dsDao = ctx.daoProvider().daoFor(NopMetaDataSource.class);
        NopMetaDataSource dataSource;
        try {
            dataSource = ctx.dataSourceResolver().resolveActiveOrThrow(dsDao, table.getQuerySpace());
        } catch (NopException e) {
            if (e.getParam("metaEntityId") == null) {
                e.param("metaEntityId", table.getMetaEntityId());
            }
            throw e;
        }

        List<MeasureSpec> measures = loadExternalMeasures(table, measureNames, ctx);
        List<DimensionSpec> dims = loadExternalDimensions(table, dimensionNames, ctx);

        Map<String, String> nameToExpr = buildNameToExprTable(measures, dims, measureNames, dimensionNames, table);

        final List<Map<String, Object>>[] holder = newArrayHolder();
        final Map<String, String> _nameToExpr = nameToExpr;
        final List<MeasureSpec> _measures = measures;
        final List<DimensionSpec> _dims = dims;
        ctx.connectionService().withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    String dialect = safeProductName(metaData);
                    // null 仅剩"driver 返回空产品名"这一罕见情形（infra 失败已在
                    // safeProductName 内 fail-loud 抛出，AR-14a），按 unsupported-dialect 处理
                    if (dialect == null || !SUPPORTED_DIALECTS.contains(dialect)) {
                        throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_UNSUPPORTED_DIALECT)
                                .param("databaseProductName", String.valueOf(dialect))
                                .param("metaEntityId", table.getMetaEntityId());
                    }
                    for (MeasureSpec m : _measures) {
                        if (m.isExpression()) {
                            ExpressionMeasureValidator.checkDialectSupported(m.validatedExpression, dialect,
                                    table.getMetaEntityId(), m.alias);
                        }
                    }
                    String sqlText = buildExternalAggregationSql(table, _measures, _dims, filter, having, orderBy,
                            _nameToExpr, measureNames, dimensionNames, limit, offset, dialect, ctx);
                    // P1-8（plan 2026-08-15-1913-1，AR-16 形态）：sql 路径 SQL 内嵌
                    // sourceSql 全文，INFO 只记 sqlHash
                    LOG.info("queryAggregation external/sql sqlHash={}",
                            MetaQualityRuleExecutor.sqlHashOf(sqlText));
                    LOG.debug("queryAggregation external/sql SQL: {}", sqlText);
                    holder[0] = executeJdbcQuery(conn, sqlText, collectBindParams(_measures, _dims, filter, having,
                            _nameToExpr, ctx, table, measureNames, dimensionNames),
                            limit, offset, table.getMetaEntityId());
                });
        return holder[0] == null ? new ArrayList<>() : holder[0];
    }

    public static List<MeasureSpec> loadExternalMeasures(NopMetaEntity table, List<String> names, MetaQueryContext ctx) {
        List<NopMetaEntityMeasure> all = loadMeasures(table, names, ctx);
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        Set<String> columnSet = resolveTableColumnNames(table, fieldDao, ctx);
        List<MeasureSpec> specs = new ArrayList<>();
        for (NopMetaEntityMeasure m : all) {
            if (m.getExpression() != null && !m.getExpression().trim().isEmpty()) {
                ExpressionMeasureValidator.ValidatedExpression ve =
                        ExpressionMeasureValidator.validateStatic(m.getExpression(),
                                ExpressionMeasureValidator.ValidationOptions.singleTableStrict(columnSet),
                                table.getMetaEntityId(), m.getMeasureName());
                specs.add(new MeasureSpec(safeAlias(m.getMeasureName()),
                        aggSqlOf(m.getAggFunc(), ve.sqlFragment, m.getMeasureName()),
                        ve.params, ve));
                continue;
            }
            String column = m.getEntityFieldId();
            if (column == null || column.trim().isEmpty()) {
                throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaEntityId", table.getMetaEntityId())
                        .param("name", m.getMeasureName()).param("entityFieldId", String.valueOf(column));
            }
            FilterToSqlTranslator.validateIdentifier(column);
            specs.add(new MeasureSpec(safeAlias(m.getMeasureName()),
                    aggSqlOf(m.getAggFunc(), column, m.getMeasureName())));
        }
        return specs;
    }

    public static List<DimensionSpec> loadExternalDimensions(NopMetaEntity table, List<String> names, MetaQueryContext ctx) {
        List<NopMetaEntityDimension> all = loadDimensions(table, names, ctx);
        List<DimensionSpec> specs = new ArrayList<>();
        for (NopMetaEntityDimension d : all) {
            String column = d.getEntityFieldId();
            if (column == null || column.trim().isEmpty()) {
                throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaEntityId", table.getMetaEntityId())
                        .param("name", d.getDimensionName()).param("entityFieldId", String.valueOf(column));
            }
            FilterToSqlTranslator.validateIdentifier(column);
            specs.add(new DimensionSpec(safeAlias(d.getDimensionName()), column, d.getDimensionType(), d.getGranularity()));
        }
        return specs;
    }
}
