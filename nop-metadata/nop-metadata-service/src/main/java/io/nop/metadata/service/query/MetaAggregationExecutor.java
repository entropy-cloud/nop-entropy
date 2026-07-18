package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConstants;
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
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import io.nop.metadata.service.field.ResolvedTableField;
import io.nop.metadata.service.tableref.TableReference;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
 *   <li>{@code external}/{@code sql}：经 {@code withConnection} 跑原生聚合 SQL（GROUP BY 维度 + aggFunc 指标）。</li>
 *   <li>{@code entity}：经 {@code orm().executeQuery} 跑原生聚合 SQL（物理表 + 物理列 columnCode，
 *       {@code allowUnderscoreName}）。</li>
 * </ul>
 *
 * <p>aggFunc 翻译：sum/count/avg/min/max/countDistinct。时间维度 granularity 按 {@link GranularityBucketing}
 * 翻译为 SQL 分桶表达式（D7，仅 external/sql 路径完整支持）。{@code expression} 型 Measure 三路径执行
 * （plan 2026-07-18-1400-1，§4.4.2 D12）：entity 路径 bypass EQL + external-sql withConnection + JOIN 同库注入，
 * 跨库内存路径显式失败（{@link #ERR_AGGR_EXPRESSION_MEMORY_NOT_COMPUTABLE}）。
 *
 * <p>失败路径显式（Minimum Rules #24）：无 measure/dimension / 字段无法解析 / aggFunc 不支持 /
 * granularity 不约定 / 方言不支持 / expression unparseable/unsafe/dialect-unsupported/too-long / 实体未注册。
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

    // ===== expression 型 Measure 错误码体系（§4.4.2 D12.4 + D12.5 + R2 HAVING/ORDER BY 交互，plan 2026-07-18-1400-1 落地）=====

    /** expression 文本不可解析（语法不合法，如未闭合括号、非法 token、语句终止符）。抛点：parse 阶段。 */
    public static final ErrorCode ERR_AGGR_EXPRESSION_UNPARSEABLE =
            ErrorCode.define("metadata.aggr-expression-unparseable",
                    "Expression measure text is unparseable (unbalanced parenthesis / illegal token / "
                            + "statement terminator / suspicious comment): "
                            + "{metaTableId} measureName={measureName} expression={expression} error={error}",
                    "metaTableId", "measureName", "expression", "error");
    /** expression 文本不安全（含关键字/函数黑名单中的危险项，或列引用未通过标识符白名单）。抛点：parse 阶段 + identifier 校验阶段。 */
    public static final ErrorCode ERR_AGGR_EXPRESSION_UNSAFE =
            ErrorCode.define("metadata.aggr-expression-unsafe",
                    "Expression measure text is unsafe (contains forbidden keyword/function, or identifier "
                            + "fails whitelist, or join context requires l./r. qualifier): "
                            + "{metaTableId} measureName={measureName} expression={expression} reason={reason}",
                    "metaTableId", "measureName", "expression", "reason");
    /** expression 使用了当前方言不支持的函数/运算符（如 MySQL 不支持 DATE_TRUNC）。抛点：执行阶段（dialect 取得后）。 */
    public static final ErrorCode ERR_AGGR_EXPRESSION_DIALECT_UNSUPPORTED =
            ErrorCode.define("metadata.aggr-expression-dialect-unsupported",
                    "Expression measure uses function/operator not supported by current dialect: "
                            + "{metaTableId} measureName={measureName} expression={expression} "
                            + "databaseProductName={databaseProductName} unsupportedToken={unsupportedToken}",
                    "metaTableId", "measureName", "expression", "databaseProductName", "unsupportedToken");
    /** expression 型 Measure 在跨库内存路径无法内存求值（对齐 D10 铁律）。抛点：跨库内存聚合入口。 */
    public static final ErrorCode ERR_AGGR_EXPRESSION_MEMORY_NOT_COMPUTABLE =
            ErrorCode.define("metadata.aggr-expression-memory-not-computable",
                    "Expression-type measure is not computable in cross-DB in-memory GROUP BY path "
                            + "(aligned with D10 in-memory aggFunc computability rule): "
                            + "{metaTableId} measureName={measureName} joinId={joinId}",
                    "metaTableId", "measureName", "joinId");
    /** expression 文本长度超 VARCHAR(1000) 容量上限（不截断、不静默存入）。抛点：save 阶段。 */
    public static final ErrorCode ERR_AGGR_EXPRESSION_TOO_LONG =
            ErrorCode.define("metadata.aggr-expression-too-long",
                    "Expression measure text exceeds VARCHAR(1000) capacity limit (not truncated, "
                            + "not silently stored): {metaTableId} measureName={measureName} length={length} limit={limit}",
                    "metaTableId", "measureName", "length", "limit");
    /**
     * expression 型 Measure 被 HAVING 或 ORDER BY 的 name 引用（首版显式失败，避免含 {@code ?} 的 aggSql 经
     * name→aggSql 反查表重新注入 HAVING/ORDER BY 致参数计数错配）。抛点：name→aggSql 反查表构建 / nameResolver 回调。
     */
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

    // ===== JOIN 聚合专属 ErrorCode（plan 0852-1：entity↔entity 跨表聚合；plan 1200-1：external↔external 同库 + 侧别建模，失败路径显式不静默降级）=====

    /**
     * 混合端点（entity ↔ external/sql）JOIN 聚合 deferred（§4.4.1 D1.2 一律走截断式应用层拼接 D5，聚合语义近似不可正确；
     * §4.4.2 D8 行 1013 已 deferred）→ 显式失败。替代既有 {@code ERR_AGGR_JOIN_ENDPOINT_NOT_ENTITY} 守卫：
     * 守卫已重构为端点组合路由（plan 1200-1 Phase 3 D1），entity↔entity / external↔external 同库 各有执行路径，
     * 仅混合端点仍显式失败。
     *
     * <p>**plan 1500-1 D1.5 收口**：混合端点同库部分已落地（external {@code withConnection} 单连接原生 GROUP BY over JOIN）。
     * 本 ErrorCode 仅保留用于历史/防御性上下文；运行时混合端点分支已改为路由到 {@link #executeMixedSameDbJoinAggregation}，
     * 同库成功路径或不可同库时改抛 {@link #ERR_AGGR_JOIN_MIXED_CROSS_DB_DEFERRED}。
     */
    static final ErrorCode ERR_AGGR_JOIN_MIXED_ENDPOINT_DEFERRED =
            ErrorCode.define("metadata.aggr-join-mixed-endpoint-deferred",
                    "Mixed-endpoint (entity<->external/sql) JOIN aggregation is deferred (cross-connection mechanism "
                            + "needed, app-layer merge truncates so GROUP BY would be approximate): "
                            + "{joinId} leftEndpointType={leftEndpointType} rightEndpointType={rightEndpointType}",
                    "joinId", "leftEndpointType", "rightEndpointType");
    /**
     * 混合端点 JOIN 聚合：entity 物理表在选定 external 连接不可见（不可同库）→ 显式失败（plan 1500-1 D1.5）。
     * querySpace 字符串相等对混合端点语义不可靠，故采用连接可达性实测；不可见即跨库 → 指向 successor plan 1500-2，
     * 不静默降级 D5 拼接近似聚合（GROUP BY 在截断后的拼接集上执行会致 SUM/COUNT/AVG 静默错误）。
     */
    static final ErrorCode ERR_AGGR_JOIN_MIXED_CROSS_DB_DEFERRED =
            ErrorCode.define("metadata.aggr-join-mixed-cross-db-deferred",
                    "Mixed-endpoint JOIN aggregation: entity physical table is not visible in the selected external "
                            + "connection (cross-DB, querySpace string is unreliable for mixed endpoints). Same-DB "
                            + "aggregation needs a single shared connection. Deferred to cross-DB successor plan "
                            + "(app-layer merge + in-memory group-by would truncate and be approximate): "
                            + "{joinId} entityPhysicalTable={entityPhysicalTable} entitySchema={entitySchema} "
                            + "externalQuerySpace={externalQuerySpace}",
                    "joinId", "entityPhysicalTable", "entitySchema", "externalQuerySpace");
    /** 混合端点 entity 物理表名为空（NopMetaEntity.tableName 缺失），无法构造 FROM 子句。 */
    static final ErrorCode ERR_AGGR_JOIN_MIXED_ENTITY_TABLE_EMPTY =
            ErrorCode.define("metadata.aggr-join-mixed-entity-table-empty",
                    "Mixed-endpoint JOIN aggregation: entity physical table name (NopMetaEntity.tableName) is empty, "
                            + "cannot build FROM clause: {joinId} entityId={entityId}",
                    "joinId", "entityId");
    /** external/sql 端点 Measure/Dimension 的 side 缺失（query-time 必填，null 即失败，不依赖是否歧义）。 */
    static final ErrorCode ERR_AGGR_JOIN_SIDE_REQUIRED =
            ErrorCode.define("metadata.aggr-join-side-required",
                    "Measure/Dimension side is required for external/sql join endpoint at query-time "
                            + "(null not allowed, column attribution ambiguous): {metaTableId} name={name} joinId={joinId}",
                    "metaTableId", "name", "joinId");
    /** side 指向端点的解析字段集合不含该列名（external/sql 端点列名存在性校验失败）。 */
    static final ErrorCode ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE =
            ErrorCode.define("metadata.aggr-join-field-not-on-side",
                    "Measure/Dimension column does not exist on the endpoint resolved field set for the given side: "
                            + "{metaTableId} name={name} side={side} endpointTableType={endpointTableType} column={column} "
                            + "joinId={joinId}", "metaTableId", "name", "side", "endpointTableType", "column", "joinId");
    /** entity 端点 side 与 entityFieldId→metaEntityId 判定的端点不一致（若提供 side 须一致）。 */
    static final ErrorCode ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH =
            ErrorCode.define("metadata.aggr-join-entity-side-mismatch",
                    "Measure/Dimension side is inconsistent with entityFieldId->metaEntityId attribution on entity endpoint: "
                            + "{metaTableId} name={name} declaredSide={declaredSide} resolvedSide={resolvedSide} "
                            + "fieldMetaEntityId={fieldMetaEntityId} joinId={joinId}",
                    "metaTableId", "name", "declaredSide", "resolvedSide", "fieldMetaEntityId", "joinId");
    /** 跨 querySpace（跨库）external↔external JOIN 聚合 deferred（需应用层先拼接再内存聚合，语义近似）。 */
    static final ErrorCode ERR_AGGR_JOIN_EXTERNAL_CROSS_QUERY_SPACE =
            ErrorCode.define("metadata.aggr-join-external-cross-query-space-deferred",
                    "Cross-querySpace (cross-DB) external<->external JOIN aggregation is deferred (needs app-layer "
                            + "merge + in-memory group-by): {joinId} leftQuerySpace={leftQuerySpace} "
                            + "rightQuerySpace={rightQuerySpace}",
                    "joinId", "leftQuerySpace", "rightQuerySpace");
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
     * 跨库 JOIN 聚合内存 GROUP BY（plan 1500-2 D10）：measure/dimension 的 key 在 {@code executeJoin} 返回的
     * 合并行 Map 中找不到（命名空间错配或字段不存在）→ 显式失败（Anti-Hollow #24：**绝不静默返回 null/0**，
     * 否则 SUM 静默归零、COUNT 静默漏计）。
     */
    static final ErrorCode ERR_AGGR_CROSS_DB_FIELD_KEY_MISSING =
            ErrorCode.define("metadata.aggr-cross-db-field-key-missing",
                    "Cross-DB JOIN aggregation: measure/dimension lookup key not found in executeJoin merged row "
                            + "(would silently produce null/0). Explicit failure per D10 namespace rule "
                            + "(entity=fieldName, table=physical column, right-side conflict=<alias>_<name>): "
                            + "{metaTableId} name={name} fieldKind={fieldKind} rawKey={rawKey} lookupKey={lookupKey} "
                            + "rowKeys={rowKeys} joinId={joinId}",
                    "metaTableId", "name", "fieldKind", "rawKey", "lookupKey", "rowKeys", "joinId");

    // ===== having/orderBy 增强专属 ErrorCode（plan 2026-07-18-0900-2：聚合后过滤 + 排序）=====

    /** having 引用未选定的 measure/dimension name → 显式失败（不静默跳过该条件）。 */
    static final ErrorCode ERR_AGGR_HAVING_UNKNOWN_NAME =
            ErrorCode.define("metadata.aggr-having-unknown-name",
                    "having references a measure/dimension name not in the user-selected measures/dimensions set: "
                            + "{metaTableId} name={name} selectedMeasures={selectedMeasures} selectedDimensions={selectedDimensions}",
                    "metaTableId", "name", "selectedMeasures", "selectedDimensions");
    /** orderBy 引用未选定的 measure/dimension name → 显式失败（不静默跳过该排序字段）。 */
    static final ErrorCode ERR_AGGR_ORDER_BY_UNKNOWN_NAME =
            ErrorCode.define("metadata.aggr-order-by-unknown-name",
                    "orderBy references a measure/dimension name not in the user-selected measures/dimensions set: "
                            + "{metaTableId} name={name} selectedMeasures={selectedMeasures} selectedDimensions={selectedDimensions}",
                    "metaTableId", "name", "selectedMeasures", "selectedDimensions");
    /** 内存路径 having 求值器收到不支持的 op → 显式失败。 */
    static final ErrorCode ERR_AGGR_HAVING_UNSUPPORTED_OP =
            ErrorCode.define("metadata.aggr-having-unsupported-op",
                    "MemoryFilterEvaluator: having op not supported in first version: {op} name={name}",
                    "op", "name");

    // ===== 多列算术 having 专属 ErrorCode（plan 2026-07-18-1500-2：having 支持多 measure 算术表达式）=====

    /**
     * 多列算术 having 表达式不可解析（替换后的最终 SQL 片段经 {@link ExpressionMeasureValidator} 校验失败：
     * 括号不匹配 / 非法 token / 语句终止符 / 注释标记）。抛点：preprocess 阶段。
     */
    static final ErrorCode ERR_AGGR_HAVING_EXPR_UNPARSEABLE =
            ErrorCode.define("metadata.aggr-having-expr-unparseable",
                    "Multi-column arithmetic HAVING expression is unparseable (post-substitution SQL fragment fails "
                            + "ExpressionMeasureValidator parse): {metaTableId} expr={expr} error={error}",
                    "metaTableId", "expr", "error");
    /**
     * 多列算术 having 表达式不安全（含关键字/函数黑名单中的危险项，或列引用未通过标识符白名单，
     * 或包含字面量（Phase 1 安全裁定：禁止字面量，仅允许 measure name 算术组合））。
     * 抛点：preprocess 阶段。
     */
    static final ErrorCode ERR_AGGR_HAVING_EXPR_UNSAFE =
            ErrorCode.define("metadata.aggr-having-expr-unsafe",
                    "Multi-column arithmetic HAVING expression is unsafe (contains forbidden keyword/function, "
                            + "identifier fails whitelist, or contains literal (Phase 1 disallows literals, "
                            + "only measure-name arithmetic allowed)): "
                            + "{metaTableId} expr={expr} reason={reason}",
                    "metaTableId", "expr", "reason");
    /**
     * 多列算术 having 在跨库内存路径无法内存求值（对齐 D12.2 既有铁律——内存求值算术表达式等于在内存里实现
     * SQL 方言子集，D12.1 已明确拒绝「平台表达式引擎」）。抛点：跨库内存聚合入口 {@code MemoryFilterEvaluator}。
     */
    static final ErrorCode ERR_AGGR_HAVING_EXPR_MEMORY_NOT_COMPUTABLE =
            ErrorCode.define("metadata.aggr-having-expr-memory-not-computable",
                    "Multi-column arithmetic HAVING expression is not computable in cross-DB in-memory GROUP BY path "
                            + "(aligned with D12.2 in-memory aggFunc computability rule; "
                            + "arithmetic of multiple measures requires SQL pushdown): "
                            + "{metaTableId} expr={expr}",
                    "metaTableId", "expr");

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
     * @param having          可选 having（TreeBean，聚合后过滤，name 引用选定 measure/dimension；plan 2026-07-18-0900-2）
     * @param orderBy         可选 orderBy（List<OrderFieldBean>，按 measure/dimension 排序；plan 2026-07-18-0900-2）
     * @param ctx             共享依赖上下文
     * @return {@code Map{items:[{维度值, 指标聚合值}]}}
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
        // 默认过滤器自动应用（§4.4.2）
        IEntityDao<NopMetaTableFilter> filterDao = ctx.daoProvider().daoFor(NopMetaTableFilter.class);
        TreeBean mergedFilter = DefaultFilterApplicator.applyDefaults(table, userFilter, filterDao);

        // JOIN 聚合路径（plan 0852-1）：joinId 非空时走 entity↔entity JOIN 聚合，不复用单表分支
        if (joinId != null && !joinId.isEmpty()) {
            return buildResult(executeJoinAggregation(table, measureNames, dimensionNames,
                    mergedFilter, joinId, limit, offset, having, orderBy, ctx));
        }

        String tableType = table.getTableType();
        if (_NopMetadataCoreConstants.TABLE_TYPE_ENTITY.equals(tableType)) {
            return buildResult(executeEntityAggregation(table, measureNames, dimensionNames,
                    mergedFilter, limit, offset, having, orderBy, ctx));
        }
        if (_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL.equals(tableType)
                || _NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(tableType)) {
            return buildResult(executeExternalAggregation(table, measureNames, dimensionNames,
                    mergedFilter, limit, offset, having, orderBy, ctx));
        }
        throw new NopException(ERR_AGGR_EXEC_FAILED)
                .param("metaTableId", table.getMetaTableId())
                .param("error", "unsupported tableType: " + tableType);
    }

    // ============================ entity 聚合（D6：orm().executeQuery + D7.1：granularity 下沉 bypass EQL）============================

    private List<Map<String, Object>> executeEntityAggregation(NopMetaTable table, List<String> measureNames,
                                                                 List<String> dimensionNames, TreeBean filter,
                                                                 Long limit, Long offset, TreeBean having,
                                                                 List<OrderFieldBean> orderBy, MetaQueryContext ctx) {
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

        // name→SQL 表达式反查表（plan 2026-07-18-0900-2：having/orderBy name 解析）
        Map<String, String> nameToExpr = buildNameToExprTable(measures, dims, measureNames, dimensionNames, table);

        // §4.4.2 D7.1：任一 temporal dimension 有非空 granularity 时，分桶表达式须 DATE_TRUNC/DATE_FORMAT 等
        // EQL 不支持的函数 → 改走 bypass EQL 路径（TableReferenceExecutor 取平台 JDBC Connection 直查物理 SQL，
        // 与 external/sql 路径同 helper）。否则维持既有 orm().executeQuery EQL 路径（向后兼容，最小变更）。
        // plan 2026-07-18-1400-1：任一 measure 为 expression 型时同样需 bypass EQL（D12.2 entity 路径裁定——
        // expression 需 EQL 不支持的函数 / CASE WHEN 复杂结构，强制 bypass 走原生物理 SQL）。
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

    /**
     * 既有 entity 聚合执行路径（D6：{@code orm().executeQuery} 经 EQL 编译器，{@code allowUnderscoreName(true)}）。
     *
     * <p>用于无 temporal-with-granularity 维度的场景（向后兼容，行为零变化）。dimension 表达式恒为裸物理列。
     */
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

        // plan 2026-07-18-1400-1：参数绑定顺序（R1 Blocker 修正）—— expression 字面量先于 filter/having。
        // 注：via EQL 路径在 needsBypass=false 时进入，expression measure 因 needsBypass=true 强制走 bypass 路径，
        // 故此处 measures 内 expressionParams 为 null（防御性保留顺序逻辑）。
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
        // plan 2026-07-18-0900-2：HAVING 子句（having 的 name 经反查表解析为 aggSql/groupExpr，跳过白名单）
        // plan 2026-07-18-1500-2：先 preprocess 多列算术 leaf（expr 属性 → 替换 name→aggSql + 安全校验）
        if (having != null) {
            preprocessHavingArithmetic(having, nameToExpr, table, measureNames, dimensionNames);
            FilterToSqlTranslator.TranslatedFilter hf = ctx.filterTranslator().translate(having,
                    nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
            if (hf.getSql() != null && !hf.getSql().isEmpty()) {
                sql.append(" HAVING ").append(hf.getSql());
                params.addAll(hf.getParams());
            }
        }
        // plan 2026-07-18-0900-2：ORDER BY 子句（name 解析为 aggSql/groupExpr + ASC/DESC + NULLS FIRST/LAST）
        String orderByClause = buildOrderByClause(orderBy, nameToExpr, table, measureNames, dimensionNames, "ORDER_BY");
        if (!orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }
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

    /**
     * entity 聚合 bypass EQL 路径（§4.4.2 D7.1：temporal dimension granularity 分桶下沉到 SQL）。
     *
     * <p>经 {@code TableReferenceExecutor.execute}（§4.4.3 D1）的 entity 分派路径取平台 JDBC Connection
     * （{@code orm.getSessionFactory().txn()} + {@code runInTransaction(SUPPORTS)} +
     * {@code IJdbcTransaction.getConnection()}），直查物理 SQL——**不经 EQL 编译器**，从而可以使用
     * {@code DATE_TRUNC}/{@code DATE_FORMAT} 等 EQL 函数白名单不允许的方言原生分桶函数。SQL 表达式与
     * external/sql 路径完全一致（复用 {@link GranularityBucketing#translate}），dialect 分发 H2/MySQL/PostgreSQL。
     *
     * <p>失败路径显式化：granularity 不约定 / 方言不支持 → 既有 inline ErrorCode（沿用 external/sql 路径同名检查）。
     */
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
                throw new NopException(ERR_AGGR_UNSUPPORTED_DIALECT)
                        .param("databaseProductName", String.valueOf(productName))
                        .param("metaTableId", table.getMetaTableId());
            }
            // plan 2026-07-18-1400-1：expression 型 measure 的 dialect-specific 函数支持检查（dialect 已知）
            for (MeasureSpec m : measures) {
                if (m.isExpression()) {
                    ExpressionMeasureValidator.checkDialectSupported(m.validatedExpression, productName,
                            table.getMetaTableId(), m.alias);
                }
            }
            // D7：与 external/sql 路径 buildExternalAggregationSql 等价的物理 SQL 构造（同 granularity 分桶表达式）
            StringBuilder sql = new StringBuilder("SELECT ");
            List<String> groupExprs = new ArrayList<>();
            for (int i = 0; i < dims.size(); i++) {
                DimensionSpec d = dims.get(i);
                String expr;
                if (_NopMetadataCoreConstants.DIMENSION_TYPE_TEMPORAL.equals(d.dimensionType) && d.granularity != null) {
                    // D7：时间维度按 granularity 分桶（复用 GranularityBucketing.translate，dialect 分发）
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

            // plan 2026-07-18-1400-1：参数绑定顺序（R1 Blocker 修正）：
            // SELECT 内 expression 字面量 → WHERE filter 值 → HAVING 值 → limit/offset（由 executeJdbcQuery 追加）
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
            // plan 2026-07-18-0900-2：HAVING 子句
            // plan 2026-07-18-1500-2：先 preprocess 多列算术 leaf
            if (having != null) {
                preprocessHavingArithmetic(having, nameToExpr, table, measureNames, dimensionNames);
                FilterToSqlTranslator.TranslatedFilter hf = ctx.filterTranslator().translate(having,
                        nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
                if (hf.getSql() != null && !hf.getSql().isEmpty()) {
                    sql.append(" HAVING ").append(hf.getSql());
                    params.addAll(hf.getParams());
                }
            }
            // plan 2026-07-18-0900-2：ORDER BY 子句
            String orderByClause = buildOrderByClause(orderBy, nameToExpr, table, measureNames, dimensionNames, "ORDER_BY");
            if (!orderByClause.isEmpty()) {
                sql.append(" ORDER BY ").append(orderByClause);
            }
            // limit/offset 占位由 executeJdbcQuery 经 PreparedStatement 绑定（与 external/sql 路径一致）
            if (limit != null) {
                sql.append(" LIMIT ?");
            }
            if (offset != null && offset > 0) {
                sql.append(" OFFSET ?");
            }
            String sqlText = sql.toString();
            LOG.info("queryAggregation entity bypass-EQL SQL: {}", sqlText);
            return executeJdbcQuery(conn, sqlText, params, limit, offset, table.getMetaTableId());
        });
    }

    // ============================ JOIN 聚合路由（plan 0852-1 entity↔entity + plan 1200-1 external↔external 同库）============================

    /**
     * JOIN 聚合执行入口（端点组合路由，plan 1200-1 重构）：加载/校验 join + 解析端点后，按**端点组合**分派：
     * <ul>
     *   <li>entity↔entity（plan 0852-1）→ {@link #executeEntityEntityJoinAggregation}（{@code orm().executeQuery}
     *       原生 {@code GROUP BY ... OVER JOIN}，物理表 + 物理列，side 可选/一致性校验）</li>
     *   <li>external↔external 同 querySpace（plan 1200-1）→ {@link #executeExternalExternalJoinAggregation}
     *       （两端点共享 {@code withConnection} 原生 {@code GROUP BY ... OVER JOIN}，side 必填 + 列名存在性校验）</li>
     *   <li>external↔external 跨 querySpace（跨库）→ 显式失败（{@link #ERR_AGGR_JOIN_EXTERNAL_CROSS_QUERY_SPACE}，deferred）</li>
     *   <li>混合端点（entity ↔ external/sql）→ 显式失败（{@link #ERR_AGGR_JOIN_MIXED_ENDPOINT_DEFERRED}，deferred）</li>
     * </ul>
     *
     * <p>失败路径显式（Minimum Rules #24，无静默跳过/无静默降级单表/无空 items）：join 不存在/不归属/joinType=right/
     * 未知 joinType（由 {@link MetaJoinExecutor#loadValidatedJoin} 抛）；self-join（双侧别名机制不足 →
     * {@link #ERR_AGGR_JOIN_SELF_JOIN}）；side 缺失/不一致/列不存在（→ 对应 ErrorCode）；expression 型 Measure
     * 在 JOIN 路径已支持（plan 2026-07-18-1400-1，注入 {@code <agg>(<validatedExpr>)}）；
     * 跨库内存路径下 expression 显式失败（→ {@link #ERR_AGGR_EXPRESSION_MEMORY_NOT_COMPUTABLE}）；
     * expression 被 HAVING/ORDER BY 引用显式失败（→ {@link #ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED}）；
     * EQL 编译失败（保留字物理列名 → {@link #ERR_AGGR_JOIN_COMPILE_FAILED}）。
     */
    private List<Map<String, Object>> executeJoinAggregation(NopMetaTable table, List<String> measureNames,
                                                                List<String> dimensionNames, TreeBean filter,
                                                                String joinId, Long limit, Long offset,
                                                                TreeBean having, List<OrderFieldBean> orderBy,
                                                                MetaQueryContext ctx) {
        // 1. 复用 MetaJoinExecutor 的 join 加载/归属/joinType 校验（显式选定「抽取共享」，见 plan Decision）
        NopMetaTableJoin join = joinExecutor.loadValidatedJoin(table, joinId, ctx);

        // 2. 解析左右端点（复用 MetaJoinExecutor.resolveEndpoint，端点解析逻辑唯一来源）
        MetaJoinExecutor.Endpoint leftEp = joinExecutor.resolveEndpoint(join, "left",
                join.getLeftEntityId(), join.getLeftTableId(), ctx);
        MetaJoinExecutor.Endpoint rightEp = joinExecutor.resolveEndpoint(join, "right",
                join.getRightEntityId(), join.getRightTableId(), ctx);

        // 3. 端点组合路由（plan 1200-1 Phase 3：重构既有「任一端点非 entity 短路」守卫为端点组合路由）
        if (leftEp.isEntity() && rightEp.isEntity()) {
            // entity↔entity：跨 querySpace → D10 内存 GROUP BY（plan 1500-2）；同 querySpace → 0852-1 既有路径
            String leftQs = leftEp.entity.getQuerySpace();
            String rightQs = rightEp.entity.getQuerySpace();
            if (!equalsStr(leftQs, rightQs)) {
                return executeCrossDbJoinAggregation(table, measureNames, dimensionNames, filter, join, joinId,
                        leftEp, rightEp, limit, offset, having, orderBy, ctx);
            }
            return executeEntityEntityJoinAggregation(table, measureNames, dimensionNames, filter, join, joinId,
                    leftEp, rightEp, limit, offset, having, orderBy, ctx);
        }
        if (!leftEp.isEntity() && !rightEp.isEntity()) {
            // external↔external：跨 querySpace → D10 内存 GROUP BY（plan 1500-2）；同 querySpace → plan 1200-1
            String leftQs = leftEp.table.getQuerySpace();
            String rightQs = rightEp.table.getQuerySpace();
            if (!equalsStr(leftQs, rightQs)) {
                return executeCrossDbJoinAggregation(table, measureNames, dimensionNames, filter, join, joinId,
                        leftEp, rightEp, limit, offset, having, orderBy, ctx);
            }
            return executeExternalExternalJoinAggregation(table, measureNames, dimensionNames, filter, join, joinId,
                    leftEp, rightEp, limit, offset, having, orderBy, ctx);
        }
        // 混合端点（entity ↔ external/sql）→ plan 1500-1 D1.5：同库（连接可达性实测通过）→ 原生 GROUP BY over JOIN；
        // 不可同库 → D10 内存 GROUP BY（plan 1500-2，复用 executeJoin + 内存聚合，精确-当-容纳/超限-失败）
        return executeMixedSameDbJoinAggregation(table, measureNames, dimensionNames, filter, join, joinId,
                leftEp, rightEp, limit, offset, having, orderBy, ctx);
    }

    /**
     * entity↔entity JOIN 聚合（plan 0852-1 既有路径，plan 1200-1 增加 side 一致性校验）。
     *
     * <p>side 语义（§4.4.2 D9）：entity 端点 side **可选**（entityFieldId→metaEntityId 已可无歧义判定归属）；
     * 若 Measure/Dimension 提供 side，须与 metaEntityId 判定的端点一致，不一致显式失败。
     */
    private List<Map<String, Object>> executeEntityEntityJoinAggregation(NopMetaTable table, List<String> measureNames,
                                                                           List<String> dimensionNames, TreeBean filter,
                                                                           NopMetaTableJoin join, String joinId,
                                                                           MetaJoinExecutor.Endpoint leftEp,
                                                                           MetaJoinExecutor.Endpoint rightEp,
                                                                           Long limit, Long offset,
                                                                           TreeBean having, List<OrderFieldBean> orderBy,
                                                                           MetaQueryContext ctx) {
        NopMetaEntity leftEntity = leftEp.entity;
        NopMetaEntity rightEntity = rightEp.entity;

        // 实体注册校验（复用 MetaJoinExecutor.requireRegistered）
        joinExecutor.requireRegistered(leftEntity, "left", joinId, ctx);
        joinExecutor.requireRegistered(rightEntity, "right", joinId, ctx);

        // self-join 守卫：leftEntityId == rightEntityId → 字段归属两侧均命中、无法表达右别名，显式失败
        if (equalsStr(leftEntity.getMetaEntityId(), rightEntity.getMetaEntityId())) {
            throw new NopException(ERR_AGGR_JOIN_SELF_JOIN)
                    .param("joinId", joinId).param("entityId", leftEntity.getMetaEntityId());
        }

        // 跨 querySpace 已在 executeJoinAggregation 路由判定（跨库 → D10 内存 GROUP BY），此处仅同库路径。

        // 物理表 + 属性名→物理列映射（左右 entity 各一份）
        String leftPhysical = requireName(leftEntity.getTableName(), "leftTableName");
        String rightPhysical = requireName(rightEntity.getTableName(), "rightTableName");
        FilterToSqlTranslator.validateIdentifier(leftPhysical);
        FilterToSqlTranslator.validateIdentifier(rightPhysical);
        Map<String, String> leftPropToCol = resolveEntityColumns(leftEntity, ctx);
        Map<String, String> rightPropToCol = resolveEntityColumns(rightEntity, ctx);

        // join 字段（属性名）→ 物理列（复用 MetaJoinExecutor.resolveFieldToColumn）
        String leftJoinCol = joinExecutor.resolveFieldToColumn(leftPropToCol, join.getLeftField(),
                leftEntity, "left", joinId);
        String rightJoinCol = joinExecutor.resolveFieldToColumn(rightPropToCol, join.getRightField(),
                rightEntity, "right", joinId);
        FilterToSqlTranslator.validateIdentifier(leftJoinCol);
        FilterToSqlTranslator.validateIdentifier(rightJoinCol);

        // 加载 Measure/Dimension 并按 metaEntityId 判定左/右归属 + side 一致性校验 + 解析物理列（含别名前缀 l./r.）
        String leftEntityId = leftEntity.getMetaEntityId();
        String rightEntityId = rightEntity.getMetaEntityId();
        JoinFieldResolver resolver = new JoinFieldResolver(leftEntityId, rightEntityId, joinId, table, ctx);
        List<JoinMeasureSpec> measures = loadJoinMeasures(table, measureNames, ctx, resolver);
        List<JoinDimensionSpec> dims = loadJoinDimensions(table, dimensionNames, ctx, resolver);

        // name→SQL 表达式反查表（plan 2026-07-18-0900-2：having/orderBy name 解析，JOIN 路径用 qualifiedAggCol/qualifiedCol）
        Map<String, String> nameToExpr = buildJoinNameToExprTable(measures, dims, measureNames, dimensionNames, table);

        // 构造 JOIN 聚合 SQL：SELECT <group l./r. cols>, <agg(l./r. col)> FROM <l> JOIN <r> ON ... [WHERE] GROUP BY ...
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
        // plan 2026-07-18-1400-1：参数绑定顺序（R1 Blocker 修正）—— expression 字面量先于 filter/having。
        for (JoinMeasureSpec m : measures) {
            if (m.expressionParams != null) {
                params.addAll(m.expressionParams);
            }
        }
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
        // plan 2026-07-18-0900-2：HAVING 子句
        // plan 2026-07-18-1500-2：先 preprocess 多列算术 leaf
        if (having != null) {
            preprocessHavingArithmetic(having, nameToExpr, table, measureNames, dimensionNames);
            FilterToSqlTranslator.TranslatedFilter hf = ctx.filterTranslator().translate(having,
                    nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
            if (hf.getSql() != null && !hf.getSql().isEmpty()) {
                sql.append(" HAVING ").append(hf.getSql());
                params.addAll(hf.getParams());
            }
        }
        // plan 2026-07-18-0900-2：ORDER BY 子句
        String orderByClause = buildOrderByClause(orderBy, nameToExpr, table, measureNames, dimensionNames, "ORDER_BY");
        if (!orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }
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

    /**
     * external↔external 同库 JOIN 聚合（plan 1200-1 Phase 3 新增，§4.4.2 D9）。
     *
     * <p>两端点均为 external/sql 且同 querySpace → 经两端点共享的 {@code withConnection}（querySpace→NopMetaDataSource）
     * 跑原生 {@code SELECT <group cols>, <agg> FROM <leftFrom> l INNER|LEFT JOIN <rightFrom> r
     * ON l.<lf>=r.<rf> [WHERE] GROUP BY ...}；Measure/Dimension 按 **side** 经 {@code l.col}/{@code r.col} 限定。
     *
     * <p>FROM 按 tableType 构造（external→{@code FROM <tableName>}；sql→{@code FROM (<sourceSql>) _t}，沿用 D6 buildFromClause）。
     * 标识符白名单 + 值参数绑定（§2.7.1 D3）。
     *
     * <p>side 语义（§4.4.2 D9）：external/sql 端点 side **必填**（null → 显式失败）；列名须属于所绑定端点的解析字段集合。
     *
     * <p>失败路径显式化（无静默跳过/无静默降级单表/无空 items）：external/sql 端点缺 side / side 指向端点无此列 /
     * self-join（双侧别名机制不足）/ 跨库 / right / 保留字列均抛 ErrorCode。
     */
    private List<Map<String, Object>> executeExternalExternalJoinAggregation(NopMetaTable table,
                                                                               List<String> measureNames,
                                                                               List<String> dimensionNames,
                                                                               TreeBean filter, NopMetaTableJoin join,
                                                                               String joinId,
                                                                               MetaJoinExecutor.Endpoint leftEp,
                                                                               MetaJoinExecutor.Endpoint rightEp,
                                                                               Long limit, Long offset,
                                                                               TreeBean having,
                                                                               List<OrderFieldBean> orderBy,
                                                                               MetaQueryContext ctx) {
        NopMetaTable leftTable = leftEp.table;
        NopMetaTable rightTable = rightEp.table;

        // self-join 守卫：左右同表（双侧别名机制不足，单 alias 无法区分）→ 显式失败
        if (equalsStr(leftTable.getMetaTableId(), rightTable.getMetaTableId())) {
            throw new NopException(ERR_AGGR_JOIN_SELF_JOIN)
                    .param("joinId", joinId).param("entityId", leftTable.getMetaTableId());
        }

        // 共享数据源（两端点同 querySpace，由路由保证）
        NopMetaDataSource dataSource = resolveSharedDataSourceOrThrow(leftTable, ctx, joinId);

        // 解析两端点列集合 + join 字段（table 端点字段即物理列名，校验属于该表解析列集合）
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        Set<String> leftCols = resolveTableColumnNames(leftTable, fieldDao, ctx);
        Set<String> rightCols = resolveTableColumnNames(rightTable, fieldDao, ctx);
        String leftJoinCol = resolveExternalFieldOrThrow(leftCols, join.getLeftField(), leftTable, "left", joinId);
        String rightJoinCol = resolveExternalFieldOrThrow(rightCols, join.getRightField(), rightTable, "right", joinId);
        FilterToSqlTranslator.validateIdentifier(leftJoinCol);
        FilterToSqlTranslator.validateIdentifier(rightJoinCol);

        // side 解析 + 校验（§4.4.2 D9）：external/sql 端点 side 必填、列名存在于所绑定端点字段集合
        JoinExternalSideResolver sideResolver = new JoinExternalSideResolver(
                leftTable, rightTable, leftCols, rightCols, joinId, table);
        List<JoinMeasureSpec> measures = loadExternalJoinMeasures(table, measureNames, ctx, sideResolver);
        List<JoinDimensionSpec> dims = loadExternalJoinDimensions(table, dimensionNames, ctx, sideResolver);

        // name→SQL 表达式反查表（plan 2026-07-18-0900-2：having/orderBy name 解析）
        Map<String, String> nameToExpr = buildJoinNameToExprTable(measures, dims, measureNames, dimensionNames, table);

        // FROM 子句按 tableType 构造（external→<tableName> l；sql→(<sourceSql>) l）
        String leftFrom = externalTableFromForJoin(leftTable, "l");
        String rightFrom = externalTableFromForJoin(rightTable, "r");

        // filter/having 预翻译（与方言无关，提前计算参数顺序与主 SQL 一致）
        // plan 2026-07-18-1500-2：先 preprocess 多列算术 leaf（在 translate 前）
        if (having != null) {
            preprocessHavingArithmetic(having, nameToExpr, table, measureNames, dimensionNames);
        }
        FilterToSqlTranslator.TranslatedFilter filterTf = filter == null ? null : ctx.filterTranslator().translate(filter);
        FilterToSqlTranslator.TranslatedFilter havingTf = having == null ? null
                : ctx.filterTranslator().translate(having,
                        nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
        // ORDER BY 子句预构造（name 解析为 aggSql/groupExpr + ASC/DESC + NULLS FIRST/LAST）
        String orderByClause = buildOrderByClause(orderBy, nameToExpr, table, measureNames, dimensionNames, "ORDER_BY");

        final List<Map<String, Object>>[] holder = newArrayHolder();
        ctx.connectionService().withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    String dialect = safeProductName(metaData);
                    if (dialect == null || !SUPPORTED_DIALECTS.contains(dialect)) {
                        throw new NopException(ERR_AGGR_UNSUPPORTED_DIALECT)
                                .param("databaseProductName", String.valueOf(dialect))
                                .param("metaTableId", table.getMetaTableId());
                    }
                    // plan 2026-07-18-1400-1：expression 型 measure 的 dialect-specific 函数支持检查（dialect 已知）
                    for (JoinMeasureSpec m : measures) {
                        if (m.isExpression()) {
                            ExpressionMeasureValidator.checkDialectSupported(m.validatedExpression, dialect,
                                    table.getMetaTableId(), m.alias);
                        }
                    }
                    StringBuilder sql = buildExternalExternalJoinSql(measures, dims, leftFrom, rightFrom,
                            leftJoinCol, rightJoinCol, join, filterTf, havingTf, orderByClause, dialect, limit, offset);
                    // plan 2026-07-18-1400-1：参数绑定顺序（R1 Blocker 修正）—— expression 字面量 → filter → having → limit/offset
                    List<Object> params = new ArrayList<>();
                    for (JoinMeasureSpec m : measures) {
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

    // ============================ 混合端点同库 JOIN 聚合（plan 1500-1 D1.5）============================

    /**
     * 混合端点（entity ↔ external/sql）同库 JOIN 聚合（plan 1500-1 D1.5）。
     *
     * <p>端点组合：一端 entity（{@code leftEntityId}/{@code rightEntityId}），另一端 external/sql table
     * （{@code leftTableId}/{@code rightTableId}）。**连接载体选定候选 A**：复用 external/sql 端点的
     * {@code NopMetaDataSource withConnection}，在该单一连接上同时访问 external 物理表 + entity 物理表
     * （直查 {@code NopMetaEntity.tableName}，**绕过 ORM session/EQL**）。
     *
     * <p>同库判定（querySpace 字符串不可靠 + 连接可达性实测）：在选定 external {@code withConnection} callback 内
     * 实测 entity 物理表是否在 {@code DatabaseMetaData.getTables(null, entitySchema, entityTableName, null)} 结果集中。
     * 可见 → 同库，跑原生 {@code GROUP BY over JOIN}；不可见 → {@link #ERR_AGGR_JOIN_MIXED_CROSS_DB_DEFERRED}（不静默降级 D5）。
     *
     * <p>schema 限定：两侧表名显式 {@code <schema>.<table>} 限定（entity 用 {@code NopMetaEntity.dbSchema}，
     * external 用 {@code NopMetaTable.schema}，可空则不限定）。
     *
     * <p>measure/dimension 解析（F1-2：仅 table 侧套用 side resolver）：entity 侧经 {@code entityFieldId→metaEntityId→columnCode}
     * （不套用 side resolver，side 可选但若提供须一致）；external/sql 侧经 side（必填）+ 列名存在性校验。{@link JoinMixedSideResolver}
     * 按 entityFieldId 是否为 NopMetaEntityField PK 且归属 entity 端点判定侧别。
     *
     * <p>{@code requireRegistered} 不适用（F1-3）：候选 A 绕过 ORM session，entity 侧仅校验物理表名可达性（tableName 非空 + 连接可达性实测）。
     *
     * <p>失败路径显式（#24）：不可同库 / 物理表名空 / 缺 datasource / right（由 loadValidatedJoin 抛）/ self-join 守卫 /
     * side 缺失 / 列不存在 / SQL 执行失败 均抛 ErrorCode，不静默降级、不静默空 items、不伪造值。
     */
    private List<Map<String, Object>> executeMixedSameDbJoinAggregation(NopMetaTable table, List<String> measureNames,
                                                                         List<String> dimensionNames, TreeBean filter,
                                                                         NopMetaTableJoin join, String joinId,
                                                                         MetaJoinExecutor.Endpoint leftEp,
                                                                         MetaJoinExecutor.Endpoint rightEp,
                                                                         Long limit, Long offset,
                                                                         TreeBean having, List<OrderFieldBean> orderBy,
                                                                         MetaQueryContext ctx) {
        // 1. 识别 entity/table 端点位置（entity 可在 left 也可在 right）
        boolean entityOnLeft = leftEp.isEntity();
        NopMetaEntity entityEndpoint = entityOnLeft ? leftEp.entity : rightEp.entity;
        NopMetaTable tableEndpoint = entityOnLeft ? rightEp.table : leftEp.table;

        // 2. self-join 防御性守卫：entity 物理表名 == external 物理表名 + 同一数据源上下文 → 别名机制不足，显式失败
        //    （混合端点本质 entity↔table 类型不同，self-join 概率低；防御性守住语义清晰）
        String entityPhysicalTable = entityEndpoint.getTableName();
        if (entityPhysicalTable == null || entityPhysicalTable.trim().isEmpty()) {
            throw new NopException(ERR_AGGR_JOIN_MIXED_ENTITY_TABLE_EMPTY)
                    .param("joinId", joinId).param("entityId", String.valueOf(entityEndpoint.getMetaEntityId()));
        }
        FilterToSqlTranslator.validateIdentifier(entityPhysicalTable);
        String entitySchema = entityEndpoint.getDbSchema();
        if (entitySchema != null && !entitySchema.trim().isEmpty()) {
            FilterToSqlTranslator.validateIdentifier(entitySchema);
        } else {
            entitySchema = null;
        }

        // 3. 解析 external/sql 端点共享数据源（querySpace→NopMetaDataSource，external 端点必须注册 ACTIVE 数据源）
        NopMetaDataSource dataSource = resolveSharedDataSourceOrThrow(tableEndpoint, ctx, joinId);

        // 3b. 同库判定（连接可达性实测）：提前实测 entity 物理表是否在选定 external 连接可见。
        //     不可见 → 不可同库 → D10 内存 GROUP BY（plan 1500-2，复用 executeJoin + 内存聚合，精确-当-容纳/超限-失败），
        //     不静默降级 D5 拼接近似聚合。
        if (!checkEntityTableVisible(dataSource, entitySchema, entityPhysicalTable, ctx)) {
            return executeCrossDbJoinAggregation(table, measureNames, dimensionNames, filter, join, joinId,
                    leftEp, rightEp, limit, offset, having, orderBy, ctx);
        }

        // 4. 解析 entity 端点的 propToCol 映射（join.leftField/rightField + entity 侧 measure/dimension 列解析）
        Map<String, String> entityPropToCol = resolveEntityColumns(entityEndpoint, ctx);

        // 5. 解析 external/sql 端点列集合 + join 字段
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        Set<String> tableCols = resolveTableColumnNames(tableEndpoint, fieldDao, ctx);
        // join 字段：entity 侧（属性名→columnCode），table 侧（直接列名，校验属于该表列集合）
        String entityJoinFieldProp = entityOnLeft ? join.getLeftField() : join.getRightField();
        String tableJoinFieldCol = entityOnLeft ? join.getRightField() : join.getLeftField();
        String entityJoinColumn = joinExecutor.resolveFieldToColumn(entityPropToCol, entityJoinFieldProp,
                entityEndpoint, entityOnLeft ? "left" : "right", joinId);
        String tableJoinColumn = resolveExternalFieldOrThrow(tableCols, tableJoinFieldCol, tableEndpoint,
                entityOnLeft ? "right" : "left", joinId);
        FilterToSqlTranslator.validateIdentifier(entityJoinColumn);
        FilterToSqlTranslator.validateIdentifier(tableJoinColumn);

        // 6. side 解析（F1-2：仅 table 侧套用 side resolver，entity 侧走 entityFieldId→columnCode）
        JoinMixedSideResolver sideResolver = new JoinMixedSideResolver(
                entityEndpoint, entityPropToCol, tableEndpoint, tableCols, entityOnLeft, joinId, table, ctx);
        List<JoinMeasureSpec> measures = loadExternalJoinMeasures(table, measureNames, ctx, sideResolver);
        List<JoinDimensionSpec> dims = loadExternalJoinDimensions(table, dimensionNames, ctx, sideResolver);

        // name→SQL 表达式反查表（plan 2026-07-18-0900-2：having/orderBy name 解析）
        Map<String, String> nameToExpr = buildJoinNameToExprTable(measures, dims, measureNames, dimensionNames, table);

        // 7. FROM 子句构造（含 schema 限定 + 别名 l/r）
        String entityAlias = entityOnLeft ? "l" : "r";
        String tableAlias = entityOnLeft ? "r" : "l";
        String entityFrom = buildEntityFromClause(entityPhysicalTable, entitySchema, entityAlias);
        String tableFrom = externalTableFromForJoin(tableEndpoint, tableAlias);

        // filter/having 预翻译（与方言无关，提前计算参数顺序）
        // plan 2026-07-18-1500-2：先 preprocess 多列算术 leaf（在 translate 前）
        if (having != null) {
            preprocessHavingArithmetic(having, nameToExpr, table, measureNames, dimensionNames);
        }
        FilterToSqlTranslator.TranslatedFilter filterTf =
                filter == null ? null : ctx.filterTranslator().translate(filter);
        FilterToSqlTranslator.TranslatedFilter havingTf = having == null ? null
                : ctx.filterTranslator().translate(having,
                        nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
        String orderByClause = buildOrderByClause(orderBy, nameToExpr, table, measureNames, dimensionNames, "ORDER_BY");

        // 8. 在 external withConnection callback 内执行原生 GROUP BY over JOIN（同库判定已在入口提前完成）
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
                        throw new NopException(ERR_AGGR_UNSUPPORTED_DIALECT)
                                .param("databaseProductName", String.valueOf(dialect))
                                .param("metaTableId", table.getMetaTableId());
                    }
                    // plan 2026-07-18-1400-1：expression 型 measure 的 dialect-specific 函数支持检查（dialect 已知）
                    for (JoinMeasureSpec m : _measures) {
                        if (m.isExpression()) {
                            ExpressionMeasureValidator.checkDialectSupported(m.validatedExpression, dialect,
                                    table.getMetaTableId(), m.alias);
                        }
                    }
                    // 同库判定已在入口提前完成（checkEntityTableVisible），此处仅同库路径。
                    StringBuilder sql = buildMixedSameDbJoinSql(_measures, _dims, _entityFrom, _tableFrom,
                            _entityAlias, _tableAlias, _entityJoinColumn, _tableJoinColumn, join, _filterTf,
                            _havingTf, _orderByClause, dialect, limit, offset);
                    // plan 2026-07-18-1400-1：参数绑定顺序（R1 Blocker 修正）—— expression 字面量 → filter → having → limit/offset
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

    /**
     * 在选定 external 连接上提前实测 entity 物理表可见性（plan 1500-2：从 withConnection callback 内提前到入口）。
     * 可见 → 同库（继续 D1.5 原生 GROUP BY over JOIN）；不可见 → 跨库（调用方路由到 D10 内存 GROUP BY）。
     */
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

    /**
     * 实测 entity 物理表是否在该连接可见（plan 1500-1 D1.5 同库判定）。
     *
     * <p>使用 {@code DatabaseMetaData.getTables(null, schema, tableName, null)}。schema 为 null 时不过滤 schema
     * （查全库），匹配任何 schema 下同名表即可（兜底）；schema 非空时按 schema 精确匹配（大小写不敏感，
     * 适应 H2 大写 vs 配置小写的合法规范化）。
     *
     * <p>tableNamePattern 的大小写敏感性因方言而异（H2 默认大小写敏感、MySQL 通常大小写不敏感、PostgreSQL 折叠为小写）。
     * 为跨方言鲁棒，依次尝试原值、大写、小写三种形式，任一命中即视为可见。
     *
     * <p>SQLException 内部 catch 转 false（不可达即视为不可见，触发显式 cross-DB 失败，由调用方抛 ErrorCode）。
     */
    private static boolean isEntityTableVisible(DatabaseMetaData metaData, String schema, String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return false;
        }
        // 依次尝试原值 / 大写 / 小写，适应跨方言 tableNamePattern 大小写敏感性差异
        return checkTableExists(metaData, schema, tableName)
                || checkTableExists(metaData, schema, tableName.toUpperCase(Locale.ROOT))
                || checkTableExists(metaData, schema, tableName.toLowerCase(Locale.ROOT));
    }

    private static boolean checkTableExists(DatabaseMetaData metaData, String schema, String tableName) {
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

    /** 构造 entity 端点 FROM 子句（含 schema 限定 + 别名）：{@code <[schema.]tableName> <alias>}。 */
    private String buildEntityFromClause(String physicalTable, String schema, String alias) {
        FilterToSqlTranslator.validateIdentifier(alias);
        if (schema != null && !schema.trim().isEmpty()) {
            FilterToSqlTranslator.validateIdentifier(schema);
            return schema + "." + physicalTable + " " + alias;
        }
        return physicalTable + " " + alias;
    }

    /**
     * 构造混合端点同库 JOIN 聚合 SQL：
     * {@code SELECT <group l./r. cols>, <agg(l./r. col)> FROM <entityFrom> <eAlias> INNER|LEFT JOIN <tableFrom> <tAlias>
     * ON <eAlias>.<entityJoinColumn> = <tAlias>.<tableJoinColumn> [WHERE] GROUP BY ...}。
     *
     * <p>注意：entity 端点别名（eAlias）和 table 端点别名（tAlias）由调用方按 entityOnLeft 决定（l/r），
     * measure/dimension 的 qualifiedCol 已在 {@link JoinMixedSideResolver} 中按端点位置构造。
     */
    private StringBuilder buildMixedSameDbJoinSql(List<JoinMeasureSpec> measures, List<JoinDimensionSpec> dims,
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
            JoinDimensionSpec d = dims.get(i);
            // external/sql 端点时间分桶（D7），entity 端点无 granularity 下沉（受 EQL 限制；此处 withConnection 原生 SQL，
            // 但 entity 端点物理列直查仍按物理列——granularity 仅对 external/sql 端点列生效，沿用 D7 范围）
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
        for (JoinMeasureSpec m : measures) {
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
        if (limit != null) {
            sql.append(" LIMIT ?");
        }
        if (offset != null && offset > 0) {
            sql.append(" OFFSET ?");
        }
        return sql;
    }


    /** 构造 external↔external JOIN 聚合 SQL：SELECT <group l./r. cols>, <agg(l./r. col)> FROM ... JOIN ... [WHERE] GROUP BY ... [HAVING] [ORDER BY] ...。 */
    private StringBuilder buildExternalExternalJoinSql(List<JoinMeasureSpec> measures, List<JoinDimensionSpec> dims,
                                                        String leftFrom, String rightFrom, String leftJoinCol,
                                                        String rightJoinCol, NopMetaTableJoin join,
                                                        FilterToSqlTranslator.TranslatedFilter filterTf,
                                                        FilterToSqlTranslator.TranslatedFilter havingTf,
                                                        String orderByClause,
                                                        String dialect, Long limit, Long offset) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> groupExprs = new ArrayList<>();
        for (int i = 0; i < dims.size(); i++) {
            JoinDimensionSpec d = dims.get(i);
            // external/sql JOIN 路径时间分桶（D7，与单表 external 路径一致，withConnection 原生 SQL）
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
        for (JoinMeasureSpec m : measures) {
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
        if (limit != null) {
            sql.append(" LIMIT ?");
        }
        if (offset != null && offset > 0) {
            sql.append(" OFFSET ?");
        }
        return sql;
    }

    // ============================ 跨库 JOIN 聚合内存 GROUP BY（plan 1500-2 D10）============================

    /**
     * 跨库 JOIN 聚合内存 GROUP BY（§4.4.2 D10）：复用 {@link MetaJoinExecutor#executeJoin}（公开入口，`:139`）
     * 取得已合并的 JOIN 行 → 内存 GROUP BY（精确-当-容纳 / 超限-失败）。
     *
     * <p>{@code executeJoin} 内部已完成：跨库取数（{@code fetchEntityRows}/{@code fetchTableRows}）+
     * {@code MAX_CROSS_DB_ROWS} 规模守卫（{@code checkSizeLimit}，超限**直接抛异常**不截断）+
     * 命名空间规范化（D1.4）+ joinType 语义（D5）。本方法复用其产出，**不直接调用 private 取数/合并方法**。
     *
     * <p>合并行 measure/dimension 值提取（Anti-Hollow 核心）：按端点来源用对应命名空间从合并行 Map 取值——
     * entity 侧经 {@code NopMetaEntityField.fieldName}（属性名，**非 columnCode**）；table 侧经物理列名；
     * 右侧冲突字段经 {@code <alias>_<name>} 取值。key 缺失 → 显式失败（**绝不静默返回 null/0**）。
     *
     * <p>失败路径显式化（#24）：超限（{@code executeJoin} 内抛）、join key 错配（{@code executeJoin} 内校验）、
     * measure/dimension key 缺失（本方法新增显式失败）、side 缺失、joinType=right、self-join、空端点 均抛 ErrorCode。
     */
    private List<Map<String, Object>> executeCrossDbJoinAggregation(NopMetaTable table, List<String> measureNames,
                                                                     List<String> dimensionNames, TreeBean filter,
                                                                     NopMetaTableJoin join, String joinId,
                                                                     MetaJoinExecutor.Endpoint leftEp,
                                                                     MetaJoinExecutor.Endpoint rightEp,
                                                                     Long limit, Long offset,
                                                                     TreeBean having, List<OrderFieldBean> orderBy,
                                                                     MetaQueryContext ctx) {
        // 1. Self-join guards（双侧别名机制不足，字段归属歧义 → 显式失败，沿用 D8/D9）
        if (leftEp.isEntity() && rightEp.isEntity()) {
            if (equalsStr(leftEp.entity.getMetaEntityId(), rightEp.entity.getMetaEntityId())) {
                throw new NopException(ERR_AGGR_JOIN_SELF_JOIN)
                        .param("joinId", joinId).param("entityId", leftEp.entity.getMetaEntityId());
            }
        } else if (!leftEp.isEntity() && !rightEp.isEntity()) {
            if (equalsStr(leftEp.table.getMetaTableId(), rightEp.table.getMetaTableId())) {
                throw new NopException(ERR_AGGR_JOIN_SELF_JOIN)
                        .param("joinId", joinId).param("entityId", leftEp.table.getMetaTableId());
            }
        }

        // 2. 跨库字段解析器（按端点组合解析 measure/dimension 的 side + rawKey per D10 命名空间规则）
        CrossDbFieldResolver resolver = new CrossDbFieldResolver(leftEp, rightEp, join, joinId, table, ctx);
        List<CrossDbMeasureSpec> measures = loadCrossDbMeasures(table, measureNames, ctx, resolver);
        List<CrossDbDimensionSpec> dims = loadCrossDbDimensions(table, dimensionNames, ctx, resolver);

        // 3. 复用 MetaJoinExecutor.executeJoin（公开入口）取得合并后的 JOIN 行。
        //    内部 checkSizeLimit 超限显式失败（不截断）；命名空间规范化 D1.4 + joinType 语义已处理。
        //    filter 经 executeAggregation 默认合并；executeJoin 再次 applyDefaults（AND 幂等，正确不冲突）。
        Map<String, Object> joinResult = joinExecutor.executeJoin(table, joinId, filter, null, 0L, ctx);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mergedRows = joinResult.get("items") instanceof List
                ? (List<Map<String, Object>>) joinResult.get("items")
                : new ArrayList<>();

        // 4. 解析合并行实际 lookup key（右侧冲突前缀）+ 校验 key 存在（key 缺失显式失败，绝不静默 null/0）
        String alias = crossDbAliasOf(join);
        if (!mergedRows.isEmpty()) {
            Map<String, Object> sample = mergedRows.get(0);
            resolveAndValidateLookupKeys(measures, alias, sample, table, joinId, "measure");
            resolveAndValidateLookupKeys(dims, alias, sample, table, joinId, "dimension");
        }

        // 5. 内存 GROUP BY（按 dimension 值分组 → 按 aggFunc 内存累加 → 输出 items）
        List<Map<String, Object>> items = memoryGroupBy(mergedRows, measures, dims);

        // 6. plan 2026-07-18-0900-2：内存 having 过滤（聚合后过滤）
        if (having != null) {
            // 跨库内存路径：having 的 name 解析为 safeAlias(measureName/dimensionName) 大写化 alias
            Map<String, String> nameToAlias = buildCrossDbNameToAliasTable(measures, dims, measureNames,
                    dimensionNames, table);
            MemoryFilterEvaluator evaluator = new MemoryFilterEvaluator(having, nameToAlias, table, measureNames,
                    dimensionNames);
            items = evaluator.filter(items);
        }

        // 7. plan 2026-07-18-0900-2：内存 orderBy 排序 → limit/offset 截断（D3 顺序：orderBy → limit/offset）
        if (orderBy != null && !orderBy.isEmpty()) {
            Map<String, String> nameToAlias = buildCrossDbNameToAliasTable(measures, dims, measureNames,
                    dimensionNames, table);
            items = MemoryOrderByComparator.sort(items, orderBy, nameToAlias, table, measureNames, dimensionNames);
        }

        // 8. 合并后截断提示（D5 分页：内存合并无全局序，limit/offset 仅截断提示）
        return truncateCrossDb(items, limit, offset);
    }

    /** 跨库内存路径 name→大写化 alias 反查表（measure/dimension name → safeAlias(name)，与内存 GROUP BY 输出 key 对齐）。 */
    private static Map<String, String> buildCrossDbNameToAliasTable(List<CrossDbMeasureSpec> measures,
                                                                      List<CrossDbDimensionSpec> dims,
                                                                      List<String> measureNames,
                                                                      List<String> dimensionNames, NopMetaTable table) {
        Map<String, String> map = new LinkedHashMap<>();
        if (measures.size() != measureNames.size()) {
            throw new NopException(ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: cross-db measures/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < measures.size(); i++) {
            map.put(measureNames.get(i), measures.get(i).alias);
        }
        if (dims.size() != dimensionNames.size()) {
            throw new NopException(ERR_AGGR_HAVING_UNKNOWN_NAME)
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

    /** 加载跨库 JOIN 聚合的 Measure 规格（解析 side + rawKey per D10 命名空间规则）。 */
    private List<CrossDbMeasureSpec> loadCrossDbMeasures(NopMetaTable table, List<String> names,
                                                           MetaQueryContext ctx, CrossDbFieldResolver resolver) {
        List<NopMetaTableMeasure> all = loadMeasures(table, names, ctx);
        List<CrossDbMeasureSpec> specs = new ArrayList<>(all.size());
        for (NopMetaTableMeasure m : all) {
            if (m.getExpression() != null && !m.getExpression().trim().isEmpty()) {
                // §4.4.2 D12.2 / D10：expression 型 Measure 在跨库内存路径不可算 → 显式失败
                // （不静默 0、不静默跳过；对齐 D10 既有「不在上列的 aggFunc（含 expression 型 Measure）→ 显式失败」铁律）
                throw new NopException(ERR_AGGR_EXPRESSION_MEMORY_NOT_COMPUTABLE)
                        .param("metaTableId", table.getMetaTableId())
                        .param("measureName", m.getMeasureName())
                        .param("joinId", resolver.joinId());
            }
            CrossDbField f = resolver.resolve(m.getEntityFieldId(), m.getMeasureName(), m.getSide(), "measure");
            specs.add(new CrossDbMeasureSpec(safeAlias(m.getMeasureName()), m.getAggFunc(), f.rawKey, f.side));
        }
        return specs;
    }

    /** 加载跨库 JOIN 聚合的 Dimension 规格（解析 side + rawKey per D10 命名空间规则）。 */
    private List<CrossDbDimensionSpec> loadCrossDbDimensions(NopMetaTable table, List<String> names,
                                                               MetaQueryContext ctx, CrossDbFieldResolver resolver) {
        List<NopMetaTableDimension> all = loadDimensions(table, names, ctx);
        List<CrossDbDimensionSpec> specs = new ArrayList<>(all.size());
        for (NopMetaTableDimension d : all) {
            CrossDbField f = resolver.resolve(d.getEntityFieldId(), d.getDimensionName(), d.getSide(), "dimension");
            specs.add(new CrossDbDimensionSpec(safeAlias(d.getDimensionName()), f.rawKey, f.side));
        }
        return specs;
    }

    /**
     * 解析合并行实际 lookup key + 校验存在（Anti-Hollow #24 核心）。
     *
     * <p>右侧字段经 {@code <alias>_<rawKey>} 冲突前缀：优先查前缀键是否存在于合并行，存在则用前缀键（冲突态），
     * 否则用裸键（非冲突态）。左侧字段直接用裸键。lookup key 在合并行找不到 → 显式失败（**绝不静默返回 null/0**）。
     */
    private void resolveAndValidateLookupKeys(List<? extends CrossDbFieldSpec> specs, String alias,
                                               Map<String, Object> sampleRow, NopMetaTable table, String joinId,
                                               String fieldKind) {
        for (CrossDbFieldSpec spec : specs) {
            String rawKey = spec.rawKey;
            String lookupKey;
            if (_NopMetadataCoreConstants.JOIN_SIDE_RIGHT.equalsIgnoreCase(spec.side)) {
                // 右侧字段：优先查冲突前缀键
                String prefixed = alias + "_" + rawKey;
                String actualPrefixed = findKeyIgnoreCase(sampleRow, prefixed);
                lookupKey = actualPrefixed != null ? actualPrefixed : rawKey;
            } else {
                lookupKey = rawKey;
            }
            String actual = findKeyIgnoreCase(sampleRow, lookupKey);
            if (actual == null) {
                throw new NopException(ERR_AGGR_CROSS_DB_FIELD_KEY_MISSING)
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

    /**
     * 内存 GROUP BY：按 dimension 值分组 → 按 aggFunc 内存累加 → 输出 items。
     *
     * <p>每行合并行按 dimension lookupKey 取值构造 group key（同组 dimension 值相等），measure 按 aggFunc 累加。
     * 输出每个 group 一行：dimension alias→值 + measure alias→聚合结果。
     */
    private List<Map<String, Object>> memoryGroupBy(List<Map<String, Object>> rows,
                                                     List<CrossDbMeasureSpec> measures,
                                                     List<CrossDbDimensionSpec> dims) {
        // 保持首次出现顺序的分组（LinkedHashMap）
        LinkedHashMap<String, Map<String, Object>> groupDims = new LinkedHashMap<>();
        LinkedHashMap<String, MemAggAccumulator[]> groupAccs = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            // 构造 group key（dimension 值字符串拼接，null 用占位符区分）
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
            MemAggAccumulator[] accs = groupAccs.get(groupKey);
            if (gRow == null) {
                gRow = new LinkedHashMap<>();
                for (int i = 0; i < dims.size(); i++) {
                    gRow.put(dims.get(i).alias, dimValues[i]);
                }
                groupDims.put(groupKey, gRow);
                accs = MemAggAccumulator.newAccumulators(measures);
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
            MemAggAccumulator[] accs = groupAccs.get(e.getKey());
            for (int i = 0; i < measures.size(); i++) {
                item.put(measures.get(i).alias, accs[i].result());
            }
            items.add(item);
        }
        return items;
    }

    /** 合并行 Map 大小写不敏感取值（物理列名 H2 常大写 vs entityFieldId 可能小写等情形）。 */
    private static Object getCaseInsensitiveObj(Map<String, Object> map, String key) {
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

    /** 大小写不敏感查找 key（返回 row 中实际存在的 key，或 null）。 */
    private static String findKeyIgnoreCase(Map<String, Object> map, String key) {
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

    /** 跨库 join alias（取自 NopMetaTableJoin.alias，空则 "right"，与 MetaJoinExecutor.mergeRow 一致）。 */
    private static String crossDbAliasOf(NopMetaTableJoin join) {
        String a = join.getAlias();
        return (a != null && !a.trim().isEmpty()) ? a : "right";
    }

    /** 合并后截断提示（D5 分页：内存合并无全局序，limit/offset 仅截断提示）。 */
    private static List<Map<String, Object>> truncateCrossDb(List<Map<String, Object>> rows, Long limit, Long offset) {
        int from = (offset != null && offset > 0) ? Math.toIntExact(offset) : 0;
        if (from > rows.size()) {
            from = rows.size();
        }
        int to = rows.size();
        if (limit != null) {
            to = Math.min(rows.size(), from + Math.toIntExact(limit));
        }
        return new ArrayList<>(rows.subList(from, to));
    }

    /**
     * 跨库 JOIN 聚合字段解析器（plan 1500-2 D10）：按端点组合解析 measure/dimension 的 side（left/right）
     * + rawKey（entity→fieldName 属性名；table→物理列名），复用既有 D8/D9/D1.5 侧别解析语义。
     *
     * <p>三种端点组合：
     * <ul>
     *   <li>entity↔entity：entityFieldId→NopMetaEntityField.metaEntityId 判定 side，rawKey=fieldName（**非 columnCode**）；
     *       side 可选（提供须一致）。</li>
     *   <li>external↔external：side 必填 + 列名存在性校验，rawKey=物理列名。</li>
     *   <li>混合端点（entity↔external/sql）：entityFieldId 为 NopMetaEntityField PK 且归属 entity 端点→entity 侧（rawKey=fieldName）；
     *       否则→table 侧（side 必填 + 列名存在性校验，rawKey=列名）。</li>
     * </ul>
     */
    final class CrossDbFieldResolver {
        private final MetaJoinExecutor.Endpoint leftEp;
        private final MetaJoinExecutor.Endpoint rightEp;
        private final NopMetaTableJoin join;
        private final String joinId;
        private final NopMetaTable ownerTable;
        private final MetaQueryContext ctx;

        // 端点组合判定
        private final boolean entityEntity;
        private final boolean tableTable;
        // 混合端点专用
        private final NopMetaEntity entityEndpoint;
        private final boolean entityOnLeft;
        private final NopMetaTable tableEndpoint;
        private Set<String> tableCols;
        // external↔external 专用
        private Set<String> leftCols;
        private Set<String> rightCols;

        CrossDbFieldResolver(MetaJoinExecutor.Endpoint leftEp, MetaJoinExecutor.Endpoint rightEp,
                              NopMetaTableJoin join, String joinId, NopMetaTable ownerTable, MetaQueryContext ctx) {
            this.leftEp = leftEp;
            this.rightEp = rightEp;
            this.join = join;
            this.joinId = joinId;
            this.ownerTable = ownerTable;
            this.ctx = ctx;
            this.entityEntity = leftEp.isEntity() && rightEp.isEntity();
            this.tableTable = !leftEp.isEntity() && !rightEp.isEntity();
            this.entityOnLeft = leftEp.isEntity();
            this.entityEndpoint = entityEntity ? null : (leftEp.isEntity() ? leftEp.entity : rightEp.entity);
            this.tableEndpoint = entityEntity ? null : (leftEp.isEntity() ? rightEp.table : leftEp.table);
        }

        CrossDbField resolve(String entityFieldId, String name, String declaredSide, String fieldKind) {
            if (entityEntity) {
                return resolveEntityEntity(entityFieldId, name, declaredSide);
            }
            if (tableTable) {
                return resolveTableTable(entityFieldId, name, declaredSide);
            }
            return resolveMixed(entityFieldId, name, declaredSide);
        }

        /** 当前 join 的 joinId（供 loadCrossDbMeasures 抛 expression 错误时附加上下文）。 */
        String joinId() {
            return joinId;
        }

        /** entity↔entity 跨库：entityFieldId→metaEntityId 判定 side，rawKey=fieldName。 */
        private CrossDbField resolveEntityEntity(String entityFieldId, String name, String declaredSide) {
            if (entityFieldId == null || entityFieldId.isEmpty()) {
                throw new NopException(ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
            }
            IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
            NopMetaEntityField field = fieldDao.getEntityById(entityFieldId);
            if (field == null || field.getFieldName() == null || field.getFieldName().isEmpty()) {
                throw new NopException(ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", entityFieldId);
            }
            String fieldMetaEntityId = field.getMetaEntityId();
            String resolvedSide;
            if (equalsStr(fieldMetaEntityId, leftEp.entity.getMetaEntityId())) {
                resolvedSide = _NopMetadataCoreConstants.JOIN_SIDE_LEFT;
            } else if (equalsStr(fieldMetaEntityId, rightEp.entity.getMetaEntityId())) {
                resolvedSide = _NopMetadataCoreConstants.JOIN_SIDE_RIGHT;
            } else {
                throw new NopException(ERR_AGGR_JOIN_FIELD_SIDE_UNRESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", entityFieldId)
                        .param("fieldMetaEntityId", String.valueOf(fieldMetaEntityId))
                        .param("leftEntityId", String.valueOf(leftEp.entity.getMetaEntityId()))
                        .param("rightEntityId", String.valueOf(rightEp.entity.getMetaEntityId()))
                        .param("joinId", joinId);
            }
            if (declaredSide != null && !declaredSide.isEmpty()
                    && !declaredSide.equalsIgnoreCase(resolvedSide)) {
                throw new NopException(ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name)
                        .param("declaredSide", declaredSide)
                        .param("resolvedSide", resolvedSide)
                        .param("fieldMetaEntityId", String.valueOf(fieldMetaEntityId))
                        .param("joinId", joinId);
            }
            // entity 端点行 key = camelCase 属性名（D10 命名空间规则，非 columnCode）
            return new CrossDbField(resolvedSide, field.getFieldName());
        }

        /** external↔external 跨库：side 必填 + 列名存在性校验，rawKey=物理列名。 */
        private CrossDbField resolveTableTable(String columnName, String name, String declaredSide) {
            if (declaredSide == null || declaredSide.isEmpty()) {
                throw new NopException(ERR_AGGR_JOIN_SIDE_REQUIRED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("joinId", joinId);
            }
            if (columnName == null || columnName.isEmpty()) {
                throw new NopException(ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", String.valueOf(columnName));
            }
            String resolvedSide;
            Set<String> cols;
            if (_NopMetadataCoreConstants.JOIN_SIDE_LEFT.equalsIgnoreCase(declaredSide)) {
                resolvedSide = _NopMetadataCoreConstants.JOIN_SIDE_LEFT;
                cols = ensureLeftCols();
            } else if (_NopMetadataCoreConstants.JOIN_SIDE_RIGHT.equalsIgnoreCase(declaredSide)) {
                resolvedSide = _NopMetadataCoreConstants.JOIN_SIDE_RIGHT;
                cols = ensureRightCols();
            } else {
                throw new NopException(ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", "unknown")
                        .param("column", columnName).param("joinId", joinId);
            }
            if (!containsIgnoreCase(cols, columnName)) {
                throw new NopException(ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", String.valueOf(
                                _NopMetadataCoreConstants.JOIN_SIDE_LEFT.equalsIgnoreCase(declaredSide)
                                        ? leftEp.table.getTableType() : rightEp.table.getTableType()))
                        .param("column", columnName).param("joinId", joinId);
            }
            return new CrossDbField(resolvedSide, columnName);
        }

        /** 混合端点跨库：entity 侧 rawKey=fieldName；table 侧 side 必填 + 列名存在性校验，rawKey=列名。 */
        private CrossDbField resolveMixed(String entityFieldId, String name, String declaredSide) {
            if (entityFieldId == null || entityFieldId.isEmpty()) {
                throw new NopException(ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
            }
            // 尝试 entity 侧归属
            NopMetaEntityField field = tryLoadEntityField(entityFieldId);
            if (field != null && field.getFieldName() != null && !field.getFieldName().isEmpty()
                    && equalsStr(field.getMetaEntityId(), entityEndpoint.getMetaEntityId())) {
                String resolvedSide = entityOnLeft
                        ? _NopMetadataCoreConstants.JOIN_SIDE_LEFT : _NopMetadataCoreConstants.JOIN_SIDE_RIGHT;
                if (declaredSide != null && !declaredSide.isEmpty()
                        && !declaredSide.equalsIgnoreCase(resolvedSide)) {
                    throw new NopException(ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH)
                            .param("metaTableId", ownerTable.getMetaTableId())
                            .param("name", name)
                            .param("declaredSide", declaredSide)
                            .param("resolvedSide", resolvedSide)
                            .param("fieldMetaEntityId", String.valueOf(field.getMetaEntityId()))
                            .param("joinId", joinId);
                }
                return new CrossDbField(resolvedSide, field.getFieldName());
            }
            // table 侧：side 必填
            if (declaredSide == null || declaredSide.isEmpty()) {
                throw new NopException(ERR_AGGR_JOIN_SIDE_REQUIRED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("joinId", joinId);
            }
            String expectedSide = entityOnLeft
                    ? _NopMetadataCoreConstants.JOIN_SIDE_RIGHT : _NopMetadataCoreConstants.JOIN_SIDE_LEFT;
            if (!expectedSide.equalsIgnoreCase(declaredSide)) {
                throw new NopException(ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", "entity")
                        .param("column", entityFieldId).param("joinId", joinId);
            }
            Set<String> cols = ensureMixedTableCols();
            if (!containsIgnoreCase(cols, entityFieldId)) {
                throw new NopException(ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", String.valueOf(tableEndpoint.getTableType()))
                        .param("column", entityFieldId).param("joinId", joinId);
            }
            return new CrossDbField(declaredSide.toLowerCase(java.util.Locale.ROOT), entityFieldId);
        }

        private Set<String> ensureLeftCols() {
            if (leftCols == null) {
                leftCols = resolveTableColumnNames(leftEp.table,
                        ctx.daoProvider().daoFor(NopMetaEntityField.class), ctx);
            }
            return leftCols;
        }

        private Set<String> ensureRightCols() {
            if (rightCols == null) {
                rightCols = resolveTableColumnNames(rightEp.table,
                        ctx.daoProvider().daoFor(NopMetaEntityField.class), ctx);
            }
            return rightCols;
        }

        private Set<String> ensureMixedTableCols() {
            if (tableCols == null) {
                tableCols = resolveTableColumnNames(tableEndpoint,
                        ctx.daoProvider().daoFor(NopMetaEntityField.class), ctx);
            }
            return tableCols;
        }

        private NopMetaEntityField tryLoadEntityField(String entityFieldId) {
            try {
                return ctx.daoProvider().daoFor(NopMetaEntityField.class).getEntityById(entityFieldId);
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** 跨库字段解析结果：side（left/right）+ rawKey（entity=fieldName / table=物理列名）。 */
    private static final class CrossDbField {
        final String side;
        final String rawKey;

        CrossDbField(String side, String rawKey) {
            this.side = side;
            this.rawKey = rawKey;
        }
    }

    /** 跨库字段规格基类：alias（输出列名）+ rawKey + side + lookupKey（合并行实际 key，解析后填充）。 */
    private abstract static class CrossDbFieldSpec {
        final String alias;
        final String rawKey;
        final String side;
        String lookupKey;

        CrossDbFieldSpec(String alias, String rawKey, String side) {
            this.alias = alias;
            this.rawKey = rawKey;
            this.side = side;
            this.lookupKey = rawKey;
        }
    }

    /** 跨库 Measure 规格：alias + aggFunc + rawKey + side。 */
    private static final class CrossDbMeasureSpec extends CrossDbFieldSpec {
        final String aggFunc;

        CrossDbMeasureSpec(String alias, String aggFunc, String rawKey, String side) {
            super(alias, rawKey, side);
            this.aggFunc = aggFunc;
        }
    }

    /** 跨库 Dimension 规格：alias + rawKey + side。 */
    private static final class CrossDbDimensionSpec extends CrossDbFieldSpec {
        CrossDbDimensionSpec(String alias, String rawKey, String side) {
            super(alias, rawKey, side);
        }
    }

    // ============================ 内存聚合累加器（plan 1500-2 D10）============================

    /**
     * 内存聚合累加器（§4.4.2 D10 aggFunc 内存可计算性）：按 aggFunc 分派子类，accumulate 逐行累加，
     * result 输出聚合值（null 表示空集聚合——avg count=0、全 null min/max）。
     */
    private abstract static class MemAggAccumulator {
        abstract void accumulate(Object v);

        abstract Object result();

        static MemAggAccumulator[] newAccumulators(List<CrossDbMeasureSpec> measures) {
            MemAggAccumulator[] accs = new MemAggAccumulator[measures.size()];
            for (int i = 0; i < measures.size(); i++) {
                accs[i] = forFunc(measures.get(i).aggFunc, measures.get(i).alias);
            }
            return accs;
        }

        static MemAggAccumulator forFunc(String aggFunc, String name) {
            if (aggFunc == null) {
                throw new NopException(ERR_AGGR_AGG_FUNC_UNSUPPORTED)
                        .param("aggFunc", String.valueOf(aggFunc)).param("measureName", name);
            }
            switch (aggFunc) {
                case _NopMetadataCoreConstants.AGG_FUNC_SUM:
                    return new SumAcc();
                case _NopMetadataCoreConstants.AGG_FUNC_COUNT:
                    return new CountAcc();
                case _NopMetadataCoreConstants.AGG_FUNC_AVG:
                    return new AvgAcc();
                case _NopMetadataCoreConstants.AGG_FUNC_MIN:
                    return new MinAcc();
                case _NopMetadataCoreConstants.AGG_FUNC_MAX:
                    return new MaxAcc();
                case _NopMetadataCoreConstants.AGG_FUNC_COUNT_DISTINCT:
                    return new CountDistinctAcc();
                default:
                    throw new NopException(ERR_AGGR_AGG_FUNC_UNSUPPORTED)
                            .param("aggFunc", aggFunc).param("measureName", name);
            }
        }
    }

    /** sum：累加数值（BigDecimal 防溢出），null 跳过。 */
    private static final class SumAcc extends MemAggAccumulator {
        private java.math.BigDecimal sum;
        private boolean hasValue;

        @Override
        public void accumulate(Object v) {
            if (v == null) {
                return;
            }
            java.math.BigDecimal n = toBigDecimal(v);
            if (n != null) {
                sum = (sum == null) ? n : sum.add(n);
                hasValue = true;
            }
        }

        @Override
        public Object result() {
            return hasValue ? sum : null;
        }
    }

    /** count：非空值计数（null 跳过）。 */
    private static final class CountAcc extends MemAggAccumulator {
        private long count;

        @Override
        public void accumulate(Object v) {
            if (v != null) {
                count++;
            }
        }

        @Override
        public Object result() {
            return count;
        }
    }

    /** avg：sum/count，count=0 → null（非伪造 0）。 */
    private static final class AvgAcc extends MemAggAccumulator {
        private java.math.BigDecimal sum;
        private long count;

        @Override
        public void accumulate(Object v) {
            if (v == null) {
                return;
            }
            java.math.BigDecimal n = toBigDecimal(v);
            if (n != null) {
                sum = (sum == null) ? n : sum.add(n);
                count++;
            }
        }

        @Override
        public Object result() {
            if (count == 0) {
                return null;
            }
            return sum.divide(java.math.BigDecimal.valueOf(count), java.math.MathContext.DECIMAL64);
        }
    }

    /** min：比较取最小（Comparable），null 跳过，全 null → null。 */
    private static final class MinAcc extends MemAggAccumulator {
        private Comparable<Object> min;
        private boolean hasValue;

        @Override
        @SuppressWarnings("unchecked")
        public void accumulate(Object v) {
            if (v == null) {
                return;
            }
            Comparable<Object> c = (Comparable<Object>) v;
            if (!hasValue || c.compareTo(min) < 0) {
                min = c;
                hasValue = true;
            }
        }

        @Override
        public Object result() {
            return hasValue ? min : null;
        }
    }

    /** max：比较取最大（Comparable），null 跳过，全 null → null。 */
    private static final class MaxAcc extends MemAggAccumulator {
        private Comparable<Object> max;
        private boolean hasValue;

        @Override
        @SuppressWarnings("unchecked")
        public void accumulate(Object v) {
            if (v == null) {
                return;
            }
            Comparable<Object> c = (Comparable<Object>) v;
            if (!hasValue || c.compareTo(max) > 0) {
                max = c;
                hasValue = true;
            }
        }

        @Override
        public Object result() {
            return hasValue ? max : null;
        }
    }

    /** countDistinct：内存去重（LinkedHashSet），结果 = 去重后基数（null 不计入）。 */
    private static final class CountDistinctAcc extends MemAggAccumulator {
        private final Set<Object> distinct = new LinkedHashSet<>();

        @Override
        public void accumulate(Object v) {
            if (v != null) {
                distinct.add(v);
            }
        }

        @Override
        public Object result() {
            return (long) distinct.size();
        }
    }

    /** 值→BigDecimal 转换（Integer/Long/Double/Float/BigDecimal/BigInteger 等）。非数值返回 null（不静默当 0）。 */
    private static java.math.BigDecimal toBigDecimal(Object v) {
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

    private static String endpointTypeOf(MetaJoinExecutor.Endpoint ep) {
        if (ep.isEntity) {
            return "entity";
        }
        return ep.table == null ? "unknown" : String.valueOf(ep.table.getTableType());
    }

    /** 加载 JOIN 聚合的 Measure 规格列表（按 metaEntityId 判定左/右归属 + side 一致性校验 + 物理列解析 + expression 支持）。 */
    private List<JoinMeasureSpec> loadJoinMeasures(NopMetaTable table, List<String> names, MetaQueryContext ctx,
                                                    JoinFieldResolver resolver) {
        List<NopMetaTableMeasure> all = loadMeasures(table, names, ctx);
        // entity-entity JOIN 上下文：左右端点字段集合（columnCode）供 expression 的 l./r. 限定列名校验
        Set<String> leftCols = resolver.resolveEntityColumns(resolver.leftEntityId());
        Set<String> rightCols = resolver.resolveEntityColumns(resolver.rightEntityId());
        List<JoinMeasureSpec> specs = new ArrayList<>();
        for (NopMetaTableMeasure m : all) {
            if (m.getExpression() != null && !m.getExpression().trim().isEmpty()) {
                // expression 型：dialect-independent 静态校验（JOIN strict，按 l./r. 前缀校验列归属）
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

    /** 加载 JOIN 聚合的 Dimension 规格列表（按 metaEntityId 判定左/右归属 + side 一致性校验 + 物理列解析）。 */
    private List<JoinDimensionSpec> loadJoinDimensions(NopMetaTable table, List<String> names, MetaQueryContext ctx,
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

    /** 加载 external↔external JOIN 聚合的 Measure 规格（side 必填 + 列名存在性校验 + expression 支持）。 */
    private List<JoinMeasureSpec> loadExternalJoinMeasures(NopMetaTable table, List<String> names,
                                                            MetaQueryContext ctx, JoinExternalSideResolver resolver) {
        return loadJoinMeasuresWithResolver(table, names, ctx, resolver::resolve,
                resolver.leftColumns(), resolver.rightColumns());
    }

    /** 加载 external↔external JOIN 聚合的 Dimension 规格（side 必填 + 列名存在性校验）。 */
    private List<JoinDimensionSpec> loadExternalJoinDimensions(NopMetaTable table, List<String> names,
                                                                MetaQueryContext ctx, JoinExternalSideResolver resolver) {
        return loadJoinDimensionsWithResolver(table, names, ctx, resolver::resolve);
    }

    /** 加载混合端点 JOIN 聚合的 Measure 规格（plan 1500-1：entity 侧 entityFieldId→columnCode，table 侧 side 必填 + expression 支持）。 */
    private List<JoinMeasureSpec> loadExternalJoinMeasures(NopMetaTable table, List<String> names,
                                                            MetaQueryContext ctx, JoinMixedSideResolver resolver) {
        return loadJoinMeasuresWithResolver(table, names, ctx, resolver::resolve,
                resolver.leftColumns(), resolver.rightColumns());
    }

    /** 加载混合端点 JOIN 聚合的 Dimension 规格（plan 1500-1：entity 侧 entityFieldId→columnCode，table 侧 side 必填）。 */
    private List<JoinDimensionSpec> loadExternalJoinDimensions(NopMetaTable table, List<String> names,
                                                                MetaQueryContext ctx, JoinMixedSideResolver resolver) {
        return loadJoinDimensionsWithResolver(table, names, ctx, resolver::resolve);
    }

    /** JOIN 字段解析函数式契约（{@code (entityFieldId, name, side) → JoinField}），供 external↔external / 混合端点共用。 */
    @FunctionalInterface
    private interface JoinFieldResolverFn {
        JoinField resolve(String entityFieldId, String name, String declaredSide);
    }

    private List<JoinMeasureSpec> loadJoinMeasuresWithResolver(NopMetaTable table, List<String> names,
                                                                MetaQueryContext ctx, JoinFieldResolverFn resolver,
                                                                Set<String> leftCols, Set<String> rightCols) {
        List<NopMetaTableMeasure> all = loadMeasures(table, names, ctx);
        List<JoinMeasureSpec> specs = new ArrayList<>();
        for (NopMetaTableMeasure m : all) {
            if (m.getExpression() != null && !m.getExpression().trim().isEmpty()) {
                // expression 型：JOIN 上下文 dialect-independent 静态校验（严格 l./r. 限定 + 端点字段集合校验）
                ExpressionMeasureValidator.ValidatedExpression ve =
                        ExpressionMeasureValidator.validateStatic(m.getExpression(),
                                ExpressionMeasureValidator.ValidationOptions.joinStrict(leftCols, rightCols),
                                table.getMetaTableId(), m.getMeasureName());
                specs.add(new JoinMeasureSpec(safeAlias(m.getMeasureName()),
                        aggSqlOf(m.getAggFunc(), ve.sqlFragment, m.getMeasureName()),
                        "<expression>", ve.params, ve));
                continue;
            }
            JoinField f = resolver.resolve(m.getEntityFieldId(), m.getMeasureName(), m.getSide());
            FilterToSqlTranslator.validateIdentifier(f.column);
            specs.add(new JoinMeasureSpec(safeAlias(m.getMeasureName()),
                    aggSqlOf(m.getAggFunc(), f.qualifiedColumn, m.getMeasureName()), f.qualifiedColumn));
        }
        return specs;
    }

    private List<JoinDimensionSpec> loadJoinDimensionsWithResolver(NopMetaTable table, List<String> names,
                                                                    MetaQueryContext ctx, JoinFieldResolverFn resolver) {
        List<NopMetaTableDimension> all = loadDimensions(table, names, ctx);
        List<JoinDimensionSpec> specs = new ArrayList<>();
        for (NopMetaTableDimension d : all) {
            JoinField f = resolver.resolve(d.getEntityFieldId(), d.getDimensionName(), d.getSide());
            FilterToSqlTranslator.validateIdentifier(f.column);
            specs.add(new JoinDimensionSpec(safeAlias(d.getDimensionName()), f.qualifiedColumn, f.column,
                    d.getDimensionType(), d.getGranularity()));
        }
        return specs;
    }

    private static boolean equalsStr(String a, String b) {
        return (a == null) ? b == null : a.equals(b);
    }

    /**
     * JOIN 字段归属解析器（entity 路径）：按 {@code entityFieldId → NopMetaEntityField.metaEntityId} 判定字段属于左/右 entity，
     * 解析物理列并构造限定别名（{@code l.<col>} / {@code r.<col>}）。
     *
     * <p>side 一致性校验（§4.4.2 D9，plan 1200-1）：entity 端点 side **可选**；若 Measure/Dimension 提供 side，
     * 须与 metaEntityId 判定的端点一致，不一致显式失败。
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

        JoinField resolve(String entityFieldId, String name, String declaredSide, String refKind) {
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
            String resolvedSide;
            String alias;
            if (equalsStr(fieldMetaEntityId, leftEntityId)) {
                resolvedSide = "left";
                alias = "l";
            } else if (equalsStr(fieldMetaEntityId, rightEntityId)) {
                resolvedSide = "right";
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
            // side 一致性校验：entity 端点 side 可选；若提供须与 metaEntityId 判定的端点一致
            if (declaredSide != null && !declaredSide.isEmpty()
                    && !declaredSide.equalsIgnoreCase(resolvedSide)) {
                throw new NopException(ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", name)
                        .param("declaredSide", declaredSide)
                        .param("resolvedSide", resolvedSide)
                        .param("fieldMetaEntityId", String.valueOf(fieldMetaEntityId))
                        .param("joinId", joinId);
            }
            return new JoinField(column, alias + "." + column);
        }

        /** 左 entity 的 metaEntityId（供 expression 校验时按端点解析列集合）。 */
        String leftEntityId() {
            return leftEntityId;
        }

        /** 右 entity 的 metaEntityId。 */
        String rightEntityId() {
            return rightEntityId;
        }

        /** 按 metaEntityId 解析该 entity 的 columnCode 集合（供 expression JOIN 限定名校验）。 */
        Set<String> resolveEntityColumns(String metaEntityId) {
            IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
            QueryBean q = new QueryBean();
            q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_metaEntityId, metaEntityId));
            List<NopMetaEntityField> fields = fieldDao.findAllByQuery(q);
            Set<String> cols = new LinkedHashSet<>();
            for (NopMetaEntityField f : fields) {
                if (f.getColumnCode() != null) {
                    cols.add(f.getColumnCode());
                }
            }
            return cols;
        }

        private static boolean equalsStr(String a, String b) {
            return (a == null) ? b == null : a.equals(b);
        }
    }

    /**
     * JOIN 字段侧别解析器（external/sql 路径，§4.4.2 D9）：按 Measure/Dimension 的 **side**（必填）绑定端点，
     * 解析物理列（= entityFieldId 列名字符串）并校验列名存在于所绑定端点的解析字段集合，构造限定别名
     * （{@code l.<col>} / {@code r.<col>}）。
     *
     * <p>失败路径显式化：side 缺失（→ {@link #ERR_AGGR_JOIN_SIDE_REQUIRED}）/ side 指向端点无此列
     * （→ {@link #ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE}）。
     */
    static final class JoinExternalSideResolver {
        private final NopMetaTable leftTable;
        private final NopMetaTable rightTable;
        private final Set<String> leftCols;
        private final Set<String> rightCols;
        private final String joinId;
        private final NopMetaTable ownerTable;

        JoinExternalSideResolver(NopMetaTable leftTable, NopMetaTable rightTable,
                                 Set<String> leftCols, Set<String> rightCols,
                                 String joinId, NopMetaTable ownerTable) {
            this.leftTable = leftTable;
            this.rightTable = rightTable;
            this.leftCols = leftCols;
            this.rightCols = rightCols;
            this.joinId = joinId;
            this.ownerTable = ownerTable;
        }

        /** 左端点字段集合（供 expression JOIN 限定名校验）。 */
        Set<String> leftColumns() {
            return leftCols;
        }

        /** 右端点字段集合。 */
        Set<String> rightColumns() {
            return rightCols;
        }

        JoinField resolve(String columnName, String name, String declaredSide) {
            // external/sql 端点 side 必填（null → 显式失败，不依赖是否歧义）
            if (declaredSide == null || declaredSide.isEmpty()) {
                throw new NopException(ERR_AGGR_JOIN_SIDE_REQUIRED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("joinId", joinId);
            }
            // entityFieldId 即物理列名（external/sql 表存裸列名字符串）
            if (columnName == null || columnName.isEmpty()) {
                throw new NopException(ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", String.valueOf(columnName));
            }
            String alias;
            String endpointType;
            Set<String> cols;
            if ("left".equalsIgnoreCase(declaredSide)) {
                alias = "l";
                endpointType = String.valueOf(leftTable.getTableType());
                cols = leftCols;
            } else if ("right".equalsIgnoreCase(declaredSide)) {
                alias = "r";
                endpointType = String.valueOf(rightTable.getTableType());
                cols = rightCols;
            } else {
                // side 枚举非法（非 left/right）→ 显式失败
                throw new NopException(ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", "unknown")
                        .param("column", columnName).param("joinId", joinId);
            }
            // 列名存在性校验：side 绑定端点的解析字段集合须含该列（不静默归属、不静默跳过）
            if (!containsIgnoreCase(cols, columnName)) {
                throw new NopException(ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", endpointType)
                        .param("column", columnName).param("joinId", joinId);
            }
            return new JoinField(columnName, alias + "." + columnName);
        }

        private static boolean containsIgnoreCase(Set<String> cols, String name) {
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
    }

    /**
     * JOIN 字段侧别解析器（混合端点路径，plan 1500-1 D1.5，§4.4.1 D1.5）。
     *
     * <p>端点组合：一端 entity（{@code entityEndpoint}），另一端 external/sql table（{@code tableEndpoint}）。
     * {@code entityOnLeft} 标识 entity 在 left（别名 l）还是 right（别名 r）；table 端点取相反别名。
     *
     * <p>resolve(entityFieldId, name, declaredSide) 判定侧别（F1-2：仅 table 侧套用 side resolver）：
     * <ul>
     *   <li>entityFieldId 为 {@code NopMetaEntityField} PK 且其 {@code metaEntityId} == entity 端点 → <b>entity 侧</b>：
     *       解析 columnCode，alias 取 entity 端点别名。side 可选（与 D9 entity side 一致性校验同范式）；
     *       若提供须与 entity 端点位置一致，不一致显式失败。</li>
     *   <li>否则 → <b>table 侧</b>：entityFieldId 即裸列名字符串。side 必填（与 D9 external/sql side 必填规则一致），
     *       须指向 table 端点位置；列名须存在于 table 端点解析字段集合，否则显式失败。</li>
     * </ul>
     *
     * <p>失败路径显式：entityFieldId 既非 entity 端点字段也非 table 端点列名 → {@link #ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE}；
     * table 侧 side 缺失 → {@link #ERR_AGGR_JOIN_SIDE_REQUIRED}；entity 侧 side 不一致 → {@link #ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH}。
     */
    static final class JoinMixedSideResolver {
        private final NopMetaEntity entityEndpoint;
        private final Map<String, String> entityPropToCol;
        private final NopMetaTable tableEndpoint;
        private final Set<String> tableCols;
        private final boolean entityOnLeft;
        private final String joinId;
        private final NopMetaTable ownerTable;
        private final MetaQueryContext ctx;

        JoinMixedSideResolver(NopMetaEntity entityEndpoint, Map<String, String> entityPropToCol,
                              NopMetaTable tableEndpoint, Set<String> tableCols,
                              boolean entityOnLeft, String joinId, NopMetaTable ownerTable,
                              MetaQueryContext ctx) {
            this.entityEndpoint = entityEndpoint;
            this.entityPropToCol = entityPropToCol;
            this.tableEndpoint = tableEndpoint;
            this.tableCols = tableCols;
            this.entityOnLeft = entityOnLeft;
            this.joinId = joinId;
            this.ownerTable = ownerTable;
            this.ctx = ctx;
        }

        /** 左端点字段集合（混合端点按 entityOnLeft 决定是 entity columnCode 还是 table 物理列名）。 */
        Set<String> leftColumns() {
            return entityOnLeft ? entityColumnCodes() : tableCols;
        }

        /** 右端点字段集合。 */
        Set<String> rightColumns() {
            return entityOnLeft ? tableCols : entityColumnCodes();
        }

        /** entity 端点 columnCode 集合（取自 entityPropToCol.values()）。 */
        private Set<String> entityColumnCodes() {
            Set<String> cols = new LinkedHashSet<>();
            if (entityPropToCol != null) {
                cols.addAll(entityPropToCol.values());
            }
            return cols;
        }

        JoinField resolve(String entityFieldId, String name, String declaredSide) {
            if (entityFieldId == null || entityFieldId.isEmpty()) {
                throw new NopException(ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
            }
            // 尝试 entity 侧归属：entityFieldId 为 NopMetaEntityField PK 且其 metaEntityId == entity 端点
            NopMetaEntityField field = tryLoadEntityField(entityFieldId);
            if (field != null && equalsStr(field.getMetaEntityId(), entityEndpoint.getMetaEntityId())) {
                // entity 侧
                String column = field.getColumnCode();
                if (column == null || column.isEmpty()) {
                    throw new NopException(ERR_AGGR_FIELD_NOT_RESOLVED)
                            .param("metaTableId", ownerTable.getMetaTableId())
                            .param("name", name).param("entityFieldId", entityFieldId);
                }
                String resolvedSide = entityOnLeft ? "left" : "right";
                String alias = entityOnLeft ? "l" : "r";
                // side 一致性校验：entity 端点 side 可选；若提供须与端点位置一致
                if (declaredSide != null && !declaredSide.isEmpty()
                        && !declaredSide.equalsIgnoreCase(resolvedSide)) {
                    throw new NopException(ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH)
                            .param("metaTableId", ownerTable.getMetaTableId())
                            .param("name", name)
                            .param("declaredSide", declaredSide)
                            .param("resolvedSide", resolvedSide)
                            .param("fieldMetaEntityId", String.valueOf(field.getMetaEntityId()))
                            .param("joinId", joinId);
                }
                return new JoinField(column, alias + "." + column);
            }
            // table 侧：entityFieldId 即裸列名字符串，side 必填 + 列名存在性校验
            if (declaredSide == null || declaredSide.isEmpty()) {
                throw new NopException(ERR_AGGR_JOIN_SIDE_REQUIRED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("joinId", joinId);
            }
            String expectedSide = entityOnLeft ? "right" : "left";
            String alias;
            if (expectedSide.equalsIgnoreCase(declaredSide)) {
                alias = entityOnLeft ? "r" : "l";
            } else {
                // side 指向 entity 端点位置但该字段非 entity 字段（既不是 entity PK 也不是 entity 字段）→ 字段不可归属
                throw new NopException(ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", "entity")
                        .param("column", entityFieldId).param("joinId", joinId);
            }
            if (!containsIgnoreCase(tableCols, entityFieldId)) {
                throw new NopException(ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", String.valueOf(tableEndpoint.getTableType()))
                        .param("column", entityFieldId).param("joinId", joinId);
            }
            return new JoinField(entityFieldId, alias + "." + entityFieldId);
        }

        /** 尝试按 PK 加载 NopMetaEntityField；非 PK 或不存在返回 null（不抛错，调用方判定归属）。 */
        private NopMetaEntityField tryLoadEntityField(String entityFieldId) {
            try {
                IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
                return fieldDao.getEntityById(entityFieldId);
            } catch (Exception e) {
                return null;
            }
        }

        private static boolean equalsStr(String a, String b) {
            return (a == null) ? b == null : a.equals(b);
        }

        private static boolean containsIgnoreCase(Set<String> cols, String name) {
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
    }

    /** JOIN Measure 规格：别名 + 聚合 SQL 表达式（含 table-qualified 列）+ 限定列（供白名单校验）+ expression 信息（plan 2026-07-18-1400-1）。 */
    private static final class JoinMeasureSpec {
        final String alias;
        final String aggSql;
        final String qualifiedAggCol;
        /** expression 型 Measure 的字面量参数列表；非 expression 型为 null。 */
        final List<Object> expressionParams;
        /** expression 型 Measure 的 validator 产出物；非 expression 型为 null。 */
        final ExpressionMeasureValidator.ValidatedExpression validatedExpression;

        JoinMeasureSpec(String alias, String aggSql, String qualifiedAggCol) {
            this(alias, aggSql, qualifiedAggCol, null, null);
        }

        JoinMeasureSpec(String alias, String aggSql, String qualifiedAggCol,
                       List<Object> expressionParams,
                       ExpressionMeasureValidator.ValidatedExpression validatedExpression) {
            this.alias = alias;
            this.aggSql = aggSql;
            this.qualifiedAggCol = qualifiedAggCol;
            this.expressionParams = expressionParams;
            this.validatedExpression = validatedExpression;
        }

        boolean isExpression() {
            return validatedExpression != null;
        }
    }

    /** JOIN Dimension 规格：别名 + table-qualified 列表达式 + 裸物理列（granularity 白名单校验用）+ 类型 + granularity。 */
    private static final class JoinDimensionSpec {
        final String alias;
        final String qualifiedCol;
        final String column;
        final String dimensionType;
        final String granularity;

        JoinDimensionSpec(String alias, String qualifiedCol, String column, String dimensionType, String granularity) {
            this.alias = alias;
            this.qualifiedCol = qualifiedCol;
            this.column = column;
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
                                                                 Long limit, Long offset, TreeBean having,
                                                                 List<OrderFieldBean> orderBy, MetaQueryContext ctx) {
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

        // name→SQL 表达式反查表（plan 2026-07-18-0900-2：having/orderBy name 解析）
        Map<String, String> nameToExpr = buildNameToExprTable(measures, dims, measureNames, dimensionNames, table);

        final List<Map<String, Object>>[] holder = newArrayHolder();
        final Map<String, String> _nameToExpr = nameToExpr;
        ctx.connectionService().withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    String dialect = safeProductName(metaData);
                    if (dialect == null || !SUPPORTED_DIALECTS.contains(dialect)) {
                        throw new NopException(ERR_AGGR_UNSUPPORTED_DIALECT)
                                .param("databaseProductName", String.valueOf(dialect))
                                .param("metaTableId", table.getMetaTableId());
                    }
                    // plan 2026-07-18-1400-1：expression 型 measure 的 dialect-specific 函数支持检查（dialect 已知）
                    for (MeasureSpec m : measures) {
                        if (m.isExpression()) {
                            ExpressionMeasureValidator.checkDialectSupported(m.validatedExpression, dialect,
                                    table.getMetaTableId(), m.alias);
                        }
                    }
                    String sqlText = buildExternalAggregationSql(table, measures, dims, filter, having, orderBy,
                            _nameToExpr, measureNames, dimensionNames, limit, offset, dialect, ctx);
                    LOG.info("queryAggregation external/sql SQL: {}", sqlText);
                    holder[0] = executeJdbcQuery(conn, sqlText, collectBindParams(measures, dims, filter, having,
                            _nameToExpr, ctx, table, measureNames, dimensionNames),
                            limit, offset, table.getMetaTableId());
                });
        return holder[0] == null ? new ArrayList<>() : holder[0];
    }

    private String buildExternalAggregationSql(NopMetaTable table, List<MeasureSpec> measures,
                                                List<DimensionSpec> dims, TreeBean filter, TreeBean having,
                                                List<OrderFieldBean> orderBy, Map<String, String> nameToExpr,
                                                List<String> measureNames, List<String> dimensionNames,
                                                Long limit, Long offset,
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
        // plan 2026-07-18-0900-2：HAVING 子句
        // plan 2026-07-18-1500-2：先 preprocess 多列算术 leaf
        if (having != null) {
            preprocessHavingArithmetic(having, nameToExpr, table, measureNames, dimensionNames);
            FilterToSqlTranslator.TranslatedFilter hf = ctx.filterTranslator().translate(having,
                    nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
            if (hf.getSql() != null && !hf.getSql().isEmpty()) {
                sql.append(" HAVING ").append(hf.getSql());
            }
        }
        // plan 2026-07-18-0900-2：ORDER BY 子句
        String orderByClause = buildOrderByClause(orderBy, nameToExpr, table, measureNames, dimensionNames, "ORDER_BY");
        if (!orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }
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

    /**
     * 收集 PreparedStatement 绑定参数，与 SQL 中 ? 出现顺序一致；limit/offset 由 executeJdbcQuery 追加。
     *
     * <p>plan 2026-07-18-1400-1：参数绑定顺序（R1 Blocker 修正）—— SQL 子句顺序为
     * {@code SELECT → WHERE → GROUP BY → HAVING → ORDER BY → LIMIT → OFFSET}，expression 字面量出现在
     * SELECT 的 {@code <agg>(<expression>)} 内，排在 filter（WHERE）之前。正确合并顺序为：
     * <b>expression 字面量 → filter 值 → having 值</b>（limit/offset 由 executeJdbcQuery 追加）。
     */
    private List<Object> collectBindParams(List<MeasureSpec> measures, List<DimensionSpec> dims, TreeBean filter,
                                           TreeBean having, Map<String, String> nameToExpr, MetaQueryContext ctx,
                                           NopMetaTable table,
                                           List<String> measureNames, List<String> dimensionNames) {
        List<Object> params = new ArrayList<>();
        // 1. expression 字面量（SELECT 内）
        for (MeasureSpec m : measures) {
            if (m.expressionParams != null) {
                params.addAll(m.expressionParams);
            }
        }
        // 2. filter 值（WHERE）
        if (filter != null) {
            FilterToSqlTranslator.TranslatedFilter tf = ctx.filterTranslator().translate(filter);
            params.addAll(tf.getParams());
        }
        // 3. having 值（HAVING）
        // plan 2026-07-18-1500-2：collectBindParams 与 buildExternalAggregationSql 须对 expr leaf 处理一致
        // （preprocess 在 buildExternalAggregationSql 内已完成；此处 having 已被改写，复用同一 nameResolverFor 即可）
        if (having != null) {
            FilterToSqlTranslator.TranslatedFilter hf = ctx.filterTranslator().translate(having,
                    nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
            params.addAll(hf.getParams());
        }
        // limit/offset 由 executeJdbcQuery 在 filter/having 参数后追加，这里不重复
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
        // entity 路径单表上下文：标识符白名单取自该 entity 的 columnCode 集合（expression 校验用）
        Set<String> columnSet = new LinkedHashSet<>(propToCol.values());
        List<MeasureSpec> specs = new ArrayList<>();
        for (NopMetaTableMeasure m : all) {
            if (m.getExpression() != null && !m.getExpression().trim().isEmpty()) {
                // expression 型：dialect-independent 静态校验（单表 strict，列集合 = entity columnCode）
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

    private List<MeasureSpec> loadExternalMeasures(NopMetaTable table, List<String> names, MetaQueryContext ctx) {
        List<NopMetaTableMeasure> all = loadMeasures(table, names, ctx);
        // external/sql 路径单表上下文：标识符白名单取自该表可解析列名集合（expression 校验用）
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        Set<String> columnSet = resolveTableColumnNames(table, fieldDao, ctx);
        List<MeasureSpec> specs = new ArrayList<>();
        for (NopMetaTableMeasure m : all) {
            if (m.getExpression() != null && !m.getExpression().trim().isEmpty()) {
                // expression 型：dialect-independent 静态校验（单表 strict，列集合 = 该表可解析列名）
                ExpressionMeasureValidator.ValidatedExpression ve =
                        ExpressionMeasureValidator.validateStatic(m.getExpression(),
                                ExpressionMeasureValidator.ValidationOptions.singleTableStrict(columnSet),
                                table.getMetaTableId(), m.getMeasureName());
                specs.add(new MeasureSpec(safeAlias(m.getMeasureName()),
                        aggSqlOf(m.getAggFunc(), ve.sqlFragment, m.getMeasureName()),
                        ve.params, ve));
                continue;
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

    // ============================ external↔external JOIN 聚合 helpers（plan 1200-1）============================

    /** 解析 external/sql 表端点的列名集合（复用 MetaTableFieldResolver.resolve，external→buildSql columnName；sql→SELECT 解析列）。 */
    private Set<String> resolveTableColumnNames(NopMetaTable table, IEntityDao<NopMetaEntityField> fieldDao,
                                                 MetaQueryContext ctx) {
        List<ResolvedTableField> fields = ctx.fieldResolver().resolve(table, fieldDao);
        Set<String> names = new LinkedHashSet<>(fields.size());
        for (ResolvedTableField f : fields) {
            names.add(f.getName());
        }
        return names;
    }

    /** table 端点 join 字段解析：field 须属于该表可解析列集合（防御性 query-time 校验）。 */
    private String resolveExternalFieldOrThrow(Set<String> columns, String field, NopMetaTable table,
                                                String side, String joinId) {
        if (field == null || field.isEmpty() || !containsIgnoreCase(columns, field)) {
            throw new NopException(ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "join-field")
                    .param("side", side)
                    .param("endpointTableType", String.valueOf(table.getTableType()))
                    .param("column", String.valueOf(field))
                    .param("joinId", joinId);
        }
        return field;
    }

    private static boolean containsIgnoreCase(Set<String> cols, String name) {
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

    /**
     * external/sql 端点 JOIN FROM 子句（带显式别名）：external→{@code <tableName> <alias>}；
     * sql→{@code (<sourceSql>) <alias>}。JOIN 场景必须显式别名（l/r），用于 table-qualified 列引用。
     */
    private String externalTableFromForJoin(NopMetaTable table, String alias) {
        FilterToSqlTranslator.validateIdentifier(alias);
        if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(table.getTableType())) {
            String sourceSql = table.getSourceSql();
            if (sourceSql == null || sourceSql.trim().isEmpty()) {
                throw new NopException(ERR_AGGR_EXEC_FAILED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("error", "sql table sourceSql is empty");
            }
            return "(" + sourceSql + ") " + alias;
        }
        String tableName = table.getTableName();
        FilterToSqlTranslator.validateIdentifier(tableName);
        return tableName + " " + alias;
    }

    /** querySpace→数据源解析（external/sql 端点共用，两端点共享），失败时附加 joinId 上下文。 */
    private NopMetaDataSource resolveSharedDataSourceOrThrow(NopMetaTable table, MetaQueryContext ctx, String joinId) {
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

    // ============================ having/orderBy name 解析辅助（plan 2026-07-18-0900-2）============================

    /**
     * 单表路径 name→SQL 表达式反查表（measure name → aggSql；dimension name → column）。
     *
     * <p>spec 列表与 names 列表按顺序对齐（{@code loadMeasures}/{@code loadDimensions} 保持 names 顺序输出）。
     * 这里 measures 列表与 measureNames 列表一一对应；dims 列表与 dimensionNames 列表一一对应。
     */
    private static Map<String, String> buildNameToExprTable(List<MeasureSpec> measures, List<DimensionSpec> dims,
                                                              List<String> measureNames, List<String> dimensionNames,
                                                              NopMetaTable table) {
        Map<String, String> map = new LinkedHashMap<>();
        if (measures.size() != measureNames.size()) {
            throw new NopException(ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: measures/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < measures.size(); i++) {
            map.put(measureNames.get(i), measures.get(i).aggSql);
        }
        if (dims.size() != dimensionNames.size()) {
            throw new NopException(ERR_AGGR_HAVING_UNKNOWN_NAME)
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

    /**
     * JOIN 路径 name→SQL 表达式反查表（measure name → aggSql；dimension name → qualifiedCol）。
     * JOIN 路径 having/orderBy name 解析为已含 l./r. 前缀的 aggSql/qualifiedCol（与 SELECT/GROUP BY 一致）。
     */
    private static Map<String, String> buildJoinNameToExprTable(List<JoinMeasureSpec> measures,
                                                                  List<JoinDimensionSpec> dims,
                                                                  List<String> measureNames,
                                                                  List<String> dimensionNames, NopMetaTable table) {
        Map<String, String> map = new LinkedHashMap<>();
        if (measures.size() != measureNames.size()) {
            throw new NopException(ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: join measures/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < measures.size(); i++) {
            map.put(measureNames.get(i), measures.get(i).aggSql);
        }
        if (dims.size() != dimensionNames.size()) {
            throw new NopException(ERR_AGGR_HAVING_UNKNOWN_NAME)
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

    /**
     * having 的 fieldResolver 回调：name → SQL 表达式（aggSql 或 column）。
     * 命中失败（未选定 measure/dimension name）→ 抛 {@link #ERR_AGGR_HAVING_UNKNOWN_NAME}（不静默跳过）。
     *
     * <p>plan 2026-07-18-1400-1：expression 型 measure 的 aggSql 含 {@code ?}（字面量参数占位符），
     * 若经此回调注入 HAVING/ORDER BY 会致 SQL 参数计数错配 → 显式失败
     * {@link #ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED}（含 aggSql 检测到 {@code ?} 即拒绝）。
     *
     * <p>plan 2026-07-18-1500-2：多列算术 having（TreeBean 叶子新增可选 {@code expr} 属性）preprocess 后，
     * 叶子的 {@code name} 已被改写为最终 SQL 片段（如 {@code SUM(AMOUNT)-SUM(QTY)}，含算子/括号等非标识符字符）。
     * 本回调检测到非标识符 name 时**原样返回**（passthrough，{@code Function.identity()} 语义）——多列算术 leaf
     * 已在 preprocess 阶段完成 name→aggSql 替换 + {@code ?} 安全边界 + 安全校验，无需再反查 nameToExpr；
     * 既有单 name leaf（{@code name} 为合法标识符）仍走 nameToExpr 反查。大小写敏感匹配 nameToExpr
     * （key 为 measureNames/dimensionNames 原值），不使用 CASE_INSENSITIVE（避免 measure name {@code count}
     * 腐蚀 SQL 函数 {@code COUNT}）。
     */
    private static java.util.function.Function<String, String> nameResolverFor(Map<String, String> nameToExpr,
                                                                                  NopMetaTable table,
                                                                                  List<String> measureNames,
                                                                                  List<String> dimensionNames,
                                                                                  String clause) {
        return name -> {
            // plan 2026-07-18-1500-2：多列算术 having preprocess 后的 leaf——name 已是最终 SQL 片段（非合法标识符），
            // 原样返回（passthrough）。检测依据：标识符白名单 ^[A-Za-z_][A-Za-z0-9_]*$ 不匹配即视为已预处理。
            if (!FilterToSqlTranslator.IDENTIFIER_PATTERN.matcher(name).matches()) {
                return name;
            }
            String expr = nameToExpr.get(name);
            if (expr == null) {
                throw new NopException(ERR_AGGR_HAVING_UNKNOWN_NAME)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", name)
                        .param("selectedMeasures", String.valueOf(measureNames))
                        .param("selectedDimensions", String.valueOf(dimensionNames));
            }
            if (expr.indexOf('?') >= 0) {
                // expression 型 measure 被 HAVING/ORDER BY 的 name 引用——aggSql 含 ?，重注入会致参数计数错配
                throw new NopException(ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("measureName", name)
                        .param("clause", clause);
            }
            return expr;
        };
    }

    // ============================ 多列算术 having 预处理（plan 2026-07-18-1500-2）============================

    /**
     * 多列算术 having 的 user-input 表达式 token 识别正则：{@code [A-Za-z_][A-Za-z0-9_]*}（word-boundary find）。
     * 与 {@link FilterToSqlTranslator#IDENTIFIER_PATTERN} 同字符集（无 {@code ^}/{@code $} 锚定，
     * 因经 {@code Matcher.find()} 在表达式中逐 token 匹配而非整串 matches）。measure name 字符集约束。
     */
    private static final Pattern HAVING_EXPR_NAME_TOKEN =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * 多列算术 having 预处理入口：递归遍历 having TreeBean，对每个含 {@code expr} 属性的叶子做：
     * <ol>
     *   <li>tokenize：{@code [A-Za-z_][A-Za-z0-9_]*} word-boundary 匹配，识别 measure name token；</li>
     *   <li>name→aggSql 替换：token 命中 {@code nameToExpr} key 则替换为 aggSql；
     *       未命中（非用户选定 measure/dimension name）→ 抛 {@link #ERR_AGGR_HAVING_UNKNOWN_NAME}（不静默保留裸字符串）；</li>
     *   <li>{@code ?} 安全边界：引用 measure 的 aggSql 含 {@code ?}（expression 型 measure）→
     *       抛 {@link #ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED}（沿用 D12.4 既有边界）；</li>
     *   <li>替换后 SQL 经 {@link ExpressionMeasureValidator#validateStatic}（{@code saveTimeLoose}：
     *       关键字黑名单 + 标识符白名单 + parse 结构 + 函数黑名单）defense-in-depth 校验；
     *       parse 失败 → {@link #ERR_AGGR_HAVING_EXPR_UNPARSEABLE}；
     *       含危险关键字/函数/字面量 → {@link #ERR_AGGR_HAVING_EXPR_UNSAFE}；</li>
     *   <li>改写叶子的 {@code name} 属性为最终 SQL 片段（如 {@code SUM(AMOUNT)-SUM(QTY)}）。
     *       后续 {@code translate(having, fieldResolver)} 经 {@link #nameResolverFor} 的 passthrough 分支原样返回。</li>
     * </ol>
     *
     * <p>**参数绑定裁定（R1 M3 + R2 NEW-3）**：Phase 1 禁止字面量出现在 user-input expr（仅允许 measure name 算术组合）；
     * 经 {@code ?} 安全边界拒绝 expression 型 measure 后，引用的均为 field-based measure（aggSql 不含 {@code ?}）；
     * 故 leaf 的 {@code ?} 仅来自 comparison literal（{@code > ?}），参数计数与 {@code translate} 单次遍历产出一致。
     *
     * <p>**承载机制（R1 B3 修复）**：经 TreeBean 既有 {@code setAttr/getAttr} 扩展属性承载，**不修改 TreeBean 类**。
     *
     * <p>大小写敏感性（R2 B2 残留）：nameToExpr key 为 {@code measureNames}/{@code dimensionNames} 原值（case-sensitive
     * {@code LinkedHashMap}），用户 expr 中的 measure name token 须大小写一致匹配。
     *
     * @param having        having TreeBean（null 安全——null 直接返回）
     * @param nameToExpr    name→aggSql/column 反查表（由 {@link #buildNameToExprTable} / {@link #buildJoinNameToExprTable} 构造）
     * @param table         错误上下文
     * @param measureNames  错误上下文（用户选定 measure name 列表）
     * @param dimensionNames 错误上下文（用户选定 dimension name 列表）
     */
    static void preprocessHavingArithmetic(TreeBean having, Map<String, String> nameToExpr,
                                                NopMetaTable table, List<String> measureNames,
                                                List<String> dimensionNames) {
        if (having == null) {
            return;
        }
        Object exprAttr = having.getAttr(HAVING_EXPR_ATTR);
        if (exprAttr != null && !exprAttr.toString().isEmpty()) {
            // leaf with `expr` attr → 预处理
            String userExpr = exprAttr.toString();
            String finalSql = substituteAndValidateHavingExpr(userExpr, nameToExpr, table,
                    measureNames, dimensionNames);
            // 改写 name 属性为最终 SQL 片段；expr 属性保留（不参与后续 translate，仅作上下文）
            having.setAttr(FilterBeanConstants.FILTER_ATTR_NAME, finalSql);
            return;
        }
        // 递归子节点（and/or/not）
        List<TreeBean> children = having.getChildren();
        if (children == null || children.isEmpty()) {
            return;
        }
        for (TreeBean child : children) {
            preprocessHavingArithmetic(child, nameToExpr, table, measureNames, dimensionNames);
        }
    }

    /** TreeBean 叶子的算术表达式扩展属性名（plan 2026-07-18-1500-2：经 setAttr/getAttr 承载，不修改 TreeBean 类）。 */
    public static final String HAVING_EXPR_ATTR = "expr";

    /**
     * 对多列算术 having 的 user-input 表达式做：name→aggSql 替换 + {@code ?} 安全边界 + 安全校验，返回最终 SQL 片段。
     *
     * <p>分词机制（word-boundary-aware，case-sensitive）：经 {@link #HAVING_EXPR_NAME_TOKEN} 正则 find 所有
     * measure-name-shaped token；对每个 token：
     * <ul>
     *   <li>命中 nameToExpr key → 检查 aggSql 是否含 {@code ?}（含则抛
     *       {@link #ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED}），再替换；</li>
     *   <li>未命中 → 抛 {@link #ERR_AGGR_HAVING_UNKNOWN_NAME}（不静默保留裸字符串）。</li>
     * </ul>
     * 非匹配字符（算子 {@code +}/{@code -}/{@code *}/{@code /}/{@code %}、括号、空白）原样保留。
     *
     * <p>替换后的最终 SQL 片段经 {@link ExpressionMeasureValidator#validateStatic} 校验（{@code saveTimeLoose}：
     * 不校验列存在性——列存在性已由 measure 加载阶段保证；但校验关键字黑名单 + 标识符白名单 + parse 结构 + 函数黑名单）。
     * 检测到字面量（{@code params} 非空）→ 抛 {@link #ERR_AGGR_HAVING_EXPR_UNSAFE}（Phase 1 禁止字面量，
     * 仅允许 measure name 算术组合；理由：避免 inner-SQL {@code ?} 致参数计数与 translate 单次遍历产出不一致）。
     */
    static String substituteAndValidateHavingExpr(String userExpr, Map<String, String> nameToExpr,
                                                    NopMetaTable table, List<String> measureNames,
                                                    List<String> dimensionNames) {
        Matcher m = HAVING_EXPR_NAME_TOKEN.matcher(userExpr);
        StringBuilder out = new StringBuilder(userExpr.length() + 32);
        int last = 0;
        while (m.find()) {
            // 追加 token 之前的非匹配字符（算子/括号/空白）原样
            out.append(userExpr, last, m.start());
            String token = m.group();
            String aggSql = nameToExpr.get(token);
            if (aggSql == null) {
                // 未匹配 measure/dimension name → 显式失败（不静默保留裸字符串）
                throw new NopException(ERR_AGGR_HAVING_UNKNOWN_NAME)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", token)
                        .param("selectedMeasures", String.valueOf(measureNames))
                        .param("selectedDimensions", String.valueOf(dimensionNames));
            }
            if (aggSql.indexOf('?') >= 0) {
                // expression 型 measure 被 arithmetic 引用——aggSql 含 ?，重注入会致参数计数错配（沿用 D12.4）
                throw new NopException(ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("measureName", token)
                        .param("clause", "HAVING");
            }
            // 用空格包裹 aggSql，避免相邻算子粘连（如 name-name 替换为 SUM(A)-SUM(B) 后无空格仍合法，
            // 但 SUM(A)-SUM(B) 紧跟下一 token 时可能粘连）。aggSql 自身已含必要边界，空格仅做防御性分隔。
            out.append(aggSql);
            last = m.end();
        }
        out.append(userExpr, last, userExpr.length());
        String finalSql = out.toString();

        // defense-in-depth：post-substitution SQL 经 ExpressionMeasureValidator 校验
        // （关键字/函数黑名单 + 标识符白名单 + parse 结构；不校验列存在性——已由 measure 加载保证）
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
            // unsafe / dialect-unsupported 等 → 映射为 having-expr-unsafe（Phase 1 收口到单一 unsafe code）
            throw new NopException(ERR_AGGR_HAVING_EXPR_UNSAFE, e)
                    .param("metaTableId", table.getMetaTableId())
                    .param("expr", userExpr)
                    .param("reason", String.valueOf(e.getParam("reason")));
        }
        // Phase 1 禁止字面量：validator 将字面量参数化为 ?，params 非空则禁止（避免参数计数错配）
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

    /**
     * 检测 having TreeBean 是否包含任何 {@code expr} 属性的叶子（多列算术 having 标记）。
     * 用于跨库内存路径入口的显式失败判定（{@code MemoryFilterEvaluator} 调用前）。
     */
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

    /**
     * 构造 ORDER BY 子句（不含 "ORDER BY" 关键字；空返回 ""）。
     * 每个 {@link OrderFieldBean#getName()} 经 nameToExpr 解析为 aggSql/groupExpr，未知 name →
     * {@link #ERR_AGGR_ORDER_BY_UNKNOWN_NAME} 显式失败（不静默跳过）。
     *
     * <p>{@code desc=true} → DESC，{@code desc=false} → ASC；{@code nullsFirst} 非空 → NULLS FIRST/LAST。
     *
     * <p>plan 2026-07-18-1400-1：expression 型 measure 的 aggSql 含 {@code ?} 不能注入 ORDER BY →
     * 抛 {@link #ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED}（与 HAVING 一致）。
     */
    private static String buildOrderByClause(List<OrderFieldBean> orderBy, Map<String, String> nameToExpr,
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
                throw new NopException(ERR_AGGR_ORDER_BY_UNKNOWN_NAME)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", String.valueOf(name))
                        .param("selectedMeasures", String.valueOf(measureNames))
                        .param("selectedDimensions", String.valueOf(dimensionNames));
            }
            if (expr.indexOf('?') >= 0) {
                throw new NopException(ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED)
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

    /** 指标规格：别名 + 聚合 SQL 表达式 + expression 信息（plan 2026-07-18-1400-1：expression 型 Measure 承载）。 */
    private static final class MeasureSpec {
        final String alias;
        final String aggSql;
        /** expression 型 Measure 的字面量参数列表（按 SQL ? 出现顺序）；非 expression 型为 null。 */
        final List<Object> expressionParams;
        /** expression 型 Measure 的 validator 产出物（供 dialect-specific 检查）；非 expression 型为 null。 */
        final ExpressionMeasureValidator.ValidatedExpression validatedExpression;

        MeasureSpec(String alias, String aggSql) {
            this(alias, aggSql, null, null);
        }

        MeasureSpec(String alias, String aggSql, List<Object> expressionParams,
                    ExpressionMeasureValidator.ValidatedExpression validatedExpression) {
            this.alias = alias;
            this.aggSql = aggSql;
            this.expressionParams = expressionParams;
            this.validatedExpression = validatedExpression;
        }

        /** 是否为 expression 型（ aggSql 含 {@code <agg>(<expression>)}，需 dialect-specific 校验 + 参数绑定）。 */
        boolean isExpression() {
            return validatedExpression != null;
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
