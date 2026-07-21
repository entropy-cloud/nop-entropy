package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableDimension;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import io.nop.metadata.service.field.ResolvedTableField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class AggregationHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AggregationHelper.class);

    public static String safeAlias(String name) {
        if (name == null) {
            return "v";
        }
        String s = name.replaceAll("[^A-Za-z0-9_]", "_");
        if (s.isEmpty() || !Character.isLetter(s.charAt(0)) && s.charAt(0) != '_') {
            s = "v_" + s;
        }
        return s.toUpperCase(Locale.ROOT);
    }

    public static Map<String, Object> buildResult(List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items != null ? items : new ArrayList<>());
        return result;
    }

    public static String aggSqlOf(String aggFunc, String column, String measureName) {
        if (aggFunc == null) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_AGG_FUNC_UNSUPPORTED)
                    .param("aggFunc", String.valueOf(aggFunc)).param("measureName", measureName);
        }
        switch (aggFunc) {
            case _NopMetadataCoreConstants.AGG_FUNC_SUM:
                return "SUM(" + column + ")";
            case _NopMetadataCoreConstants.AGG_FUNC_COUNT:
                return "COUNT(" + column + ")";
            case _NopMetadataCoreConstants.AGG_FUNC_AVG:
                return "AVG(" + column + ")";
            case _NopMetadataCoreConstants.AGG_FUNC_MIN:
                return "MIN(" + column + ")";
            case _NopMetadataCoreConstants.AGG_FUNC_MAX:
                return "MAX(" + column + ")";
            case _NopMetadataCoreConstants.AGG_FUNC_COUNT_DISTINCT:
                return "COUNT(DISTINCT " + column + ")";
            default:
                throw new NopException(NopMetadataErrors.ERR_AGGR_AGG_FUNC_UNSUPPORTED)
                        .param("aggFunc", aggFunc).param("measureName", measureName);
        }
    }

    public static List<Map<String, Object>> executeJdbcQuery(Connection conn, String sql, List<Object> filterParams,
                                                              Long limit, Long offset, String metaTableId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object p : filterParams) {
                st.setObject(idx++, p);
            }
            if (limit != null) {
                st.setObject(idx++, limit);
            }
            if (offset != null && offset > 0) {
                st.setObject(idx++, offset);
            }
            try (ResultSet rs = st.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= columnCount; c++) {
                        String label = meta.getColumnLabel(c);
                        if (label == null || label.isEmpty()) {
                            label = meta.getColumnName(c);
                        }
                        row.put(label, rs.getObject(c));
                    }
                    rows.add(row);
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_EXEC_FAILED, e)
                    .param("metaTableId", metaTableId)
                    .param("error", messageOf(e));
        }
    }

    public static List<Map<String, Object>> collectRows(IDataSet ds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (IDataRow row : ds) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < row.getFieldCount(); i++) {
                map.put(row.getMeta().getFieldName(i), row.getObject(i));
            }
            rows.add(map);
        }
        return rows;
    }

    public static String requireName(String value, String what) {
        if (value == null || value.trim().isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_EXEC_FAILED).param("error", what + " is empty");
        }
        return value;
    }

    public static Set<String> resolveTableColumnNames(NopMetaTable table, IEntityDao<NopMetaEntityField> fieldDao,
                                                       MetaQueryContext ctx) {
        List<ResolvedTableField> fields = ctx.fieldResolver().resolve(table, fieldDao);
        Set<String> names = new LinkedHashSet<>(fields.size());
        for (ResolvedTableField f : fields) {
            names.add(f.getName());
        }
        return names;
    }

    public static String resolveExternalFieldOrThrow(Set<String> columns, String field, NopMetaTable table,
                                                      String side, String joinId) {
        if (field == null || field.isEmpty() || !containsIgnoreCase(columns, field)) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "join-field")
                    .param("side", side)
                    .param("endpointTableType", String.valueOf(table.getTableType()))
                    .param("column", String.valueOf(field))
                    .param("joinId", joinId);
        }
        return field;
    }

    public static NopMetaDataSource resolveSharedDataSourceOrThrow(NopMetaTable table, MetaQueryContext ctx, String joinId) {
        IEntityDao<NopMetaDataSource> dsDao = ctx.daoProvider().daoFor(NopMetaDataSource.class);
        try {
            return ctx.dataSourceResolver().resolveActiveOrThrow(dsDao, table.getQuerySpace());
        } catch (NopException e) {
            if (e.getParam("joinId") == null) {
                e.param("joinId", joinId).param("tableId", table.getMetaTableId());
            }
            throw e;
        }
    }

    public static Map<String, String> resolveEntityColumns(NopMetaEntity entity, MetaQueryContext ctx) {
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_metaEntityId, entity.getMetaEntityId()));
        List<NopMetaEntityField> fields = fieldDao.findAllByQuery(q);
        Map<String, String> propToCol = new LinkedHashMap<>();
        for (NopMetaEntityField f : fields) {
            if (f.getFieldName() != null && f.getColumnCode() != null) {
                propToCol.put(f.getFieldName(), f.getColumnCode());
            }
        }
        return propToCol;
    }

    public static TreeBean rewriteFilterToColumns(TreeBean filter, Map<String, String> propToCol) {
        if (filter == null) {
            return null;
        }
        TreeBean copy = new TreeBean(filter.getTagName());
        if (filter.getAttrs() != null) {
            copy.setAttrs(new LinkedHashMap<>(filter.getAttrs()));
        }
        Object name = copy.getAttr("name");
        if (name != null && propToCol.containsKey(name.toString())) {
            copy.setAttr("name", propToCol.get(name.toString()));
        }
        if (filter.getChildren() != null) {
            List<TreeBean> children = new ArrayList<>();
            for (TreeBean child : filter.getChildren()) {
                children.add(rewriteFilterToColumns(child, propToCol));
            }
            copy.setChildren(children);
        }
        return copy;
    }

    public static String resolveEntityFieldColumn(String entityFieldId, String name, NopMetaTable table,
                                                   MetaQueryContext ctx, Map<String, String> propToCol) {
        if (entityFieldId == null || entityFieldId.isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
        }
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        NopMetaEntityField field = fieldDao.getEntityById(entityFieldId);
        if (field == null || field.getColumnCode() == null) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", name).param("entityFieldId", entityFieldId);
        }
        return field.getColumnCode();
    }

    public static Map<String, String> buildNameToExprTable(List<AggregationContext.MeasureSpec> measures,
                                                            List<AggregationContext.DimensionSpec> dims,
                                                            List<String> measureNames, List<String> dimensionNames,
                                                            NopMetaTable table) {
        Map<String, String> map = new LinkedHashMap<>();
        if (measures.size() != measureNames.size()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: measures/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < measures.size(); i++) {
            map.put(measureNames.get(i), measures.get(i).aggSql);
        }
        if (dims.size() != dimensionNames.size()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: dims/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < dims.size(); i++) {
            map.put(dimensionNames.get(i), dims.get(i).column);
        }
        return map;
    }

    public static Map<String, String> buildJoinNameToExprTable(List<AggregationContext.JoinMeasureSpec> measures,
                                                                List<AggregationContext.JoinDimensionSpec> dims,
                                                                List<String> measureNames,
                                                                List<String> dimensionNames, NopMetaTable table) {
        Map<String, String> map = new LinkedHashMap<>();
        if (measures.size() != measureNames.size()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: join measures/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < measures.size(); i++) {
            map.put(measureNames.get(i), measures.get(i).aggSql);
        }
        if (dims.size() != dimensionNames.size()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: join dims/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < dims.size(); i++) {
            map.put(dimensionNames.get(i), dims.get(i).qualifiedCol);
        }
        return map;
    }

    public static Function<String, String> nameResolverFor(Map<String, String> nameToExpr,
                                                            NopMetaTable table,
                                                            List<String> measureNames,
                                                            List<String> dimensionNames,
                                                            String clause) {
        return name -> {
            if (!FilterToSqlTranslator.IDENTIFIER_PATTERN.matcher(name).matches()) {
                return name;
            }
            String expr = nameToExpr.get(name);
            if (expr == null) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", name)
                        .param("selectedMeasures", String.valueOf(measureNames))
                        .param("selectedDimensions", String.valueOf(dimensionNames));
            }
            if (expr.indexOf('?') >= 0) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("measureName", name)
                        .param("clause", clause);
            }
            return expr;
        };
    }

    public static String buildOrderByClause(List<OrderFieldBean> orderBy, Map<String, String> nameToExpr,
                                             NopMetaTable table, List<String> measureNames,
                                             List<String> dimensionNames, String clause) {
        if (orderBy == null || orderBy.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orderBy.size(); i++) {
            OrderFieldBean f = orderBy.get(i);
            String name = f.getName();
            String expr = nameToExpr.get(name);
            if (expr == null) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_ORDER_BY_UNKNOWN_NAME)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", String.valueOf(name))
                        .param("selectedMeasures", String.valueOf(measureNames))
                        .param("selectedDimensions", String.valueOf(dimensionNames));
            }
            if (expr.indexOf('?') >= 0) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("measureName", String.valueOf(name))
                        .param("clause", clause);
            }
            if (i > 0) {
                sb.append(",");
            }
            sb.append(expr).append(f.isDesc() ? " DESC" : " ASC");
            Boolean nullsFirst = f.getNullsFirst();
            if (nullsFirst != null) {
                sb.append(nullsFirst ? " NULLS FIRST" : " NULLS LAST");
            }
        }
        return sb.toString();
    }

    public static List<NopMetaTableMeasure> loadMeasures(NopMetaTable table, List<String> names, MetaQueryContext ctx) {
        IEntityDao<NopMetaTableMeasure> dao = ctx.daoProvider().daoFor(NopMetaTableMeasure.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTableMeasure.PROP_NAME_metaTableId, table.getMetaTableId()));
        List<NopMetaTableMeasure> all = dao.findAllByQuery(q);
        Map<String, NopMetaTableMeasure> byName = new LinkedHashMap<>();
        for (NopMetaTableMeasure m : all) {
            byName.put(m.getMeasureName(), m);
        }
        List<NopMetaTableMeasure> result = new ArrayList<>();
        for (String name : names) {
            NopMetaTableMeasure m = byName.get(name);
            if (m == null) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_MEASURE_NOT_FOUND)
                        .param("metaTableId", table.getMetaTableId()).param("measureName", name);
            }
            result.add(m);
        }
        return result;
    }

    public static List<NopMetaTableDimension> loadDimensions(NopMetaTable table, List<String> names, MetaQueryContext ctx) {
        IEntityDao<NopMetaTableDimension> dao = ctx.daoProvider().daoFor(NopMetaTableDimension.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTableDimension.PROP_NAME_metaTableId, table.getMetaTableId()));
        List<NopMetaTableDimension> all = dao.findAllByQuery(q);
        Map<String, NopMetaTableDimension> byName = new LinkedHashMap<>();
        for (NopMetaTableDimension d : all) {
            byName.put(d.getDimensionName(), d);
        }
        List<NopMetaTableDimension> result = new ArrayList<>();
        for (String name : names) {
            NopMetaTableDimension d = byName.get(name);
            if (d == null) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_DIMENSION_NOT_FOUND)
                        .param("metaTableId", table.getMetaTableId()).param("dimensionName", name);
            }
            result.add(d);
        }
        return result;
    }

    public static String endpointTypeOf(MetaJoinExecutor.Endpoint ep) {
        if (ep.isEntity) {
            return "entity";
        }
        return ep.table == null ? "unknown" : String.valueOf(ep.table.getTableType());
    }

    public static List<Map<String, Object>>[] newArrayHolder() {
        return ArrayHolderUtils.newArrayHolder();
    }

    public static boolean containsIgnoreCase(Set<String> cols, String name) {
        if (cols == null || name == null) {
            return false;
        }
        for (String c : cols) {
            if (c != null && c.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public static boolean equalsStr(String a, String b) {
        return (a == null) ? b == null : a.equals(b);
    }

    public static String crossDbAliasOf(NopMetaTableJoin join) {
        String a = join.getAlias();
        return (a != null && !a.trim().isEmpty()) ? a : "right";
    }

    public static Object getCaseInsensitiveObj(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static String findKeyIgnoreCase(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        for (String k : map.keySet()) {
            if (k.equalsIgnoreCase(key)) {
                return k;
            }
        }
        return null;
    }

    public static String safeProductName(DatabaseMetaData metaData) {
        try {
            return metaData.getDatabaseProductName();
        } catch (SQLException e) {
            LOG.error("safeProductName failed: getDatabaseProductName threw", e);
            return null;
        }
    }

    public static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getName();
    }

    public static String externalTableFromForJoin(NopMetaTable table, String alias) {
        FilterToSqlTranslator.validateIdentifier(alias);
        if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(table.getTableType())) {
            String sourceSql = table.getSourceSql();
            if (sourceSql == null || sourceSql.trim().isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_EXEC_FAILED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("error", "sql table sourceSql is empty");
            }
            return "(" + sourceSql + ") " + alias;
        }
        String tableName = table.getTableName();
        FilterToSqlTranslator.validateIdentifier(tableName);
        return tableName + " " + alias;
    }

    public static String buildEntityFromClause(String physicalTable, String schema, String alias) {
        FilterToSqlTranslator.validateIdentifier(alias);
        if (schema != null && !schema.trim().isEmpty()) {
            FilterToSqlTranslator.validateIdentifier(schema);
            return schema + "." + physicalTable + " " + alias;
        }
        return physicalTable + " " + alias;
    }

    public static boolean isEntityTableVisible(DatabaseMetaData metaData, String schema, String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return false;
        }
        return checkTableExists(metaData, schema, tableName)
                || checkTableExists(metaData, schema, tableName.toUpperCase(Locale.ROOT))
                || checkTableExists(metaData, schema, tableName.toLowerCase(Locale.ROOT));
    }

    public static boolean checkTableExists(DatabaseMetaData metaData, String schema, String tableName) {
        try (ResultSet rs = metaData.getTables(null, schema, tableName, null)) {
            while (rs.next()) {
                return true;
            }
        } catch (SQLException e) {
            LOG.warn("checkTableExists: getTables failed (treated as not visible): schema={}, table={}",
                    schema, tableName, e);
        }
        return false;
    }

    public static java.math.BigDecimal toBigDecimal(Object v) {
        if (v instanceof java.math.BigDecimal) {
            return (java.math.BigDecimal) v;
        }
        if (v instanceof java.math.BigInteger) {
            return new java.math.BigDecimal((java.math.BigInteger) v);
        }
        if (v instanceof Number) {
            return java.math.BigDecimal.valueOf(((Number) v).doubleValue());
        }
        return null;
    }

    public static String buildFromClause(NopMetaTable table) {
        if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(table.getTableType())) {
            String sourceSql = table.getSourceSql();
            if (sourceSql == null || sourceSql.trim().isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_EXEC_FAILED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("error", "sql table sourceSql is empty");
            }
            return "(" + sourceSql + ") _t";
        }
        String tableName = table.getTableName();
        FilterToSqlTranslator.validateIdentifier(tableName);
        return tableName;
    }

    public static String buildExternalAggregationSql(NopMetaTable table,
                                                      List<AggregationContext.MeasureSpec> measures,
                                                      List<AggregationContext.DimensionSpec> dims, TreeBean filter,
                                                      TreeBean having,
                                                      List<OrderFieldBean> orderBy, Map<String, String> nameToExpr,
                                                      List<String> measureNames, List<String> dimensionNames,
                                                      Long limit, Long offset,
                                                      String dialect, MetaQueryContext ctx) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> groupExprs = new ArrayList<>();
        for (int i = 0; i < dims.size(); i++) {
            AggregationContext.DimensionSpec d = dims.get(i);
            String expr;
            if (_NopMetadataCoreConstants.DIMENSION_TYPE_TEMPORAL.equals(d.dimensionType) && d.granularity != null) {
                expr = GranularityBucketing.translate(d.granularity, d.column, dialect, d.alias);
            } else {
                FilterToSqlTranslator.validateIdentifier(d.column);
                expr = d.column;
            }
            sql.append(i == 0 ? "" : ",").append(expr).append(" AS ").append(d.alias);
            groupExprs.add(expr);
        }
        for (AggregationContext.MeasureSpec m : measures) {
            sql.append(",").append(m.aggSql).append(" AS ").append(m.alias);
        }

        String fromClause = buildFromClause(table);
        sql.append(" FROM ").append(fromClause);

        if (filter != null) {
            FilterToSqlTranslator.TranslatedFilter tf = ctx.filterTranslator().translate(filter);
            if (tf.getSql() != null && !tf.getSql().isEmpty()) {
                sql.append(" WHERE ").append(tf.getSql());
            }
        }
        sql.append(" GROUP BY ").append(String.join(",", groupExprs));
        if (having != null) {
            MetaAggregationExecutor.preprocessHavingArithmetic(having, nameToExpr, table, measureNames, dimensionNames);
            FilterToSqlTranslator.TranslatedFilter hf = ctx.filterTranslator().translate(having,
                    nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
            if (hf.getSql() != null && !hf.getSql().isEmpty()) {
                sql.append(" HAVING ").append(hf.getSql());
            }
        }
        String orderByClause = buildOrderByClause(orderBy, nameToExpr, table, measureNames, dimensionNames, "ORDER_BY");
        if (!orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }
        SqlPagination.appendLimitOffset(sql, limit, offset, dialect);
        return sql.toString();
    }

    public static List<Object> collectBindParams(List<AggregationContext.MeasureSpec> measures,
                                                  List<AggregationContext.DimensionSpec> dims, TreeBean filter,
                                                  TreeBean having, Map<String, String> nameToExpr, MetaQueryContext ctx,
                                                  NopMetaTable table,
                                                  List<String> measureNames, List<String> dimensionNames) {
        List<Object> params = new ArrayList<>();
        for (AggregationContext.MeasureSpec m : measures) {
            if (m.expressionParams != null) {
                params.addAll(m.expressionParams);
            }
        }
        if (filter != null) {
            FilterToSqlTranslator.TranslatedFilter tf = ctx.filterTranslator().translate(filter);
            params.addAll(tf.getParams());
        }
        if (having != null) {
            FilterToSqlTranslator.TranslatedFilter hf = ctx.filterTranslator().translate(having,
                    nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
            params.addAll(hf.getParams());
        }
        return params;
    }

    public static StringBuilder buildExternalExternalJoinSql(
            List<AggregationContext.JoinMeasureSpec> measures,
            List<AggregationContext.JoinDimensionSpec> dims,
            String leftFrom, String rightFrom, String leftJoinCol,
            String rightJoinCol, NopMetaTableJoin join,
            FilterToSqlTranslator.TranslatedFilter filterTf,
            FilterToSqlTranslator.TranslatedFilter havingTf,
            String orderByClause,
            String dialect, Long limit, Long offset) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> groupExprs = new ArrayList<>();
        for (int i = 0; i < dims.size(); i++) {
            AggregationContext.JoinDimensionSpec d = dims.get(i);
            String expr;
            if (_NopMetadataCoreConstants.DIMENSION_TYPE_TEMPORAL.equals(d.dimensionType) && d.granularity != null) {
                expr = GranularityBucketing.translate(d.granularity, d.qualifiedCol, dialect, d.alias);
                FilterToSqlTranslator.validateIdentifier(d.column);
            } else {
                expr = d.qualifiedCol;
            }
            sql.append(i == 0 ? "" : ",").append(expr).append(" AS ").append(d.alias);
            groupExprs.add(expr);
        }
        for (AggregationContext.JoinMeasureSpec m : measures) {
            sql.append(",").append(m.aggSql).append(" AS ").append(m.alias);
        }
        sql.append(" FROM ").append(leftFrom);
        String joinKeyword = _NopMetadataCoreConstants.JOIN_TYPE_INNER.equals(join.getJoinType())
                ? " INNER JOIN " : " LEFT JOIN ";
        sql.append(joinKeyword).append(rightFrom)
                .append(" ON l.").append(leftJoinCol)
                .append(" = r.").append(rightJoinCol);

        if (filterTf != null && filterTf.getSql() != null && !filterTf.getSql().isEmpty()) {
            sql.append(" WHERE ").append(filterTf.getSql());
        }
        sql.append(" GROUP BY ").append(String.join(",", groupExprs));
        if (havingTf != null && havingTf.getSql() != null && !havingTf.getSql().isEmpty()) {
            sql.append(" HAVING ").append(havingTf.getSql());
        }
        if (!orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }
        SqlPagination.appendLimitOffset(sql, limit, offset, dialect);
        return sql;
    }

    public static StringBuilder buildMixedSameDbJoinSql(
            List<AggregationContext.JoinMeasureSpec> measures,
            List<AggregationContext.JoinDimensionSpec> dims,
            String entityFrom, String tableFrom,
            String entityAlias, String tableAlias,
            String entityJoinColumn, String tableJoinColumn,
            NopMetaTableJoin join,
            FilterToSqlTranslator.TranslatedFilter filterTf,
            FilterToSqlTranslator.TranslatedFilter havingTf,
            String orderByClause,
            String dialect, Long limit, Long offset) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> groupExprs = new ArrayList<>();
        for (int i = 0; i < dims.size(); i++) {
            AggregationContext.JoinDimensionSpec d = dims.get(i);
            String expr;
            if (_NopMetadataCoreConstants.DIMENSION_TYPE_TEMPORAL.equals(d.dimensionType) && d.granularity != null) {
                expr = GranularityBucketing.translate(d.granularity, d.qualifiedCol, dialect, d.alias);
                FilterToSqlTranslator.validateIdentifier(d.column);
            } else {
                expr = d.qualifiedCol;
            }
            sql.append(i == 0 ? "" : ",").append(expr).append(" AS ").append(d.alias);
            groupExprs.add(expr);
        }
        for (AggregationContext.JoinMeasureSpec m : measures) {
            sql.append(",").append(m.aggSql).append(" AS ").append(m.alias);
        }
        sql.append(" FROM ").append(entityFrom);
        String joinKeyword = _NopMetadataCoreConstants.JOIN_TYPE_INNER.equals(join.getJoinType())
                ? " INNER JOIN " : " LEFT JOIN ";
        sql.append(joinKeyword).append(tableFrom)
                .append(" ON ").append(entityAlias).append(".").append(entityJoinColumn)
                .append(" = ").append(tableAlias).append(".").append(tableJoinColumn);

        if (filterTf != null && filterTf.getSql() != null && !filterTf.getSql().isEmpty()) {
            sql.append(" WHERE ").append(filterTf.getSql());
        }
        sql.append(" GROUP BY ").append(String.join(",", groupExprs));
        if (havingTf != null && havingTf.getSql() != null && !havingTf.getSql().isEmpty()) {
            sql.append(" HAVING ").append(havingTf.getSql());
        }
        if (!orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }
        SqlPagination.appendLimitOffset(sql, limit, offset, dialect);
        return sql;
    }

    public static Map<String, String> buildCrossDbNameToAliasTable(
            List<AggregationContext.CrossDbMeasureSpec> measures,
            List<AggregationContext.CrossDbDimensionSpec> dims,
            List<String> measureNames,
            List<String> dimensionNames, NopMetaTable table) {
        Map<String, String> map = new LinkedHashMap<>();
        if (measures.size() != measureNames.size()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: cross-db measures/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < measures.size(); i++) {
            map.put(measureNames.get(i), measures.get(i).alias);
        }
        if (dims.size() != dimensionNames.size()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: cross-db dims/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < dims.size(); i++) {
            map.put(dimensionNames.get(i), dims.get(i).alias);
        }
        return map;
    }

    public static void resolveAndValidateLookupKeys(List<? extends AggregationContext.CrossDbFieldSpec> specs,
                                                      String alias,
                                                      Map<String, Object> sampleRow, NopMetaTable table, String joinId,
                                                      String fieldKind) {
        for (AggregationContext.CrossDbFieldSpec spec : specs) {
            String rawKey = spec.rawKey;
            String lookupKey;
            if (_NopMetadataCoreConstants.JOIN_SIDE_RIGHT.equalsIgnoreCase(spec.side)) {
                String prefixed = alias + "_" + rawKey;
                String actualPrefixed = findKeyIgnoreCase(sampleRow, prefixed);
                lookupKey = actualPrefixed != null ? actualPrefixed : rawKey;
            } else {
                lookupKey = rawKey;
            }
            String actual = findKeyIgnoreCase(sampleRow, lookupKey);
            if (actual == null) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_CROSS_DB_FIELD_KEY_MISSING)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", spec.alias)
                        .param("fieldKind", fieldKind)
                        .param("rawKey", String.valueOf(rawKey))
                        .param("lookupKey", String.valueOf(lookupKey))
                        .param("rowKeys", sampleRow.keySet())
                        .param("joinId", joinId);
            }
            spec.lookupKey = actual;
        }
    }

    public static List<Map<String, Object>> memoryGroupBy(List<Map<String, Object>> rows,
                                                           List<AggregationContext.CrossDbMeasureSpec> measures,
                                                           List<AggregationContext.CrossDbDimensionSpec> dims) {
        LinkedHashMap<String, Map<String, Object>> groupDims = new LinkedHashMap<>();
        LinkedHashMap<String, AggregationContext.MemAggAccumulator[]> groupAccs = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            StringBuilder keyBuilder = new StringBuilder();
            Object[] dimValues = new Object[dims.size()];
            for (int i = 0; i < dims.size(); i++) {
                Object v = getCaseInsensitiveObj(row, dims.get(i).lookupKey);
                dimValues[i] = v;
                if (i > 0) {
                    keyBuilder.append('\u0001');
                }
                keyBuilder.append(v == null ? "\u0000" : String.valueOf(v));
            }
            String groupKey = keyBuilder.toString();

            Map<String, Object> gRow = groupDims.get(groupKey);
            AggregationContext.MemAggAccumulator[] accs = groupAccs.get(groupKey);
            if (gRow == null) {
                gRow = new LinkedHashMap<>();
                for (int i = 0; i < dims.size(); i++) {
                    gRow.put(dims.get(i).alias, dimValues[i]);
                }
                groupDims.put(groupKey, gRow);
                accs = AggregationContext.MemAggAccumulator.newAccumulators(measures);
                groupAccs.put(groupKey, accs);
            }
            for (int i = 0; i < measures.size(); i++) {
                Object val = getCaseInsensitiveObj(row, measures.get(i).lookupKey);
                accs[i].accumulate(val);
            }
        }

        List<Map<String, Object>> items = new ArrayList<>(groupDims.size());
        for (Map.Entry<String, Map<String, Object>> e : groupDims.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>(e.getValue());
            AggregationContext.MemAggAccumulator[] accs = groupAccs.get(e.getKey());
            for (int i = 0; i < measures.size(); i++) {
                item.put(measures.get(i).alias, accs[i].result());
            }
            items.add(item);
        }
        return items;
    }

    public static List<Map<String, Object>> truncateCrossDb(List<Map<String, Object>> rows, Long limit, Long offset) {
        int from = 0;
        if (offset != null && offset > 0) {
            if (offset > Integer.MAX_VALUE) {
                throw new NopException(NopMetadataErrors.ERR_PAGINATION_OFFSET_TOO_LARGE).param("offset", offset);
            }
            from = offset.intValue();
        }
        if (from > rows.size()) {
            from = rows.size();
        }
        int to = rows.size();
        if (limit != null) {
            if (limit > Integer.MAX_VALUE) {
                throw new NopException(NopMetadataErrors.ERR_PAGINATION_LIMIT_TOO_LARGE).param("limit", limit);
            }
            to = Math.min(rows.size(), from + limit.intValue());
        }
        return new ArrayList<>(rows.subList(from, to));
    }

    public static List<AggregationContext.JoinMeasureSpec> loadJoinMeasuresWithResolver(
            NopMetaTable table, List<String> names,
            MetaQueryContext ctx,
            AggregationContext.JoinFieldResolverFn resolver,
            Set<String> leftCols, Set<String> rightCols) {
        List<NopMetaTableMeasure> all = loadMeasures(table, names, ctx);
        List<AggregationContext.JoinMeasureSpec> specs = new ArrayList<>();
        for (NopMetaTableMeasure m : all) {
            if (m.getExpression() != null && !m.getExpression().trim().isEmpty()) {
                ExpressionMeasureValidator.ValidatedExpression ve =
                        ExpressionMeasureValidator.validateStatic(m.getExpression(),
                                ExpressionMeasureValidator.ValidationOptions.joinStrict(leftCols, rightCols),
                                table.getMetaTableId(), m.getMeasureName());
                specs.add(new AggregationContext.JoinMeasureSpec(safeAlias(m.getMeasureName()),
                        aggSqlOf(m.getAggFunc(), ve.sqlFragment, m.getMeasureName()),
                        "<expression>", ve.params, ve));
                continue;
            }
            AggregationContext.JoinField f = resolver.resolve(m.getEntityFieldId(), m.getMeasureName(), m.getSide());
            FilterToSqlTranslator.validateIdentifier(f.column);
            specs.add(new AggregationContext.JoinMeasureSpec(safeAlias(m.getMeasureName()),
                    aggSqlOf(m.getAggFunc(), f.qualifiedColumn, m.getMeasureName()), f.qualifiedColumn));
        }
        return specs;
    }

    public static List<AggregationContext.JoinDimensionSpec> loadJoinDimensionsWithResolver(
            NopMetaTable table, List<String> names,
            MetaQueryContext ctx,
            AggregationContext.JoinFieldResolverFn resolver) {
        List<NopMetaTableDimension> all = loadDimensions(table, names, ctx);
        List<AggregationContext.JoinDimensionSpec> specs = new ArrayList<>();
        for (NopMetaTableDimension d : all) {
            AggregationContext.JoinField f = resolver.resolve(d.getEntityFieldId(), d.getDimensionName(), d.getSide());
            FilterToSqlTranslator.validateIdentifier(f.column);
            specs.add(new AggregationContext.JoinDimensionSpec(safeAlias(d.getDimensionName()), f.qualifiedColumn, f.column,
                    d.getDimensionType(), d.getGranularity()));
        }
        return specs;
    }
}
