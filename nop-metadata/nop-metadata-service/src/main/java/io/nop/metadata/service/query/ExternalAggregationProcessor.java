package io.nop.metadata.service.query;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableDimension;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
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

public class ExternalAggregationProcessor implements AggregationProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalAggregationProcessor.class);

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

        IEntityDao<NopMetaDataSource> dsDao = ctx.daoProvider().daoFor(NopMetaDataSource.class);
        NopMetaDataSource dataSource;
        try {
            dataSource = ctx.dataSourceResolver().resolveActiveOrThrow(dsDao, table.getQuerySpace());
        } catch (NopException e) {
            if (e.getParam("metaTableId") == null) {
                e.param("metaTableId", table.getMetaTableId());
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
                    if (dialect == null || !SUPPORTED_DIALECTS.contains(dialect)) {
                        throw new NopException(MetaAggregationExecutor.ERR_AGGR_UNSUPPORTED_DIALECT)
                                .param("databaseProductName", String.valueOf(dialect))
                                .param("metaTableId", table.getMetaTableId());
                    }
                    for (MeasureSpec m : _measures) {
                        if (m.isExpression()) {
                            ExpressionMeasureValidator.checkDialectSupported(m.validatedExpression, dialect,
                                    table.getMetaTableId(), m.alias);
                        }
                    }
                    String sqlText = buildExternalAggregationSql(table, _measures, _dims, filter, having, orderBy,
                            _nameToExpr, measureNames, dimensionNames, limit, offset, dialect, ctx);
                    LOG.info("queryAggregation external/sql SQL: {}", sqlText);
                    holder[0] = executeJdbcQuery(conn, sqlText, collectBindParams(_measures, _dims, filter, having,
                            _nameToExpr, ctx, table, measureNames, dimensionNames),
                            limit, offset, table.getMetaTableId());
                });
        return holder[0] == null ? new ArrayList<>() : holder[0];
    }

    public static List<MeasureSpec> loadExternalMeasures(NopMetaTable table, List<String> names, MetaQueryContext ctx) {
        List<NopMetaTableMeasure> all = loadMeasures(table, names, ctx);
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        Set<String> columnSet = resolveTableColumnNames(table, fieldDao, ctx);
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
            String column = m.getEntityFieldId();
            if (column == null || column.trim().isEmpty()) {
                throw new NopException(MetaAggregationExecutor.ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", m.getMeasureName()).param("entityFieldId", String.valueOf(column));
            }
            FilterToSqlTranslator.validateIdentifier(column);
            specs.add(new MeasureSpec(safeAlias(m.getMeasureName()),
                    aggSqlOf(m.getAggFunc(), column, m.getMeasureName())));
        }
        return specs;
    }

    public static List<DimensionSpec> loadExternalDimensions(NopMetaTable table, List<String> names, MetaQueryContext ctx) {
        List<NopMetaTableDimension> all = loadDimensions(table, names, ctx);
        List<DimensionSpec> specs = new ArrayList<>();
        for (NopMetaTableDimension d : all) {
            String column = d.getEntityFieldId();
            if (column == null || column.trim().isEmpty()) {
                throw new NopException(MetaAggregationExecutor.ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", d.getDimensionName()).param("entityFieldId", String.valueOf(column));
            }
            FilterToSqlTranslator.validateIdentifier(column);
            specs.add(new DimensionSpec(safeAlias(d.getDimensionName()), column, d.getDimensionType(), d.getGranularity()));
        }
        return specs;
    }
}
