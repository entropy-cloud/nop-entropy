package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableFilter;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 指标/维度聚合查询执行器（架构基线 §4.4.2，落地 D6/D7）。
 *
 * <p>按 tableType 路由聚合执行（D6）：
 * <ul>
 *   <li>{@code external}/{@code sql}：经 ExternalAggregationProcessor / SqlAggregationProcessor 跑原生聚合 SQL。</li>
 *   <li>{@code entity}：经 EntityAggregationProcessor 跑聚合 SQL。</li>
 *   <li>JOIN 聚合：经对应 Join Processor 按端点组合路由。</li>
 * </ul>
 *
 * <p>aggFunc 翻译：sum/count/avg/min/max/countDistinct。时间维度 granularity 按 {@link GranularityBucketing}
 * 翻译为 SQL 分桶表达式（D7，仅 external/sql 路径完整支持）。
 *
 * <p>失败路径显式（Minimum Rules #24）：无 measure/dimension / 字段无法解析 / aggFunc 不支持 /
 * granularity 不约定 / 方言不支持 / expression unparseable/unsafe/dialect-unsupported/too-long / 实体未注册。
 */
public class MetaAggregationExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(MetaAggregationExecutor.class);

    private final MetaJoinExecutor joinExecutor;

    public MetaAggregationExecutor() {
        this(new MetaJoinExecutor());
    }

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

    public static final ErrorCode ERR_AGGR_EXPRESSION_UNPARSEABLE =
            ErrorCode.define("metadata.aggr-expression-unparseable",
                    "Expression measure text is unparseable (unbalanced parenthesis / illegal token / "
                            + "statement terminator / suspicious comment): "
                            + "{metaTableId} measureName={measureName} expression={expression} error={error}",
                    "metaTableId", "measureName", "expression", "error");
    public static final ErrorCode ERR_AGGR_EXPRESSION_UNSAFE =
            ErrorCode.define("metadata.aggr-expression-unsafe",
                    "Expression measure text is unsafe (contains forbidden keyword/function, or identifier "
                            + "fails whitelist, or join context requires l./r. qualifier): "
                            + "{metaTableId} measureName={measureName} expression={expression} reason={reason}",
                    "metaTableId", "measureName", "expression", "reason");
    public static final ErrorCode ERR_AGGR_EXPRESSION_DIALECT_UNSUPPORTED =
            ErrorCode.define("metadata.aggr-expression-dialect-unsupported",
                    "Expression measure uses function/operator not supported by current dialect: "
                            + "{metaTableId} measureName={measureName} expression={expression} "
                            + "databaseProductName={databaseProductName} unsupportedToken={unsupportedToken}",
                    "metaTableId", "measureName", "expression", "databaseProductName", "unsupportedToken");
    public static final ErrorCode ERR_AGGR_EXPRESSION_MEMORY_NOT_COMPUTABLE =
            ErrorCode.define("metadata.aggr-expression-memory-not-computable",
                    "Expression-type measure is not computable in cross-DB in-memory GROUP BY path "
                            + "(aligned with D10 in-memory aggFunc computability rule): "
                            + "{metaTableId} measureName={measureName} joinId={joinId}",
                    "metaTableId", "measureName", "joinId");
    public static final ErrorCode ERR_AGGR_EXPRESSION_TOO_LONG =
            ErrorCode.define("metadata.aggr-expression-too-long",
                    "Expression measure text exceeds VARCHAR(1000) capacity limit (not truncated, "
                            + "not silently stored): {metaTableId} measureName={measureName} length={length} limit={limit}",
                    "metaTableId", "measureName", "length", "limit");
    public static final ErrorCode ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED =
            ErrorCode.define("metadata.aggr-expression-having-order-by-unsupported",
                    "Expression-type measure is referenced by HAVING or ORDER BY name "
                            + "(first version explicitly fails to avoid ? re-injection parameter count mismatch): "
                            + "{metaTableId} measureName={measureName} clause={clause}",
                    "metaTableId", "measureName", "clause");
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

    static final ErrorCode ERR_AGGR_JOIN_MIXED_ENDPOINT_DEFERRED =
            ErrorCode.define("metadata.aggr-join-mixed-endpoint-deferred",
                    "Mixed-endpoint (entity<->external/sql) JOIN aggregation is deferred (cross-connection mechanism "
                            + "needed, app-layer merge truncates so GROUP BY would be approximate): "
                            + "{joinId} leftEndpointType={leftEndpointType} rightEndpointType={rightEndpointType}",
                    "joinId", "leftEndpointType", "rightEndpointType");
    static final ErrorCode ERR_AGGR_JOIN_MIXED_CROSS_DB_DEFERRED =
            ErrorCode.define("metadata.aggr-join-mixed-cross-db-deferred",
                    "Mixed-endpoint JOIN aggregation: entity physical table is not visible in the selected external "
                            + "connection (cross-DB, querySpace string is unreliable for mixed endpoints). Same-DB "
                            + "aggregation needs a single shared connection. Deferred to cross-DB successor plan "
                            + "(app-layer merge + in-memory group-by would truncate and be approximate): "
                            + "{joinId} entityPhysicalTable={entityPhysicalTable} entitySchema={entitySchema} "
                            + "externalQuerySpace={externalQuerySpace}",
                    "joinId", "entityPhysicalTable", "entitySchema", "externalQuerySpace");
    static final ErrorCode ERR_AGGR_JOIN_MIXED_ENTITY_TABLE_EMPTY =
            ErrorCode.define("metadata.aggr-join-mixed-entity-table-empty",
                    "Mixed-endpoint JOIN aggregation: entity physical table name (NopMetaEntity.tableName) is empty, "
                            + "cannot build FROM clause: {joinId} entityId={entityId}",
                    "joinId", "entityId");
    static final ErrorCode ERR_AGGR_JOIN_SIDE_REQUIRED =
            ErrorCode.define("metadata.aggr-join-side-required",
                    "Measure/Dimension side is required for external/sql join endpoint at query-time "
                            + "(null not allowed, column attribution ambiguous): {metaTableId} name={name} joinId={joinId}",
                    "metaTableId", "name", "joinId");
    static final ErrorCode ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE =
            ErrorCode.define("metadata.aggr-join-field-not-on-side",
                    "Measure/Dimension column does not exist on the endpoint resolved field set for the given side: "
                            + "{metaTableId} name={name} side={side} endpointTableType={endpointTableType} column={column} "
                            + "joinId={joinId}", "metaTableId", "name", "side", "endpointTableType", "column", "joinId");
    static final ErrorCode ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH =
            ErrorCode.define("metadata.aggr-join-entity-side-mismatch",
                    "Measure/Dimension side is inconsistent with entityFieldId->metaEntityId attribution on entity endpoint: "
                            + "{metaTableId} name={name} declaredSide={declaredSide} resolvedSide={resolvedSide} "
                            + "fieldMetaEntityId={fieldMetaEntityId} joinId={joinId}",
                    "metaTableId", "name", "declaredSide", "resolvedSide", "fieldMetaEntityId", "joinId");
    static final ErrorCode ERR_AGGR_JOIN_EXTERNAL_CROSS_QUERY_SPACE =
            ErrorCode.define("metadata.aggr-join-external-cross-query-space-deferred",
                    "Cross-querySpace (cross-DB) external<->external JOIN aggregation is deferred (needs app-layer "
                            + "merge + in-memory group-by): {joinId} leftQuerySpace={leftQuerySpace} "
                            + "rightQuerySpace={rightQuerySpace}",
                    "joinId", "leftQuerySpace", "rightQuerySpace");
    static final ErrorCode ERR_AGGR_JOIN_SELF_JOIN =
            ErrorCode.define("metadata.aggr-join-self-join-unsupported",
                    "Self-join (leftEntityId == rightEntityId) is not supported for JOIN aggregation: "
                            + "field attribution to left/right alias is ambiguous: {joinId} entityId={entityId}",
                    "joinId", "entityId");
    static final ErrorCode ERR_AGGR_JOIN_FIELD_SIDE_UNRESOLVED =
            ErrorCode.define("metadata.aggr-join-field-side-unresolved",
                    "Measure/Dimension entityFieldId does not belong to either left or right entity of the join "
                            + "(metaEntityId mismatch): {metaTableId} name={name} entityFieldId={entityFieldId} "
                            + "fieldMetaEntityId={fieldMetaEntityId} leftEntityId={leftEntityId} "
                            + "rightEntityId={rightEntityId} joinId={joinId}",
                    "metaTableId", "name", "entityFieldId", "fieldMetaEntityId",
                    "leftEntityId", "rightEntityId", "joinId");
    static final ErrorCode ERR_AGGR_JOIN_CROSS_QUERY_SPACE =
            ErrorCode.define("metadata.aggr-join-cross-query-space-deferred",
                    "Cross-querySpace (cross-DB) entity-entity JOIN aggregation is deferred (needs app-layer "
                            + "merge + in-memory group-by): {joinId} leftQuerySpace={leftQuerySpace} "
                            + "rightQuerySpace={rightQuerySpace}",
                    "joinId", "leftQuerySpace", "rightQuerySpace");
    static final ErrorCode ERR_AGGR_JOIN_COMPILE_FAILED =
            ErrorCode.define("metadata.aggr-join-compile-failed",
                    "Entity JOIN aggregation SQL failed to compile via EQL (possible reserved-word physical column "
                            + "name like PRECISION/SCALE/NUMBER, or ambiguous column). Migration: rename the physical "
                            + "column, or model the data as an external/sql table which supports arbitrary column "
                            + "names via withConnection: {joinId} -- {error}",
                    "joinId", "error");

    static final ErrorCode ERR_AGGR_CROSS_DB_FIELD_KEY_MISSING =
            ErrorCode.define("metadata.aggr-cross-db-field-key-missing",
                    "Cross-DB JOIN aggregation: measure/dimension lookup key not found in executeJoin merged row "
                            + "(would silently produce null/0). Explicit failure per D10 namespace rule "
                            + "(entity=fieldName, table=physical column, right-side conflict=<alias>_<name>): "
                            + "{metaTableId} name={name} fieldKind={fieldKind} rawKey={rawKey} lookupKey={lookupKey} "
                            + "rowKeys={rowKeys} joinId={joinId}",
                    "metaTableId", "name", "fieldKind", "rawKey", "lookupKey", "rowKeys", "joinId");

    static final ErrorCode ERR_AGGR_HAVING_UNKNOWN_NAME =
            ErrorCode.define("metadata.aggr-having-unknown-name",
                    "having references a measure/dimension name not in the user-selected measures/dimensions set: "
                            + "{metaTableId} name={name} selectedMeasures={selectedMeasures} selectedDimensions={selectedDimensions}",
                    "metaTableId", "name", "selectedMeasures", "selectedDimensions");
    static final ErrorCode ERR_AGGR_ORDER_BY_UNKNOWN_NAME =
            ErrorCode.define("metadata.aggr-order-by-unknown-name",
                    "orderBy references a measure/dimension name not in the user-selected measures/dimensions set: "
                            + "{metaTableId} name={name} selectedMeasures={selectedMeasures} selectedDimensions={selectedDimensions}",
                    "metaTableId", "name", "selectedMeasures", "selectedDimensions");
    static final ErrorCode ERR_AGGR_HAVING_UNSUPPORTED_OP =
            ErrorCode.define("metadata.aggr-having-unsupported-op",
                    "MemoryFilterEvaluator: having op not supported in first version: {op} name={name}",
                    "op", "name");

    static final ErrorCode ERR_AGGR_HAVING_EXPR_UNPARSEABLE =
            ErrorCode.define("metadata.aggr-having-expr-unparseable",
                    "Multi-column arithmetic HAVING expression is unparseable (post-substitution SQL fragment fails "
                            + "ExpressionMeasureValidator parse): {metaTableId} expr={expr} error={error}",
                    "metaTableId", "expr", "error");
    static final ErrorCode ERR_AGGR_HAVING_EXPR_UNSAFE =
            ErrorCode.define("metadata.aggr-having-expr-unsafe",
                    "Multi-column arithmetic HAVING expression is unsafe (contains forbidden keyword/function, "
                            + "identifier fails whitelist, or contains literal (Phase 1 disallows literals, "
                            + "only measure-name arithmetic allowed)): "
                            + "{metaTableId} expr={expr} reason={reason}",
                    "metaTableId", "expr", "reason");
    static final ErrorCode ERR_AGGR_HAVING_EXPR_MEMORY_NOT_COMPUTABLE =
            ErrorCode.define("metadata.aggr-having-expr-memory-not-computable",
                    "Multi-column arithmetic HAVING expression is not computable in cross-DB in-memory GROUP BY path "
                            + "(aligned with D12.2 in-memory aggFunc computability rule; "
                            + "arithmetic of multiple measures requires SQL pushdown): "
                            + "{metaTableId} expr={expr}",
                    "metaTableId", "expr");

    /**
     * 执行聚合查询（分派入口）。
     */
    public Map<String, Object> executeAggregation(NopMetaTable table, List<String> measureNames,
                                                   List<String> dimensionNames, TreeBean userFilter, String joinId,
                                                   Long limit, Long offset, TreeBean having,
                                                   List<OrderFieldBean> orderBy, MetaQueryContext ctx) {
        if (measureNames == null || measureNames.isEmpty()) {
            throw new NopException(ERR_AGGR_NO_MEASURE).param("metaTableId", table.getMetaTableId());
        }
        if (dimensionNames == null || dimensionNames.isEmpty()) {
            throw new NopException(ERR_AGGR_NO_DIMENSION).param("metaTableId", table.getMetaTableId());
        }
        IEntityDao<NopMetaTableFilter> filterDao = ctx.daoProvider().daoFor(NopMetaTableFilter.class);
        TreeBean mergedFilter = DefaultFilterApplicator.applyDefaults(table, userFilter, filterDao);

        // Build shared AggregationContext
        AggregationContext aggrCtx = new AggregationContext(ctx, joinExecutor);
        aggrCtx.setTable(table);
        aggrCtx.setMeasureNames(measureNames);
        aggrCtx.setDimensionNames(dimensionNames);
        aggrCtx.setFilter(mergedFilter);
        aggrCtx.setJoinId(joinId);
        aggrCtx.setLimit(limit);
        aggrCtx.setOffset(offset);
        aggrCtx.setHaving(having);
        aggrCtx.setOrderBy(orderBy);

        if (joinId != null && !joinId.isEmpty()) {
            return AggregationContext.buildResult(executeJoinAggregation(aggrCtx, table, measureNames, dimensionNames,
                    mergedFilter, joinId, limit, offset, having, orderBy, ctx));
        }

        String tableType = table.getTableType();
        AggregationProcessor processor;
        if (_NopMetadataCoreConstants.TABLE_TYPE_ENTITY.equals(tableType)) {
            processor = new EntityAggregationProcessor();
        } else if (_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL.equals(tableType)) {
            processor = new ExternalAggregationProcessor();
        } else if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(tableType)) {
            processor = new SqlAggregationProcessor();
        } else {
            throw new NopException(ERR_AGGR_EXEC_FAILED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("error", "unsupported tableType: " + tableType);
        }
        return AggregationContext.buildResult(processor.execute(aggrCtx));
    }

    /**
     * JOIN 聚合执行入口（端点组合路由）：加载/校验 join + 解析端点后，按端点组合分派到对应 Processor。
     */
    private List<Map<String, Object>> executeJoinAggregation(AggregationContext aggrCtx, NopMetaTable table,
                                                              List<String> measureNames, List<String> dimensionNames,
                                                              TreeBean filter, String joinId, Long limit, Long offset,
                                                              TreeBean having, List<OrderFieldBean> orderBy,
                                                              MetaQueryContext ctx) {
        NopMetaTableJoin join = joinExecutor.loadValidatedJoin(table, joinId, ctx);

        MetaJoinExecutor.Endpoint leftEp = joinExecutor.resolveEndpoint(join, "left",
                join.getLeftEntityId(), join.getLeftTableId(), ctx);
        MetaJoinExecutor.Endpoint rightEp = joinExecutor.resolveEndpoint(join, "right",
                join.getRightEntityId(), join.getRightTableId(), ctx);

        aggrCtx.setJoin(join);
        aggrCtx.setLeftEndpoint(leftEp);
        aggrCtx.setRightEndpoint(rightEp);

        if (leftEp.isEntity() && rightEp.isEntity()) {
            String leftQs = leftEp.entity.getQuerySpace();
            String rightQs = rightEp.entity.getQuerySpace();
            if (!AggregationContext.equalsStr(leftQs, rightQs)) {
                return new CrossDbInMemoryAggregationProcessor().execute(aggrCtx);
            }
            return new EntityEntityJoinAggregationProcessor().execute(aggrCtx);
        }
        if (!leftEp.isEntity() && !rightEp.isEntity()) {
            String leftQs = leftEp.table.getQuerySpace();
            String rightQs = rightEp.table.getQuerySpace();
            if (!AggregationContext.equalsStr(leftQs, rightQs)) {
                return new CrossDbInMemoryAggregationProcessor().execute(aggrCtx);
            }
            return new ExternalExternalJoinAggregationProcessor().execute(aggrCtx);
        }
        return new MixedSameDbJoinAggregationProcessor().execute(aggrCtx);
    }

    // ============================ 多列算术 having 预处理（plan 2026-07-18-1500-2）============================

    private static final Pattern HAVING_EXPR_NAME_TOKEN =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public static void preprocessHavingArithmetic(TreeBean having, Map<String, String> nameToExpr,
                                                   NopMetaTable table, List<String> measureNames,
                                                   List<String> dimensionNames) {
        if (having == null) {
            return;
        }
        Object exprAttr = having.getAttr(HAVING_EXPR_ATTR);
        if (exprAttr != null && !exprAttr.toString().isEmpty()) {
            String userExpr = exprAttr.toString();
            String finalSql = substituteAndValidateHavingExpr(userExpr, nameToExpr, table,
                    measureNames, dimensionNames);
            having.setAttr(FilterBeanConstants.FILTER_ATTR_NAME, finalSql);
            return;
        }
        List<TreeBean> children = having.getChildren();
        if (children == null || children.isEmpty()) {
            return;
        }
        for (TreeBean child : children) {
            preprocessHavingArithmetic(child, nameToExpr, table, measureNames, dimensionNames);
        }
    }

    public static final String HAVING_EXPR_ATTR = "expr";

    static String substituteAndValidateHavingExpr(String userExpr, Map<String, String> nameToExpr,
                                                    NopMetaTable table, List<String> measureNames,
                                                    List<String> dimensionNames) {
        Matcher m = HAVING_EXPR_NAME_TOKEN.matcher(userExpr);
        StringBuilder out = new StringBuilder(userExpr.length() + 32);
        int last = 0;
        while (m.find()) {
            out.append(userExpr, last, m.start());
            String token = m.group();
            String aggSql = nameToExpr.get(token);
            if (aggSql == null) {
                throw new NopException(ERR_AGGR_HAVING_UNKNOWN_NAME)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", token)
                        .param("selectedMeasures", String.valueOf(measureNames))
                        .param("selectedDimensions", String.valueOf(dimensionNames));
            }
            if (aggSql.indexOf('?') >= 0) {
                throw new NopException(ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("measureName", token)
                        .param("clause", "HAVING");
            }
            out.append(aggSql);
            last = m.end();
        }
        out.append(userExpr, last, userExpr.length());
        String finalSql = out.toString();

        ExpressionMeasureValidator.ValidatedExpression ve;
        try {
            ve = ExpressionMeasureValidator.validateStatic(finalSql,
                    ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(),
                    table.getMetaTableId(), "<having-arithmetic>");
        } catch (NopException e) {
            if (ERR_AGGR_EXPRESSION_UNPARSEABLE.getErrorCode().equals(e.getErrorCode())) {
                throw new NopException(ERR_AGGR_HAVING_EXPR_UNPARSEABLE, e)
                        .param("metaTableId", table.getMetaTableId())
                        .param("expr", userExpr)
                        .param("error", String.valueOf(e.getParam("error")));
            }
            throw new NopException(ERR_AGGR_HAVING_EXPR_UNSAFE, e)
                    .param("metaTableId", table.getMetaTableId())
                    .param("expr", userExpr)
                    .param("reason", String.valueOf(e.getParam("reason")));
        }
        if (ve.params != null && !ve.params.isEmpty()) {
            throw new NopException(ERR_AGGR_HAVING_EXPR_UNSAFE)
                    .param("metaTableId", table.getMetaTableId())
                    .param("expr", userExpr)
                    .param("reason", "literals are not allowed in having arithmetic expression "
                            + "(Phase 1 only allows measure-name arithmetic combination): "
                            + "params=" + ve.params);
        }
        return finalSql;
    }

    static boolean containsHavingArithmeticLeaf(TreeBean having) {
        if (having == null) {
            return false;
        }
        Object exprAttr = having.getAttr(HAVING_EXPR_ATTR);
        if (exprAttr != null && !exprAttr.toString().isEmpty()) {
            return true;
        }
        List<TreeBean> children = having.getChildren();
        if (children == null || children.isEmpty()) {
            return false;
        }
        for (TreeBean child : children) {
            if (containsHavingArithmeticLeaf(child)) {
                return true;
            }
        }
        return false;
    }
}
