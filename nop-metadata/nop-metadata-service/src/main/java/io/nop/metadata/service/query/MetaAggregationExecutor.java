package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableDimension;
import io.nop.metadata.dao.entity.NopMetaTableFilter;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 指标/维度聚合查询执行器（架构基线 §4.4.2，落地 D6/D7）。
 *
 * <p>按 tableType 路由聚合执行（D6）：
 * <ul>
 *   <li>{@code external}/{@code sql}：经 {@code withConnection} 跑原生聚合 SQL（GROUP BY 维度 + aggFunc 指标）。</li>
 *   <li>{@code entity}：经 {@code orm().executeQuery} 跑原生聚合 SQL（物理表 + 物理列 columnCode，
 *       {@code allowUnderscoreName}）。</li>
 * </ul>
 *
 * <p>aggFunc 翻译：sum/count/avg/min/max/countDistinct。时间维度 granularity 按 {@link GranularityBucketing}
 * 翻译为 SQL 分桶表达式（D7，仅 external/sql 路径完整支持）。{@code expression} 型 Measure 首版显式不支持。
 *
 * <p>失败路径显式（Minimum Rules #24）：无 measure/dimension / 字段无法解析 / aggFunc 不支持 /
 * granularity 不约定 / 方言不支持 / expression measure / 实体未注册。
 */
public class MetaAggregationExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(MetaAggregationExecutor.class);

    static final Set<String> SUPPORTED_DIALECTS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("H2", "MySQL", "PostgreSQL")));

    static final ErrorCode ERR_AGGR_NO_MEASURE =
            ErrorCode.define("metadata.aggr-no-measure",
                    "No measure selected for aggregation: {metaTableId}", "metaTableId");
    static final ErrorCode ERR_AGGR_NO_DIMENSION =
            ErrorCode.define("metadata.aggr-no-dimension",
                    "No dimension selected for aggregation: {metaTableId}", "metaTableId");
    static final ErrorCode ERR_AGGR_MEASURE_NOT_FOUND =
            ErrorCode.define("metadata.aggr-measure-not-found",
                    "Measure not found for table: {metaTableId} measureName={measureName}",
                    "metaTableId", "measureName");
    static final ErrorCode ERR_AGGR_DIMENSION_NOT_FOUND =
            ErrorCode.define("metadata.aggr-dimension-not-found",
                    "Dimension not found for table: {metaTableId} dimensionName={dimensionName}",
                    "metaTableId", "dimensionName");
    static final ErrorCode ERR_AGGR_EXPRESSION_MEASURE =
            ErrorCode.define("metadata.aggr-expression-measure-unsupported",
                    "expression-type Measure is explicitly unsupported in first version: "
                            + "{metaTableId} measureName={measureName}", "metaTableId", "measureName");
    static final ErrorCode ERR_AGGR_AGG_FUNC_UNSUPPORTED =
            ErrorCode.define("metadata.aggr-agg-func-unsupported",
                    "aggFunc not supported (expected sum/count/avg/min/max/countDistinct): "
                            + "{aggFunc} measureName={measureName}", "aggFunc", "measureName");
    static final ErrorCode ERR_AGGR_FIELD_NOT_RESOLVED =
            ErrorCode.define("metadata.aggr-field-not-resolved",
                    "Measure/Dimension field could not be resolved to a physical column: "
                            + "{metaTableId} name={name} entityFieldId={entityFieldId}",
                    "metaTableId", "name", "entityFieldId");
    static final ErrorCode ERR_AGGR_ENTITY_NOT_REGISTERED =
            ErrorCode.define("metadata.aggr-entity-not-registered",
                    "Aggregation target entity not registered in runtime IOrmSessionFactory: "
                            + "{metaTableId} entityName={entityName}", "metaTableId", "entityName");
    static final ErrorCode ERR_AGGR_UNSUPPORTED_DIALECT =
            ErrorCode.define("metadata.aggr-unsupported-dialect",
                    "Dialect not supported in first version (only H2/MySQL/PostgreSQL): "
                            + "{databaseProductName} metaTableId={metaTableId}",
                    "databaseProductName", "metaTableId");
    static final ErrorCode ERR_AGGR_EXEC_FAILED =
            ErrorCode.define("metadata.aggr-exec-failed",
                    "Aggregation SQL execution failed: metaTableId={metaTableId} -- {error}",
                    "metaTableId", "error");

    /**
     * 执行聚合查询。
     *
     * @param table           目标逻辑表
     * @param measureNames    选定指标名列表（NopMetaTableMeasure.measureName）
     * @param dimensionNames  选定维度名列表（NopMetaTableDimension.dimensionName）
     * @param userFilter      用户 filter（可为 null）
     * @param limit           分页上限（可为 null）
     * @param offset          分页偏移（可为 null）
     * @param ctx             共享依赖上下文
     * @return {@code Map{items:[{维度值, 指标聚合值}]}}
     */
    public Map<String, Object> executeAggregation(NopMetaTable table, List<String> measureNames,
                                                  List<String> dimensionNames, TreeBean userFilter,
                                                  Long limit, Long offset, MetaQueryContext ctx) {
        if (measureNames == null || measureNames.isEmpty()) {
            throw new NopException(ERR_AGGR_NO_MEASURE).param("metaTableId", table.getMetaTableId());
        }
        if (dimensionNames == null || dimensionNames.isEmpty()) {
            throw new NopException(ERR_AGGR_NO_DIMENSION).param("metaTableId", table.getMetaTableId());
        }
        // 默认过滤器自动应用（§4.4.2）
        IEntityDao<NopMetaTableFilter> filterDao = ctx.daoProvider().daoFor(NopMetaTableFilter.class);
        TreeBean mergedFilter = DefaultFilterApplicator.applyDefaults(table, userFilter, filterDao);

        String tableType = table.getTableType();
        if (_NopMetadataCoreConstants.TABLE_TYPE_ENTITY.equals(tableType)) {
            return buildResult(executeEntityAggregation(table, measureNames, dimensionNames,
                    mergedFilter, limit, offset, ctx));
        }
        if (_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL.equals(tableType)
                || _NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(tableType)) {
            return buildResult(executeExternalAggregation(table, measureNames, dimensionNames,
                    mergedFilter, limit, offset, ctx));
        }
        throw new NopException(ERR_AGGR_EXEC_FAILED)
                .param("metaTableId", table.getMetaTableId())
                .param("error", "unsupported tableType: " + tableType);
    }

    // ============================ entity 聚合（D6：orm().executeQuery）============================

    private List<Map<String, Object>> executeEntityAggregation(NopMetaTable table, List<String> measureNames,
                                                               List<String> dimensionNames, TreeBean filter,
                                                               Long limit, Long offset, MetaQueryContext ctx) {
        IEntityDao<NopMetaEntity> entityDao = ctx.daoProvider().daoFor(NopMetaEntity.class);
        NopMetaEntity entity = entityDao.getEntityById(table.getBaseEntityId());
        if (entity == null || entity.getEntityName() == null || entity.getEntityName().isEmpty()
                || !ctx.orm().isValidEntityName(entity.getEntityName())) {
            throw new NopException(ERR_AGGR_ENTITY_NOT_REGISTERED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("entityName", entity == null ? null : entity.getEntityName());
        }
        String physicalTable = requireName(entity.getTableName(), "tableName");
        FilterToSqlTranslator.validateIdentifier(physicalTable);

        // 属性名→物理列映射（entityFieldId 是 NopMetaEntityField 主键 → columnCode）
        Map<String, String> propToCol = resolveEntityColumns(entity, ctx);

        List<MeasureSpec> measures = loadEntityMeasures(table, measureNames, ctx, propToCol);
        List<DimensionSpec> dims = loadEntityDimensions(table, dimensionNames, ctx, propToCol);

        // 构造聚合 SQL（物理表 + 物理列）
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> groupExprs = new ArrayList<>();
        for (int i = 0; i < dims.size(); i++) {
            if (i > 0 || true) {
                // 维度先输出
            }
            DimensionSpec d = dims.get(i);
            // entity 路径时间分桶受 EQL 函数白名单限制，首版按物理列直查（granularity 暂不下沉到 SQL）
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
        if (filter != null) {
            TreeBean colFilter = rewriteFilterToColumns(filter, propToCol);
            FilterToSqlTranslator.TranslatedFilter tf = ctx.filterTranslator().translate(colFilter);
            if (tf.getSql() != null && !tf.getSql().isEmpty()) {
                sql.append(" WHERE ").append(tf.getSql());
                params.addAll(tf.getParams());
            }
        }
        sql.append(" GROUP BY ").append(String.join(",", groupExprs));
        if (limit != null) {
            sql.append(" LIMIT ?");
            params.add(limit);
        }
        if (offset != null && offset > 0) {
            sql.append(" OFFSET ?");
            params.add(offset);
        }

        String sqlText = sql.toString();
        LOG.info("queryAggregation entity SQL: {}", sqlText);
        SQL sqlObj = SQL.begin().allowUnderscoreName(true).sql(sqlText, params.toArray()).end();
        return ctx.orm().executeQuery(sqlObj, null, this::collectRows);
    }

    // ============================ external/sql 聚合（D6：withConnection）============================

    private List<Map<String, Object>> executeExternalAggregation(NopMetaTable table, List<String> measureNames,
                                                                 List<String> dimensionNames, TreeBean filter,
                                                                 Long limit, Long offset, MetaQueryContext ctx) {
        // querySpace→数据源（external/sql 路径，D2 解析）
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

        // external/sql 路径：entityFieldId 是字段名字符串（非主键）
        List<MeasureSpec> measures = loadExternalMeasures(table, measureNames, ctx);
        List<DimensionSpec> dims = loadExternalDimensions(table, dimensionNames, ctx);

        final List<Map<String, Object>>[] holder = newArrayHolder();
        ctx.connectionService().withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    String dialect = safeProductName(metaData);
                    if (dialect == null || !SUPPORTED_DIALECTS.contains(dialect)) {
                        throw new NopException(ERR_AGGR_UNSUPPORTED_DIALECT)
                                .param("databaseProductName", String.valueOf(dialect))
                                .param("metaTableId", table.getMetaTableId());
                    }
                    String sqlText = buildExternalAggregationSql(table, measures, dims, filter, limit, offset, dialect, ctx);
                    LOG.info("queryAggregation external/sql SQL: {}", sqlText);
                    holder[0] = executeJdbcQuery(conn, sqlText, collectBindParams(measures, dims, filter, ctx, table, dialect),
                            limit, offset, table.getMetaTableId());
                });
        return holder[0] == null ? new ArrayList<>() : holder[0];
    }

    private String buildExternalAggregationSql(NopMetaTable table, List<MeasureSpec> measures,
                                               List<DimensionSpec> dims, TreeBean filter, Long limit, Long offset,
                                               String dialect, MetaQueryContext ctx) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> groupExprs = new ArrayList<>();
        for (int i = 0; i < dims.size(); i++) {
            DimensionSpec d = dims.get(i);
            String expr;
            if (_NopMetadataCoreConstants.DIMENSION_TYPE_TEMPORAL.equals(d.dimensionType) && d.granularity != null) {
                // D7：时间维度按 granularity 分桶（external/sql 路径，withConnection 原生 SQL）
                expr = GranularityBucketing.translate(d.granularity, d.column, dialect, d.alias);
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

        String fromClause = buildFromClause(table);
        sql.append(" FROM ").append(fromClause);

        if (filter != null) {
            FilterToSqlTranslator.TranslatedFilter tf = ctx.filterTranslator().translate(filter);
            if (tf.getSql() != null && !tf.getSql().isEmpty()) {
                sql.append(" WHERE ").append(tf.getSql());
            }
        }
        sql.append(" GROUP BY ").append(String.join(",", groupExprs));
        if (limit != null) {
            sql.append(" LIMIT ?");
        }
        if (offset != null && offset > 0) {
            sql.append(" OFFSET ?");
        }
        return sql.toString();
    }

    /** external: {@code FROM <tableName>}；sql: {@code FROM (<sourceSql>) _t}。标识符/表名经白名单校验。 */
    private String buildFromClause(NopMetaTable table) {
        if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(table.getTableType())) {
            String sourceSql = table.getSourceSql();
            if (sourceSql == null || sourceSql.trim().isEmpty()) {
                throw new NopException(ERR_AGGR_EXEC_FAILED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("error", "sql table sourceSql is empty");
            }
            return "(" + sourceSql + ") _t";
        }
        String tableName = table.getTableName();
        FilterToSqlTranslator.validateIdentifier(tableName);
        return tableName;
    }

    /** 收集 PreparedStatement 绑定参数（filter 值 + limit/offset），与 SQL 中 ? 出现顺序一致。 */
    private List<Object> collectBindParams(List<MeasureSpec> measures, List<DimensionSpec> dims, TreeBean filter,
                                           MetaQueryContext ctx, NopMetaTable table, String dialect) {
        List<Object> params = new ArrayList<>();
        if (filter != null) {
            FilterToSqlTranslator.TranslatedFilter tf = ctx.filterTranslator().translate(filter);
            params.addAll(tf.getParams());
        }
        // limit/offset 由 executeJdbcQuery 在 filter 参数后追加，这里不重复
        return params;
    }

    private List<Map<String, Object>> executeJdbcQuery(Connection conn, String sql, List<Object> filterParams,
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
            throw new NopException(ERR_AGGR_EXEC_FAILED)
                    .param("metaTableId", metaTableId)
                    .param("error", messageOf(e))
                    .cause(e);
        }
    }

    // ============================ measure/dimension 加载 ============================

    private List<MeasureSpec> loadEntityMeasures(NopMetaTable table, List<String> names, MetaQueryContext ctx,
                                                 Map<String, String> propToCol) {
        List<NopMetaTableMeasure> all = loadMeasures(table, names, ctx);
        List<MeasureSpec> specs = new ArrayList<>();
        for (NopMetaTableMeasure m : all) {
            if (m.getExpression() != null && !m.getExpression().trim().isEmpty()) {
                throw new NopException(ERR_AGGR_EXPRESSION_MEASURE)
                        .param("metaTableId", table.getMetaTableId()).param("measureName", m.getMeasureName());
            }
            String column = resolveEntityFieldColumn(m.getEntityFieldId(), m.getMeasureName(), table, ctx, propToCol);
            FilterToSqlTranslator.validateIdentifier(column);
            specs.add(new MeasureSpec(safeAlias(m.getMeasureName()),
                    aggSqlOf(m.getAggFunc(), column, m.getMeasureName())));
        }
        return specs;
    }

    private List<MeasureSpec> loadExternalMeasures(NopMetaTable table, List<String> names, MetaQueryContext ctx) {
        List<NopMetaTableMeasure> all = loadMeasures(table, names, ctx);
        List<MeasureSpec> specs = new ArrayList<>();
        for (NopMetaTableMeasure m : all) {
            if (m.getExpression() != null && !m.getExpression().trim().isEmpty()) {
                throw new NopException(ERR_AGGR_EXPRESSION_MEASURE)
                        .param("metaTableId", table.getMetaTableId()).param("measureName", m.getMeasureName());
            }
            // external/sql：entityFieldId 是字段名字符串
            String column = m.getEntityFieldId();
            if (column == null || column.trim().isEmpty()) {
                throw new NopException(ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", m.getMeasureName()).param("entityFieldId", String.valueOf(column));
            }
            FilterToSqlTranslator.validateIdentifier(column);
            specs.add(new MeasureSpec(safeAlias(m.getMeasureName()),
                    aggSqlOf(m.getAggFunc(), column, m.getMeasureName())));
        }
        return specs;
    }

    private List<NopMetaTableMeasure> loadMeasures(NopMetaTable table, List<String> names, MetaQueryContext ctx) {
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
                throw new NopException(ERR_AGGR_MEASURE_NOT_FOUND)
                        .param("metaTableId", table.getMetaTableId()).param("measureName", name);
            }
            result.add(m);
        }
        return result;
    }

    private List<DimensionSpec> loadEntityDimensions(NopMetaTable table, List<String> names, MetaQueryContext ctx,
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

    private List<DimensionSpec> loadExternalDimensions(NopMetaTable table, List<String> names, MetaQueryContext ctx) {
        List<NopMetaTableDimension> all = loadDimensions(table, names, ctx);
        List<DimensionSpec> specs = new ArrayList<>();
        for (NopMetaTableDimension d : all) {
            String column = d.getEntityFieldId();
            if (column == null || column.trim().isEmpty()) {
                throw new NopException(ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", d.getDimensionName()).param("entityFieldId", String.valueOf(column));
            }
            FilterToSqlTranslator.validateIdentifier(column);
            specs.add(new DimensionSpec(safeAlias(d.getDimensionName()), column, d.getDimensionType(), d.getGranularity()));
        }
        return specs;
    }

    private List<NopMetaTableDimension> loadDimensions(NopMetaTable table, List<String> names, MetaQueryContext ctx) {
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
                throw new NopException(ERR_AGGR_DIMENSION_NOT_FOUND)
                        .param("metaTableId", table.getMetaTableId()).param("dimensionName", name);
            }
            result.add(d);
        }
        return result;
    }

    /** entity 路径：entityFieldId（主键）→ NopMetaEntityField → columnCode。 */
    private String resolveEntityFieldColumn(String entityFieldId, String name, NopMetaTable table,
                                            MetaQueryContext ctx, Map<String, String> propToCol) {
        if (entityFieldId == null || entityFieldId.isEmpty()) {
            throw new NopException(ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
        }
        // entityFieldId 是 NopMetaEntityField 主键 → 查 columnCode
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        NopMetaEntityField field = fieldDao.getEntityById(entityFieldId);
        if (field == null || field.getColumnCode() == null) {
            throw new NopException(ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", name).param("entityFieldId", entityFieldId);
        }
        return field.getColumnCode();
    }

    private Map<String, String> resolveEntityColumns(NopMetaEntity entity, MetaQueryContext ctx) {
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

    private TreeBean rewriteFilterToColumns(TreeBean filter, Map<String, String> propToCol) {
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

    private String aggSqlOf(String aggFunc, String column, String measureName) {
        if (aggFunc == null) {
            throw new NopException(ERR_AGGR_AGG_FUNC_UNSUPPORTED)
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
                throw new NopException(ERR_AGGR_AGG_FUNC_UNSUPPORTED)
                        .param("aggFunc", aggFunc).param("measureName", measureName);
        }
    }

    // ============================ helpers ============================

    private static String safeAlias(String name) {
        if (name == null) {
            return "v";
        }
        // 别名须通过标识符白名单；非法字符替换为下划线
        String s = name.replaceAll("[^A-Za-z0-9_]", "_");
        if (s.isEmpty() || !Character.isLetter(s.charAt(0)) && s.charAt(0) != '_') {
            s = "v_" + s;
        }
        return s.toUpperCase(Locale.ROOT);
    }

    private List<Map<String, Object>> collectRows(IDataSet ds) {
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

    private String requireName(String value, String what) {
        if (value == null || value.trim().isEmpty()) {
            throw new NopException(ERR_AGGR_EXEC_FAILED).param("error", what + " is empty");
        }
        return value;
    }

    private static String safeProductName(DatabaseMetaData metaData) {
        try {
            return metaData.getDatabaseProductName();
        } catch (SQLException e) {
            return null;
        }
    }

    private static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getName();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>>[] newArrayHolder() {
        return (List<Map<String, Object>>[]) new List<?>[1];
    }

    private static Map<String, Object> buildResult(List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items != null ? items : new ArrayList<>());
        return result;
    }

    /** 指标规格：别名 + 聚合 SQL 表达式。 */
    private static final class MeasureSpec {
        final String alias;
        final String aggSql;

        MeasureSpec(String alias, String aggSql) {
            this.alias = alias;
            this.aggSql = aggSql;
        }
    }

    /** 维度规格：别名 + 列表达式 + 类型 + granularity。 */
    private static final class DimensionSpec {
        final String alias;
        final String column;
        final String dimensionType;
        final String granularity;

        DimensionSpec(String alias, String column, String dimensionType, String granularity) {
            this.alias = alias;
            this.column = column;
            this.dimensionType = dimensionType;
            this.granularity = granularity;
        }
    }
}
