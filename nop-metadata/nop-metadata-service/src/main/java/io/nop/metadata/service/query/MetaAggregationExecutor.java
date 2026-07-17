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
import io.nop.metadata.dao.entity.NopMetaTableJoin;
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
import java.util.Objects;
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

    /**
     * JOIN 加载/端点解析复用依赖（plan 0852-1：显式选定「抽取共享」复用 {@link MetaJoinExecutor} 的 join
     * 加载/归属/joinType 校验 + 端点解析 + 实体注册校验，避免去重 debt）。
     */
    private final MetaJoinExecutor joinExecutor;

    public MetaAggregationExecutor() {
        this(new MetaJoinExecutor());
    }

    /** 显式注入 join 执行器（BizModel 传入共享实例，保持单例语义）。 */
    public MetaAggregationExecutor(MetaJoinExecutor joinExecutor) {
        this.joinExecutor = Objects.requireNonNull(joinExecutor, "joinExecutor");
    }

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

    // ===== JOIN 聚合专属 ErrorCode（plan 0852-1：entity↔entity 跨表聚合，失败路径显式不静默降级）=====

    /** JOIN 聚合要求两端点均为 entity；任一为 external/sql table 端点 → 显式失败（external/sql JOIN 聚合 deferred）。 */
    static final ErrorCode ERR_AGGR_JOIN_ENDPOINT_NOT_ENTITY =
            ErrorCode.define("metadata.aggr-join-endpoint-not-entity",
                    "JOIN aggregation requires both join endpoints to be entity; external/sql JOIN aggregation "
                            + "is deferred (out-of-scope, needs Measure/Dimension side modeling): "
                            + "{joinId} side={side} endpointType={endpointType}",
                    "joinId", "side", "endpointType");
    /** self-join（leftEntityId == rightEntityId）：字段归属两侧均命中、无法表达右别名 → 显式失败（与 external/sql 侧别缺口同源）。 */
    static final ErrorCode ERR_AGGR_JOIN_SELF_JOIN =
            ErrorCode.define("metadata.aggr-join-self-join-unsupported",
                    "Self-join (leftEntityId == rightEntityId) is not supported for JOIN aggregation: "
                            + "field attribution to left/right alias is ambiguous: {joinId} entityId={entityId}",
                    "joinId", "entityId");
    /** Measure/Dimension 的 entityFieldId.metaEntityId 既不等于左也不等于右 entity → 字段不可归属，显式失败。 */
    static final ErrorCode ERR_AGGR_JOIN_FIELD_SIDE_UNRESOLVED =
            ErrorCode.define("metadata.aggr-join-field-side-unresolved",
                    "Measure/Dimension entityFieldId does not belong to either left or right entity of the join "
                            + "(metaEntityId mismatch): {metaTableId} name={name} entityFieldId={entityFieldId} "
                            + "fieldMetaEntityId={fieldMetaEntityId} leftEntityId={leftEntityId} "
                            + "rightEntityId={rightEntityId} joinId={joinId}",
                    "metaTableId", "name", "entityFieldId", "fieldMetaEntityId",
                    "leftEntityId", "rightEntityId", "joinId");
    /** 跨 querySpace（跨库）entity-entity JOIN 聚合 deferred（需应用层先拼接再内存聚合）→ 显式失败。 */
    static final ErrorCode ERR_AGGR_JOIN_CROSS_QUERY_SPACE =
            ErrorCode.define("metadata.aggr-join-cross-query-space-deferred",
                    "Cross-querySpace (cross-DB) entity-entity JOIN aggregation is deferred (needs app-layer "
                            + "merge + in-memory group-by): {joinId} leftQuerySpace={leftQuerySpace} "
                            + "rightQuerySpace={rightQuerySpace}",
                    "joinId", "leftQuerySpace", "rightQuerySpace");
    /**
     * EQL 编译失败（可能为 EQL 保留字物理列名，如 PRECISION/SCALE/NUMBER）→ 显式失败并给出迁移指引（不静默退化）。
     * 单表 entity 聚合路径同样经 orm().executeQuery，但 JOIN 路径投影两侧任意列、保留字风险更高，故单独 ErrorCode。
     */
    static final ErrorCode ERR_AGGR_JOIN_COMPILE_FAILED =
            ErrorCode.define("metadata.aggr-join-compile-failed",
                    "Entity JOIN aggregation SQL failed to compile via EQL (possible reserved-word physical column "
                            + "name like PRECISION/SCALE/NUMBER, or ambiguous column). Migration: rename the physical "
                            + "column, or model the data as an external/sql table which supports arbitrary column "
                            + "names via withConnection: {joinId} -- {error}",
                    "joinId", "error");

    /**
     * 执行聚合查询。
     *
     * <p>{@code joinId} 为可选 JOIN 聚合参数（plan 0852-1）：
     * <ul>
     *   <li>{@code joinId} 为 null/空 → 单表聚合（既有行为，按 {@code table.tableType} 路由 entity/external/sql）。</li>
     *   <li>{@code joinId} 非空 → entity↔entity JOIN 聚合：经 {@code NopMetaTableJoin} 关联两端点 entity，
     *       Measure/Dimension 经 {@code entityFieldId → metaEntityId} 判定左/右归属后构造 {@code GROUP BY over JOIN}。
     *       仅同库 entity↔entity 支持；external/sql 端点 / 跨库 / self-join / right / 字段不可归属 / EQL 保留字 均显式失败。</li>
     * </ul>
     *
     * @param table           目标逻辑表
     * @param measureNames    选定指标名列表（NopMetaTableMeasure.measureName）
     * @param dimensionNames  选定维度名列表（NopMetaTableDimension.dimensionName）
     * @param userFilter      用户 filter（可为 null）
     * @param joinId          可选 NopMetaTableJoin 主键（null/空 → 单表聚合）
     * @param limit           分页上限（可为 null）
     * @param offset          分页偏移（可为 null）
     * @param ctx             共享依赖上下文
     * @return {@code Map{items:[{维度值, 指标聚合值}]}}
     */
    public Map<String, Object> executeAggregation(NopMetaTable table, List<String> measureNames,
                                                  List<String> dimensionNames, TreeBean userFilter, String joinId,
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

        // JOIN 聚合路径（plan 0852-1）：joinId 非空时走 entity↔entity JOIN 聚合，不复用单表分支
        if (joinId != null && !joinId.isEmpty()) {
            return buildResult(executeJoinAggregation(table, measureNames, dimensionNames,
                    mergedFilter, joinId, limit, offset, ctx));
        }

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

    // ============================ entity↔entity JOIN 聚合（plan 0852-1，orm().executeQuery GROUP BY over JOIN）============================

    /**
     * entity↔entity JOIN 聚合执行（plan 0852-1）：对 {@code NopMetaTableJoin} 定义的关联执行跨表聚合，
     * 所选 Measure + Dimension（可来自左 entity 或经 JOIN 可达的右 entity）经 GROUP BY 聚合返回。
     *
     * <p>同库 entity↔entity 走 DB 侧原生 {@code GROUP BY ... OVER JOIN}（复用 {@link MetaJoinExecutor} 的
     * join 加载/端点解析语义）。每个 Measure/Dimension 经 {@code entityFieldId → NopMetaEntityField.metaEntityId}
     * 判定所属端点（left/right entity）+ 解析物理列，在 SQL 中以 {@code l.col} / {@code r.col} 限定。
     *
     * <p>失败路径显式（Minimum Rules #24，无静默跳过/无静默降级单表/无空 items）：
     * <ul>
     *   <li>join 不存在/不归属/joinType=right/未知 joinType → 由 {@link MetaJoinExecutor#loadValidatedJoin} 抛</li>
     *   <li>任一端点非 entity（external/sql table 端点）→ {@link #ERR_AGGR_JOIN_ENDPOINT_NOT_ENTITY}</li>
     *   <li>self-join（leftEntityId == rightEntityId）→ {@link #ERR_AGGR_JOIN_SELF_JOIN}</li>
     *   <li>跨 querySpace（跨库）→ {@link #ERR_AGGR_JOIN_CROSS_QUERY_SPACE}</li>
     *   <li>字段 metaEntityId 既不等于左也不等于右 entity → {@link #ERR_AGGR_JOIN_FIELD_SIDE_UNRESOLVED}</li>
     *   <li>expression 型 Measure → {@link #ERR_AGGR_EXPRESSION_MEASURE}</li>
     *   <li>EQL 编译失败（保留字物理列名等）→ {@link #ERR_AGGR_JOIN_COMPILE_FAILED}（含迁移指引）</li>
     * </ul>
     */
    private List<Map<String, Object>> executeJoinAggregation(NopMetaTable table, List<String> measureNames,
                                                              List<String> dimensionNames, TreeBean filter,
                                                              String joinId, Long limit, Long offset,
                                                              MetaQueryContext ctx) {
        // 1. 复用 MetaJoinExecutor 的 join 加载/归属/joinType 校验（显式选定「抽取共享」，见 plan Decision）
        NopMetaTableJoin join = joinExecutor.loadValidatedJoin(table, joinId, ctx);

        // 2. 解析左右端点（复用 MetaJoinExecutor.resolveEndpoint，端点解析逻辑唯一来源）
        MetaJoinExecutor.Endpoint leftEp = joinExecutor.resolveEndpoint(join, "left",
                join.getLeftEntityId(), join.getLeftTableId(), ctx);
        MetaJoinExecutor.Endpoint rightEp = joinExecutor.resolveEndpoint(join, "right",
                join.getRightEntityId(), join.getRightTableId(), ctx);

        // 3. 端点类型守卫：两端点必须均为 entity（external/sql JOIN 聚合 deferred）
        if (!leftEp.isEntity) {
            throw new NopException(ERR_AGGR_JOIN_ENDPOINT_NOT_ENTITY)
                    .param("joinId", joinId).param("side", "left")
                    .param("endpointType", endpointTypeOf(leftEp));
        }
        if (!rightEp.isEntity) {
            throw new NopException(ERR_AGGR_JOIN_ENDPOINT_NOT_ENTITY)
                    .param("joinId", joinId).param("side", "right")
                    .param("endpointType", endpointTypeOf(rightEp));
        }
        NopMetaEntity leftEntity = leftEp.entity;
        NopMetaEntity rightEntity = rightEp.entity;

        // 4. 实体注册校验（复用 MetaJoinExecutor.requireRegistered）
        joinExecutor.requireRegistered(leftEntity, "left", joinId, ctx);
        joinExecutor.requireRegistered(rightEntity, "right", joinId, ctx);

        // 5. self-join 守卫：leftEntityId == rightEntityId → 字段归属两侧均命中、无法表达右别名，显式失败
        if (equalsStr(leftEntity.getMetaEntityId(), rightEntity.getMetaEntityId())) {
            throw new NopException(ERR_AGGR_JOIN_SELF_JOIN)
                    .param("joinId", joinId).param("entityId", leftEntity.getMetaEntityId());
        }

        // 6. 跨 querySpace 守卫：跨库 entity-entity JOIN 聚合 deferred
        String leftQs = leftEntity.getQuerySpace();
        String rightQs = rightEntity.getQuerySpace();
        if (!equalsStr(leftQs, rightQs)) {
            throw new NopException(ERR_AGGR_JOIN_CROSS_QUERY_SPACE)
                    .param("joinId", joinId)
                    .param("leftQuerySpace", String.valueOf(leftQs))
                    .param("rightQuerySpace", String.valueOf(rightQs));
        }

        // 7. 物理表 + 属性名→物理列映射（左右 entity 各一份）
        String leftPhysical = requireName(leftEntity.getTableName(), "leftTableName");
        String rightPhysical = requireName(rightEntity.getTableName(), "rightTableName");
        FilterToSqlTranslator.validateIdentifier(leftPhysical);
        FilterToSqlTranslator.validateIdentifier(rightPhysical);
        Map<String, String> leftPropToCol = resolveEntityColumns(leftEntity, ctx);
        Map<String, String> rightPropToCol = resolveEntityColumns(rightEntity, ctx);

        // 8. join 字段（属性名）→ 物理列（复用 MetaJoinExecutor.resolveFieldToColumn）
        String leftJoinCol = joinExecutor.resolveFieldToColumn(leftPropToCol, join.getLeftField(),
                leftEntity, "left", joinId);
        String rightJoinCol = joinExecutor.resolveFieldToColumn(rightPropToCol, join.getRightField(),
                rightEntity, "right", joinId);
        FilterToSqlTranslator.validateIdentifier(leftJoinCol);
        FilterToSqlTranslator.validateIdentifier(rightJoinCol);

        // 9. 加载 Measure/Dimension 并按 metaEntityId 判定左/右归属 + 解析物理列（含别名前缀 l./r.）
        String leftEntityId = leftEntity.getMetaEntityId();
        String rightEntityId = rightEntity.getMetaEntityId();
        JoinFieldResolver resolver = new JoinFieldResolver(leftEntityId, rightEntityId, joinId, table, ctx);
        List<JoinMeasureSpec> measures = loadJoinMeasures(table, measureNames, ctx, resolver);
        List<JoinDimensionSpec> dims = loadJoinDimensions(table, dimensionNames, ctx, resolver);

        // 10. 构造 JOIN 聚合 SQL：SELECT <group l./r. cols>, <agg(l./r. col)> FROM <l> JOIN <r> ON ... [WHERE] GROUP BY ...
        // 注：裸物理列已在 loadJoinMeasures/loadJoinDimensions 中经白名单校验（防注入）；
        // 此处 qualifiedCol 形如 "l.DISPLAY_NAME"（含别名前缀），不再整体校验（含点号）。
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> groupExprs = new ArrayList<>();
        for (int i = 0; i < dims.size(); i++) {
            JoinDimensionSpec d = dims.get(i);
            // entity JOIN 路径时间分桶受 EQL 函数白名单限制（与单表 entity 路径一致），首版按物理列直查
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
        if (filter != null) {
            // filter 叶子字段名（左表属性名）重写为左表物理列（与 executeSameDbJoin 一致，不限定别名，
            // 左表列在 JOIN 中通常非歧义；若歧义 EQL 会显式编译失败经 ERR_AGGR_JOIN_COMPILE_FAILED 抛出）
            TreeBean colFilter = rewriteFilterToColumns(filter, leftPropToCol);
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
        LOG.info("queryAggregation entity JOIN SQL: {}", sqlText);
        SQL sqlObj = SQL.begin().allowUnderscoreName(true).sql(sqlText, params.toArray()).end();
        // EQL 保留字风险：JOIN 路径投影两侧任意 measure/dimension 物理列，遇保留字（PRECISION/SCALE/NUMBER 等）
        // EQL 编译失败 → 显式失败并给出迁移指引（不静默退化单表、不静默空 items）
        try {
            return ctx.orm().executeQuery(sqlObj, null, this::collectRows);
        } catch (Exception e) {
            throw new NopException(ERR_AGGR_JOIN_COMPILE_FAILED)
                    .param("joinId", joinId)
                    .param("error", messageOf(e))
                    .cause(e);
        }
    }

    private static String endpointTypeOf(MetaJoinExecutor.Endpoint ep) {
        if (ep.isEntity) {
            return "entity";
        }
        return ep.table == null ? "unknown" : String.valueOf(ep.table.getTableType());
    }

    /** 加载 JOIN 聚合的 Measure 规格列表（按 metaEntityId 判定左/右归属 + 物理列解析）。 */
    private List<JoinMeasureSpec> loadJoinMeasures(NopMetaTable table, List<String> names, MetaQueryContext ctx,
                                                    JoinFieldResolver resolver) {
        List<NopMetaTableMeasure> all = loadMeasures(table, names, ctx);
        List<JoinMeasureSpec> specs = new ArrayList<>();
        for (NopMetaTableMeasure m : all) {
            if (m.getExpression() != null && !m.getExpression().trim().isEmpty()) {
                throw new NopException(ERR_AGGR_EXPRESSION_MEASURE)
                        .param("metaTableId", table.getMetaTableId()).param("measureName", m.getMeasureName());
            }
            JoinField f = resolver.resolve(m.getEntityFieldId(), m.getMeasureName());
            FilterToSqlTranslator.validateIdentifier(f.column);
            specs.add(new JoinMeasureSpec(safeAlias(m.getMeasureName()),
                    aggSqlOf(m.getAggFunc(), f.qualifiedColumn, m.getMeasureName()), f.qualifiedColumn));
        }
        return specs;
    }

    /** 加载 JOIN 聚合的 Dimension 规格列表（按 metaEntityId 判定左/右归属 + 物理列解析）。 */
    private List<JoinDimensionSpec> loadJoinDimensions(NopMetaTable table, List<String> names, MetaQueryContext ctx,
                                                        JoinFieldResolver resolver) {
        List<NopMetaTableDimension> all = loadDimensions(table, names, ctx);
        List<JoinDimensionSpec> specs = new ArrayList<>();
        for (NopMetaTableDimension d : all) {
            JoinField f = resolver.resolve(d.getEntityFieldId(), d.getDimensionName());
            FilterToSqlTranslator.validateIdentifier(f.column);
            specs.add(new JoinDimensionSpec(safeAlias(d.getDimensionName()), f.qualifiedColumn,
                    d.getDimensionType(), d.getGranularity()));
        }
        return specs;
    }

    private static boolean equalsStr(String a, String b) {
        return (a == null) ? b == null : a.equals(b);
    }

    /**
     * JOIN 字段归属解析器：按 {@code entityFieldId → NopMetaEntityField.metaEntityId} 判定字段属于左/右 entity，
     * 解析物理列并构造限定别名（{@code l.<col>} / {@code r.<col>}）。不可归属显式失败。
     */
    private static final class JoinFieldResolver {
        private final String leftEntityId;
        private final String rightEntityId;
        private final String joinId;
        private final NopMetaTable table;
        private final MetaQueryContext ctx;

        JoinFieldResolver(String leftEntityId, String rightEntityId, String joinId,
                          NopMetaTable table, MetaQueryContext ctx) {
            this.leftEntityId = leftEntityId;
            this.rightEntityId = rightEntityId;
            this.joinId = joinId;
            this.table = table;
            this.ctx = ctx;
        }

        JoinField resolve(String entityFieldId, String name) {
            if (entityFieldId == null || entityFieldId.isEmpty()) {
                throw new NopException(ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
            }
            IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
            NopMetaEntityField field = fieldDao.getEntityById(entityFieldId);
            if (field == null || field.getColumnCode() == null) {
                throw new NopException(ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", name).param("entityFieldId", entityFieldId);
            }
            String fieldMetaEntityId = field.getMetaEntityId();
            String column = field.getColumnCode();
            String alias;
            if (equalsStr(fieldMetaEntityId, leftEntityId)) {
                alias = "l";
            } else if (equalsStr(fieldMetaEntityId, rightEntityId)) {
                alias = "r";
            } else {
                // 字段 metaEntityId 既不等于左也不等于右 entity → 显式失败（不静默归属左/不静默跳过）
                throw new NopException(ERR_AGGR_JOIN_FIELD_SIDE_UNRESOLVED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", name).param("entityFieldId", entityFieldId)
                        .param("fieldMetaEntityId", String.valueOf(fieldMetaEntityId))
                        .param("leftEntityId", String.valueOf(leftEntityId))
                        .param("rightEntityId", String.valueOf(rightEntityId))
                        .param("joinId", joinId);
            }
            return new JoinField(column, alias + "." + column);
        }

        private static boolean equalsStr(String a, String b) {
            return (a == null) ? b == null : a.equals(b);
        }
    }

    /** JOIN Measure 规格：别名 + 聚合 SQL 表达式（含 table-qualified 列）+ 限定列（供白名单校验）。 */
    private static final class JoinMeasureSpec {
        final String alias;
        final String aggSql;
        final String qualifiedAggCol;

        JoinMeasureSpec(String alias, String aggSql, String qualifiedAggCol) {
            this.alias = alias;
            this.aggSql = aggSql;
            this.qualifiedAggCol = qualifiedAggCol;
        }
    }

    /** JOIN Dimension 规格：别名 + table-qualified 列表达式 + 类型 + granularity。 */
    private static final class JoinDimensionSpec {
        final String alias;
        final String qualifiedCol;
        final String dimensionType;
        final String granularity;

        JoinDimensionSpec(String alias, String qualifiedCol, String dimensionType, String granularity) {
            this.alias = alias;
            this.qualifiedCol = qualifiedCol;
            this.dimensionType = dimensionType;
            this.granularity = granularity;
        }
    }

    /** JOIN 字段解析结果：物理列 + table-qualified 限定列（l.col / r.col）。 */
    private static final class JoinField {
        final String column;
        final String qualifiedColumn;

        JoinField(String column, String qualifiedColumn) {
            this.column = column;
            this.qualifiedColumn = qualifiedColumn;
        }
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
