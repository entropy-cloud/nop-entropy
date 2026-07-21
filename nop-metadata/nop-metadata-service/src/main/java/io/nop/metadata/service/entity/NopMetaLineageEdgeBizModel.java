/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaLineageEdgeBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.core.dto.LineageExtractResultDTO;
import io.nop.metadata.core.dto.LineageRecordResultDTO;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import io.nop.metadata.service.field.MetaTableFieldResolver;
import io.nop.metadata.service.lineage.ColumnLineageCandidate;
import io.nop.metadata.service.lineage.SqlColumnLineageExtractor;
import io.nop.metadata.service.lineage.SqlSourceTableExtractor;
import io.nop.metadata.service.lineage.SqlTableReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@BizModel("NopMetaLineageEdge")
public class NopMetaLineageEdgeBizModel extends CrudBizModel<NopMetaLineageEdge> implements INopMetaLineageEdgeBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaLineageEdgeBizModel.class);


    /**
     * AR-09：lineage 图遍历内存膨胀 DoS 防护。buildLineageGraph/buildTableNameIndex 原用 {@code dao().findAll()}
     * 全量加载，边集/表集达到规模上限时直接 OOM。改为 {@code findAllByQuery} 带 limit（maxEdges+1/maxTables+1），
     * 返回 list size > max 时抛 ErrorCode 显式失败（不静默截断、不静默全部丢弃）。
     */

    /** AR-09：lineage 图遍历边数上限（默认 100_000，可经 nop.metadata.lineage.max-edges 配置）。 */
    public static final int DEFAULT_LINEAGE_MAX_EDGES = 100_000;
    /** AR-09：lineage 表名索引上限（默认 100_000）。 */
    public static final int DEFAULT_LINEAGE_MAX_TABLES = 100_000;

    /**
     * AR-09：可配置的边数上限。{@code @cfg} 默认空时使用 {@link #DEFAULT_LINEAGE_MAX_EDGES}。
     * 测试时可经 system property / {@code setTestConfig} 覆盖。
     */
    @InjectValue(value = "@cfg:nop.metadata.lineage.max-edges|0")
    protected int configuredMaxEdges = 0;

    /** AR-09：可配置的表名索引上限。 */
    @InjectValue(value = "@cfg:nop.metadata.lineage.max-tables|0")
    protected int configuredMaxTables = 0;

    /** AR-09：解析生效的边数上限（配置 > 0 时覆盖默认）。 */
    protected int resolveMaxEdges() {
        return configuredMaxEdges > 0 ? configuredMaxEdges : DEFAULT_LINEAGE_MAX_EDGES;
    }

    /** AR-09：解析生效的表数上限（配置 > 0 时覆盖默认）。 */
    protected int resolveMaxTables() {
        return configuredMaxTables > 0 ? configuredMaxTables : DEFAULT_LINEAGE_MAX_TABLES;
    }

    /** SQL 源表抽取器（架构基线 §2.6.1，复用 nop-orm-eql AST）。无状态。 */
    private final SqlSourceTableExtractor sqlExtractor = new SqlSourceTableExtractor();

    /** 列级 SQL 血缘抽取器（架构基线 §2.6.1 列级 sql_parse，P2-5+）。无状态。 */
    private final SqlColumnLineageExtractor columnExtractor = new SqlColumnLineageExtractor();

    /**
     * 跨表类型字段解析器（架构基线 §2.5.2 D2 + §2.6.1 D5 expression measure 列级血缘归属解析）。无状态。
     * 用于 extractMeasureLineage action 解析目标表 T 的可用字段集合（per-table 一次，resolver 失败属表级前置失败）。
     */
    private final MetaTableFieldResolver fieldResolver = new MetaTableFieldResolver();

    public NopMetaLineageEdgeBizModel() {
        setEntityName(NopMetaLineageEdge.class.getName());
    }

    // ============================================================
    // Phase 1: 血缘填充机制
    // ============================================================

    /**
     * 手工录入一条或多条血缘边（架构基线 §2.6.1）。支持表级（sourceColumn/targetColumn 留空）和列级。
     * {@code lineageSource} 缺省为 {@code manual}，调用方可指定 {@code open_lineage}/{@code hook}。
     *
     * <p>校验：sourceTableId/targetTableId 必须存在于目录 NopMetaTable——不存在抛
     * {@code metadata.lineage-table-not-found}（不静默建悬空边）。整批先校验后保存（原子性）。
     *
     * @param edges 边列表，每个 Map 可含：sourceTableId(必填)/targetTableId(必填)/sourceColumn?/
     *              targetColumn?/transformType?/transformExpr?/pipelineId?/confidence?/lineageSource?
     * @return {@code {recordedEdgeCount: int}}
     */
    @BizMutation
    public LineageRecordResultDTO recordLineage(@Name("edges") List<Map<String, Object>> edges,
                                                  IServiceContext context) {
        if (edges == null || edges.isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_LINEAGE_NO_EDGES).param("size", 0);
        }

        // 1. 解析 + 收集引用的表 ID
        List<NopMetaLineageEdge> parsed = new ArrayList<>(edges.size());
        Set<String> referencedTableIds = new LinkedHashSet<>();
        for (int i = 0; i < edges.size(); i++) {
            Map<String, Object> m = edges.get(i);
            String sourceTableId = readString(m, "sourceTableId");
            String targetTableId = readString(m, "targetTableId");
            if (sourceTableId == null || sourceTableId.isEmpty() || targetTableId == null || targetTableId.isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_LINEAGE_TABLE_ID_MISSING).param("index", i).param("edge", m);
            }
            referencedTableIds.add(sourceTableId);
            referencedTableIds.add(targetTableId);

            NopMetaLineageEdge edge = dao().newEntity();
            edge.setSourceTableId(sourceTableId);
            edge.setTargetTableId(targetTableId);
            edge.setSourceColumn(readString(m, "sourceColumn"));
            edge.setTargetColumn(readString(m, "targetColumn"));
            edge.setTransformType(readString(m, "transformType"));
            edge.setTransformExpr(readString(m, "transformExpr"));
            edge.setPipelineId(readString(m, "pipelineId"));
            Double confidence = readDouble(m, "confidence");
            if (confidence != null) {
                edge.setConfidence(confidence);
            }
            String lineageSource = readString(m, "lineageSource");
            edge.setLineageSource(lineageSource != null ? lineageSource
                    : _NopMetadataCoreConstants.LINEAGE_SOURCE_MANUAL);
            parsed.add(edge);
        }

        // 2. 批量存在性校验（不静默建悬空边）
        Set<String> existingIds = loadExistingTableIds(referencedTableIds);
        for (String id : referencedTableIds) {
            if (!existingIds.contains(id)) {
                throw new NopException(NopMetadataErrors.ERR_LINEAGE_TABLE_NOT_FOUND).param("tableId", id);
            }
        }

        // 3. 保存全部（原子：校验通过才保存）
        for (NopMetaLineageEdge edge : parsed) {
            dao().saveEntity(edge);
        }
        orm().flushSession();

        LineageRecordResultDTO result = new LineageRecordResultDTO();
        result.setEdgeCount(parsed.size());
        return result;
    }

    /**
     * 从 tableType=sql 的视图表的 sourceSql 抽取表级血缘（架构基线 §2.6.1 sql_parse）。
     *
     * <p>行为：加载 NopMetaTable → 非 sql 类型抛 {@code metadata.lineage-not-sql-table}（不静默）→
     * SQL 抽取器解析 FROM/JOIN 表引用 → 匹配目录 NopMetaTable.tableName（大小写不敏感）→
     * 按 {@code (sourceTableId, targetTableId, lineageSource='sql_parse')} 幂等 upsert 表级边
     * （target=该 sql 表自身，sourceColumn/targetColumn 空）→ 未匹配表名进 unresolved（不丢、不静默）→
     * SQL 解析失败进 errors。
     *
     * @return {@code {extractedEdgeCount: int, unresolved: [...], errors: [...]}}
     */
    @BizMutation
    public LineageExtractResultDTO extractLineageFromSql(@Name("metaTableId") String metaTableId,
                                                          IServiceContext context) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        NopMetaTable targetTable = tableDao.getEntityById(metaTableId);
        if (targetTable == null) {
            throw new NopException(NopMetadataErrors.ERR_LINEAGE_SQL_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        if (!_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(targetTable.getTableType())) {
            throw new NopException(NopMetadataErrors.ERR_LINEAGE_NOT_SQL_VIEW_TABLE)
                    .param("metaTableId", metaTableId)
                    .param("tableType", targetTable.getTableType());
        }

        String sourceSql = targetTable.getSourceSql();
        List<Map<String, Object>> errors = new ArrayList<>();

        // 抽取表引用（解析失败进 errors，不静默返回空）
        List<SqlTableReference> refs;
        try {
            refs = sqlExtractor.extract(sourceSql);
        } catch (NopException e) {
            LOG.error("extractLineageFromSql sql_parse failed for metaTableId={}", metaTableId, e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("stage", "sql_parse");
            err.put("error", toErrorMessage(e));
            errors.add(err);
            refs = Collections.emptyList();
        }

        // 构建目录表名索引（tableName → metaTableId），大小写不敏感匹配
        Map<String, String> nameToId = buildTableNameIndex();

        String targetId = targetTable.getMetaTableId();
        List<String> unresolved = new ArrayList<>();
        int extracted = 0;
        for (SqlTableReference ref : refs) {
            String sourceId = nameToId.get(ref.getSimpleName().toLowerCase());
            if (sourceId == null) {
                // dangling：不丢、不静默。schema 约束（sourceTableId mandatory）不允许建悬空边，故只收集
                unresolved.add(ref.getFullName());
                continue;
            }
            upsertSqlParseEdge(sourceId, targetId);
            extracted++;
        }

        LineageExtractResultDTO result = new LineageExtractResultDTO();
        result.setMetaTableId(metaTableId);
        result.setEdgeCount(extracted);
        result.setSourceTables(unresolved);
        result.setUnresolved(unresolved);
        result.setErrors(errors);
        return result;
    }

    /**
     * 从 tableType=sql 的视图表的 sourceSql 抽取**列级**血缘（架构基线 §2.6.1 列级 sql_parse，P2-5+）。
     *
     * <p>行为：加载 NopMetaTable → 非 sql 类型/不存在/空 sourceSql 显式失败 → 列级抽取器解析 SELECT projections
     * → 列引用归属解析（表别名→源表名映射，D1 仅用句法字段）→ 源表名匹配目录 NopMetaTable.tableName（大小写不敏感）
     * → 命中则按列级五元组幂等 upsert 列级边（sourceColumn/targetColumn/transformType 填充，lineageSource=sql_parse）
     * → 未匹配/不可归属进 unresolved（不建悬空边）→ SQL 解析失败进 errors。
     *
     * <p>与表级 {@link #extractLineageFromSql} 独立共存：表级 upsert 查询补 {@code sourceColumn IS NULL} 隔离，
     * 列级 upsert 用列级五元组，二者各管各的幂等（D2）。
     *
     * @return {@code {extractedEdgeCount: int, unresolved: [...], errors: [...]}}
     */
    @BizMutation
    public LineageExtractResultDTO extractColumnLineageFromSql(@Name("metaTableId") String metaTableId,
                                                                IServiceContext context) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        NopMetaTable targetTable = tableDao.getEntityById(metaTableId);
        if (targetTable == null) {
            throw new NopException(NopMetadataErrors.ERR_LINEAGE_SQL_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        if (!_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(targetTable.getTableType())) {
            throw new NopException(NopMetadataErrors.ERR_LINEAGE_NOT_SQL_VIEW_TABLE)
                    .param("metaTableId", metaTableId)
                    .param("tableType", targetTable.getTableType());
        }
        String sourceSql = targetTable.getSourceSql();
        if (sourceSql == null || sourceSql.trim().isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_LINEAGE_SQL_SOURCE_EMPTY).param("metaTableId", metaTableId);
        }

        List<Map<String, Object>> errors = new ArrayList<>();
        List<ColumnLineageCandidate> candidates;
        try {
            candidates = columnExtractor.extract(sourceSql);
        } catch (NopException e) {
            LOG.error("extractColumnLineageFromSql sql_parse failed for metaTableId={}", metaTableId, e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("stage", "sql_parse_column");
            err.put("error", toErrorMessage(e));
            errors.add(err);
            candidates = Collections.emptyList();
        }

        // 构建目录表名索引（tableName(lower) → metaTableId），大小写不敏感匹配
        Map<String, String> nameToId = buildTableNameIndex();

        String targetId = targetTable.getMetaTableId();
        List<String> unresolved = new ArrayList<>();
        int extracted = 0;
        for (ColumnLineageCandidate c : candidates) {
            if (c.isUnresolvable()) {
                // 不可归属（多表无限定符 / CTE 子查询别名 / 通配符）→ 显式进 unresolved（不静默丢弃、不伪造）
                unresolved.add(c.getTargetColumn() + " <- " + String.valueOf(c.getSourceColumn())
                        + " (" + c.getUnresolvedReason() + ")");
                continue;
            }
            String sourceId = nameToId.get(c.getSourceTableName().toLowerCase());
            if (sourceId == null) {
                // dangling：源列所属表未匹配目录 → 进 unresolved（不建悬空边，sourceTableId mandatory）
                unresolved.add(c.getTargetColumn() + " <- " + c.getSourceTableName() + "."
                        + c.getSourceColumn() + " (source-table-not-in-catalog)");
                continue;
            }
            upsertColumnSqlParseEdge(sourceId, targetId, c.getSourceColumn(), c.getTargetColumn(),
                    c.getTransformType());
            extracted++;
        }

        LineageExtractResultDTO result = new LineageExtractResultDTO();
        result.setMetaTableId(metaTableId);
        result.setEdgeCount(extracted);
        result.setUnresolved(unresolved);
        result.setErrors(errors);
        return result;
    }

    // ============================================================
    // Phase 2: 图遍历查询（架构基线 §2.6.2）
    // ============================================================

    /**
     * 向上追溯：反向 BFS（沿 targetTableId→sourceTableId）收集给定表的所有上游源表（架构基线 §2.6.2）。
     * 含 visited 环检测（血缘图可能含环，不死循环）。返回 metaTableId 列表（不含起点自身）。
     */
    @BizQuery
    public List<String> getUpstream(@Name("metaTableId") String metaTableId, IServiceContext context) {
        LineageGraph graph = buildLineageGraph();
        Set<String> visited = new HashSet<>();
        visited.add(metaTableId);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(metaTableId);
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            // 反向：从 target 找 source（cur 作为 target，其上游是 source）
            List<String> sources = graph.reverse.get(cur);
            if (sources != null) {
                for (String src : sources) {
                    if (visited.add(src)) {
                        result.add(src);
                        queue.add(src);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 向下追踪：正向 BFS（沿 sourceTableId→targetTableId）收集给定表的所有下游表（架构基线 §2.6.2）。
     * 含 visited 环检测（不死循环）。返回 metaTableId 列表（不含起点自身）。
     */
    @BizQuery
    public List<String> getDownstream(@Name("metaTableId") String metaTableId, IServiceContext context) {
        LineageGraph graph = buildLineageGraph();
        Set<String> visited = new HashSet<>();
        visited.add(metaTableId);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(metaTableId);
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            List<String> targets = graph.forward.get(cur);
            if (targets != null) {
                for (String tgt : targets) {
                    if (visited.add(tgt)) {
                        result.add(tgt);
                        queue.add(tgt);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 路径查找：返回 S→T 的单条最短路径（BFS 最短路径 + visited 环检测防死循环，架构基线 §2.6.2）。
     * 无路径返回显式空列表（不报错、不静默返回 null）。返回的路径含两端点 S 和 T。
     */
    @BizQuery
    public List<String> getLineagePath(@Name("sourceTableId") String sourceTableId,
                                        @Name("targetTableId") String targetTableId,
                                        IServiceContext context) {
        LineageGraph graph = buildLineageGraph();
        if (sourceTableId.equals(targetTableId)) {
            return Collections.singletonList(sourceTableId);
        }
        // BFS 最短路径：记录前驱节点
        Map<String, String> prev = new HashMap<>();
        Set<String> visited = new HashSet<>();
        visited.add(sourceTableId);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(sourceTableId);
        boolean found = false;
        while (!queue.isEmpty() && !found) {
            String cur = queue.poll();
            List<String> targets = graph.forward.get(cur);
            if (targets == null) {
                continue;
            }
            for (String tgt : targets) {
                if (visited.add(tgt)) {
                    prev.put(tgt, cur);
                    if (tgt.equals(targetTableId)) {
                        found = true;
                        break;
                    }
                    queue.add(tgt);
                }
            }
        }
        if (!found) {
            // 显式空（不报错）
            return Collections.emptyList();
        }
        // 重建路径
        List<String> path = new ArrayList<>();
        String node = targetTableId;
        while (node != null) {
            path.add(node);
            node = prev.get(node);
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * 影响分析：变更影响 = 该表下游（架构基线 §2.6.2）。若提供 columnName 且存在该列的列级边，
     * 则按列过滤（仅沿该列的列级边可达的下游）；否则回退表级（该表所有下游表，不静默返回空）。
     *
     * <p>回退逻辑：columnName 提供但该列无任何列级边时，回退为全表级下游（保证不静默返回空）。
     */
    @BizQuery
    public List<String> getImpactAnalysis(@Name("metaTableId") String metaTableId,
                                           @Optional @Name("columnName") String columnName,
                                           IServiceContext context) {
        LineageGraph graph = buildLineageGraph();
        List<String> tableLevel = bfsForward(graph.forward, metaTableId);

        if (columnName == null || columnName.isEmpty()) {
            return tableLevel;
        }
        // 按列过滤：仅沿 sourceColumn==columnName 或 targetColumn==columnName 的列级边做正向 BFS
        List<String> columnFiltered = bfsForwardByColumn(graph.columnForward, metaTableId, columnName);
        if (columnFiltered.isEmpty()) {
            // 回退表级（该列无列级边时，变更影响为全表级下游，不静默返回空）
            return tableLevel;
        }
        return columnFiltered;
    }

    // ============================================================
    // Phase 3: expression 型 Measure 列级血缘（架构基线 §2.6.1 D1/D3/D4/D5/D6）
    // ============================================================

    /**
     * 从 NopMetaTable 的 expression 型 Measure 抽取列级血缘（架构基线 §2.6.1 expression 型 Measure D1-D6）。
     *
     * <p>行为契约（依 §Scope / design doc §2.6.1）：
     * <ol>
     *   <li>加载 NopMetaTable T（不存在抛 {@code NopMetadataErrors.ERR_LINEAGE_TABLE_NOT_FOUND}）。</li>
     *   <li>经 {@link MetaTableFieldResolver#resolveFieldNames} 解析 T 自身可用字段集合——**表级前置失败**：
     *       baseEntityId null / buildSql 损坏 / sourceSql 不可解析时抛 ErrorCode，**直接中断 action**，
     *       不进 per-measure 隔离（D5 失败处理分层 (a)）。</li>
     *   <li>D6 replace：按 {@code (sourceTableId=T AND targetTableId=T AND lineageSource=measure_parse)}
     *       删旧 measure_parse 边，再从当前 measure 集合全量重插。</li>
     *   <li>加载 T 的所有 NopMetaTableMeasure（filter {@code metaTableId=T}）。</li>
     *   <li>对每个 {@code expression != null} 的 measure，**per-measure try/catch**（D5 (b)）：
     *     <ul>
     *       <li>调 {@link ExpressionMeasureValidator#validateStatic} 取 {@code ValidatedExpression.identifiers}
     *           （传 {@link ExpressionMeasureValidator.ValidationOptions#saveTimeLoose()} 跳过列存在性 fail-fast）。</li>
     *       <li>identifier 与字段集合 **case-insensitive** 比对（对齐 validator.containsIgnoreCase + sql_parse toLowerCase 先例）：
     *         <ul>
     *           <li>裸名 ∈ 字段集合 → 产 flat-collect 自环边（D1+D3），{@code transformType = aggFunc != null ? aggregated : derived}（D4）。</li>
     *           <li>{@code l.}/{@code r.} 限定名 → 进 unresolved（标 {@code join-context-deferred}）。</li>
     *           <li>裸名 ∉ 字段集合 → 进 unresolved（不伪造映射、不静默丢弃）。</li>
     *         </ul>
     *       </li>
     *       <li>validator 抛异常（unparseable/unsafe）→ 进 errors（标 measureName + 错误信息），该 measure 不产边，
     *           **不中断整批**。</li>
     *     </ul>
     *   </li>
     *   <li>返回 {@code {extractedEdgeCount: int, unresolved: List<String>, errors: List<Map<String,Object>>}}
     *       （对齐 {@link #extractColumnLineageFromSql} 返回结构）。</li>
     * </ol>
     *
     * <p>D1 BFS 语义隔离：产出的自环边 {@code sourceTableId == targetTableId == T}，在
     * {@link #getDownstream} / {@link #getImpactAnalysis} 永远不可达（BFS 设计，非 bug）；
     * 仅经 {@code NopMetaLineageEdge} 直接查询召回（D2）。
     *
     * @return {@code {extractedEdgeCount: int, unresolved: [...], errors: [...]}}
     */
    @BizMutation
    public LineageExtractResultDTO extractMeasureLineage(@Name("metaTableId") String metaTableId,
                                                          IServiceContext context) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        NopMetaTable targetTable = tableDao.getEntityById(metaTableId);
        if (targetTable == null) {
            throw new NopException(NopMetadataErrors.ERR_LINEAGE_TABLE_NOT_FOUND).param("tableId", metaTableId);
        }

        // 表级前置失败：resolver per-table 调用，baseEntityId null / buildSql 损坏 / sourceSql 不可解析
        // 时直接抛 ErrorCode 中断 action（D5 (a)，不进 per-measure 隔离）
        IEntityDao<NopMetaEntityField> fieldDao = daoFor(NopMetaEntityField.class);
        Set<String> fieldNames = fieldResolver.resolveFieldNames(targetTable, fieldDao);
        // case-insensitive 比对准备：lowercase 字段集合
        Set<String> fieldNamesLower = new HashSet<>(fieldNames.size());
        for (String n : fieldNames) {
            if (n != null) {
                fieldNamesLower.add(n.toLowerCase());
            }
        }

        String targetId = targetTable.getMetaTableId();

        // D6 replace：删除 T 既有 measure_parse 自环边（条件 source+target+lineageSource，防未来 D1 扩展歧义）
        deleteMeasureParseEdges(targetId);

        // 加载 T 的所有 measure
        IEntityDao<NopMetaTableMeasure> measureDao = daoFor(NopMetaTableMeasure.class);
        QueryBean mq = new QueryBean();
        mq.addFilter(FilterBeans.eq(NopMetaTableMeasure.PROP_NAME_metaTableId, metaTableId));
        List<NopMetaTableMeasure> measures = measureDao.findAllByQuery(mq);

        List<String> unresolved = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int extracted = 0;

        for (NopMetaTableMeasure measure : measures) {
            String expression = measure.getExpression();
            if (expression == null || expression.trim().isEmpty()) {
                // 非 expression 型 measure（entityFieldId 引用）——跳过（Non-Goal: field-based measure 聚合血缘）
                continue;
            }
            String measureName = measure.getMeasureName();
            try {
                ExpressionMeasureValidator.ValidatedExpression validated =
                        ExpressionMeasureValidator.validateStatic(expression,
                                ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(),
                                metaTableId, measureName);
                String transformType = measure.getAggFunc() != null && !measure.getAggFunc().isEmpty()
                        ? _NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED
                        : _NopMetadataCoreConstants.LINEAGE_TRANSFORM_DERIVED;
                // identifier 归属比对（case-insensitive）
                for (String ident : validated.identifiers) {
                    if (ident == null || ident.isEmpty()) {
                        continue;
                    }
                    if (ident.indexOf('.') >= 0) {
                        // JOIN 限定列（l./r.）——进 unresolved（标 join-context-deferred，JOIN 上下文跨表血缘为 Non-Goal）
                        unresolved.add(measureName + " <- " + ident + " (join-context-deferred)");
                        continue;
                    }
                    if (!fieldNamesLower.contains(ident.toLowerCase())) {
                        // 裸名不在字段集合——进 unresolved（不伪造映射、不静默丢弃）
                        unresolved.add(measureName + " <- " + ident + " (column-not-in-table-fields)");
                        continue;
                    }
                    // 产 flat-collect 自环边（D1+D3+D4）
                    upsertMeasureParseEdge(targetId, ident, measureName, transformType);
                    extracted++;
                }
            } catch (NopException e) {
                // D5 (b) per-measure 隔离：validator 失败进 errors，不中断整批
                LOG.warn("extractMeasureLineage validator failed for metaTableId={}, measureName={}",
                        metaTableId, measureName, e);
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("stage", "measure_parse");
                err.put("measureName", measureName);
                err.put("error", toErrorMessage(e));
                errors.add(err);
            }
        }

        orm().flushSession();

        LineageExtractResultDTO result = new LineageExtractResultDTO();
        result.setMetaTableId(metaTableId);
        result.setEdgeCount(extracted);
        result.setUnresolved(unresolved);
        result.setErrors(errors);
        return result;
    }

    // ============================================================
    // shared helpers（Phase 1 + Phase 2 + Phase 3）
    // ============================================================

    /** 正向 BFS（无列过滤），返回从起点可达的所有下游（不含起点自身）。含 visited 环检测。 */
    private List<String> bfsForward(Map<String, List<String>> forward, String start) {
        Set<String> visited = new HashSet<>();
        visited.add(start);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            List<String> targets = forward.get(cur);
            if (targets == null) {
                continue;
            }
            for (String tgt : targets) {
                if (visited.add(tgt)) {
                    result.add(tgt);
                    queue.add(tgt);
                }
            }
        }
        return result;
    }

    /**
     * 正向 BFS 仅沿列级边（sourceColumn 或 targetColumn == columnName）。
     * 返回从起点经该列列级边可达的所有下游（不含起点自身）。含 visited 环检测。
     */
    private List<String> bfsForwardByColumn(Map<String, List<NopMetaLineageEdge>> columnForward,
                                            String start, String columnName) {
        Set<String> visited = new HashSet<>();
        visited.add(start);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            List<NopMetaLineageEdge> edges = columnForward.get(cur);
            if (edges == null) {
                continue;
            }
            for (NopMetaLineageEdge edge : edges) {
                if (!matchesColumn(edge, columnName)) {
                    continue;
                }
                String tgt = edge.getTargetTableId();
                if (visited.add(tgt)) {
                    result.add(tgt);
                    queue.add(tgt);
                }
            }
        }
        return result;
    }

    private static boolean matchesColumn(NopMetaLineageEdge edge, String columnName) {
        return columnName != null && (columnName.equals(edge.getSourceColumn())
                || columnName.equals(edge.getTargetColumn()));
    }

    /**
     * 一次性加载全量 MetaLineageEdge 在内存建图（架构基线 §2.6.2 BFS 策略：元数据目录量级、边数小，
     * 避免逐跳查询）。forward: source→[targets]；reverse: target→[sources]；
     * columnForward: source→[列级边]（用于影响分析按列过滤）。
     *
     * <p>AR-09 DoS 防护：改用 {@code findAllByQuery(limit=maxEdges+1)}，list size > maxEdges 时显式失败，
     * 不再无上限全量加载（避免恶意/失控数据导入 OOM）。
     */
    private LineageGraph buildLineageGraph() {
        // AR-09：上限保护——拉 maxEdges+1 行；若实际返回行数 > maxEdges 抛 ErrorCode（不静默截断）
        int maxEdges = resolveMaxEdges();
        QueryBean q = new QueryBean();
        q.setLimit(maxEdges + 1);
        List<NopMetaLineageEdge> edges = dao().findAllByQuery(q);
        if (edges.size() > maxEdges) {
            throw new NopException(NopMetadataErrors.ERR_LINEAGE_GRAPH_TOO_LARGE)
                    .param("edges", edges.size())
                    .param("limit", maxEdges);
        }
        Map<String, List<String>> forward = new HashMap<>();
        Map<String, List<String>> reverse = new HashMap<>();
        Map<String, List<NopMetaLineageEdge>> columnForward = new HashMap<>();
        for (NopMetaLineageEdge edge : edges) {
            String src = edge.getSourceTableId();
            String tgt = edge.getTargetTableId();
            forward.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
            reverse.computeIfAbsent(tgt, k -> new ArrayList<>()).add(src);
            if (edge.getSourceColumn() != null || edge.getTargetColumn() != null) {
                columnForward.computeIfAbsent(src, k -> new ArrayList<>()).add(edge);
            }
        }
        return new LineageGraph(forward, reverse, columnForward);
    }

    /** 血缘图内存结构（正向/反向邻接表 + 列级正向邻接表）。 */
    private static final class LineageGraph {
        final Map<String, List<String>> forward;
        final Map<String, List<String>> reverse;
        final Map<String, List<NopMetaLineageEdge>> columnForward;

        LineageGraph(Map<String, List<String>> forward,
                     Map<String, List<String>> reverse,
                     Map<String, List<NopMetaLineageEdge>> columnForward) {
            this.forward = forward;
            this.reverse = reverse;
            this.columnForward = columnForward;
        }
    }

    // ============================================================
    // Phase 1 helpers
    // ============================================================

    /** 加载目录中实际存在的表 ID 集合（用于 recordLineage 校验）。 */
    private Set<String> loadExistingTableIds(Set<String> ids) {
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.in(NopMetaTable.PROP_NAME_metaTableId, ids));
        List<NopMetaTable> tables = tableDao.findAllByQuery(q);
        Set<String> existing = new HashSet<>();
        for (NopMetaTable t : tables) {
            existing.add(t.getMetaTableId());
        }
        return existing;
    }

    /**
     * 构建 tableName(lower) → metaTableId 索引（全量加载，元数据目录量级，见架构基线 §2.6.2 BFS 策略说明）。
     *
     * <p>AR-09 DoS 防护：与 {@link #buildLineageGraph} 一致加上限保护，避免表数失控导致 OOM。
     */
    private Map<String, String> buildTableNameIndex() {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        int maxTables = resolveMaxTables();
        QueryBean q = new QueryBean();
        q.setLimit(maxTables + 1);
        List<NopMetaTable> tables = tableDao.findAllByQuery(q);
        if (tables.size() > maxTables) {
            throw new NopException(NopMetadataErrors.ERR_LINEAGE_TABLE_INDEX_TOO_LARGE)
                    .param("tables", tables.size())
                    .param("limit", maxTables);
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (NopMetaTable t : tables) {
            if (t.getTableName() != null) {
                map.putIfAbsent(t.getTableName().toLowerCase(), t.getMetaTableId());
            }
        }
        return map;
    }

    /**
     * 幂等 upsert（表级，架构基线 §2.6.1）：按 (sourceTableId, targetTableId, lineageSource='sql_parse')
     * 且 sourceColumn IS NULL 去重。存在则更新（transformType），不存在则新建表级边。
     *
     * <p>表级/列级隔离（P2-5+ D2 裁定）：补 {@code sourceColumn IS NULL} 过滤条件，让表级查询只匹配表级边，
     * 列级边（sourceColumn 非空）不会被表级重抽误匹配/误更新。
     */
    private void upsertSqlParseEdge(String sourceTableId, String targetTableId) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceTableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetTableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE));
        // 表级/列级隔离：仅匹配表级边（sourceColumn 为 null），不误匹配列级边（D2）
        q.addFilter(FilterBeans.isNull(NopMetaLineageEdge.PROP_NAME_sourceColumn));
        NopMetaLineageEdge edge = dao().findFirstByQuery(q);
        if (edge == null) {
            edge = dao().newEntity();
            edge.setSourceTableId(sourceTableId);
            edge.setTargetTableId(targetTableId);
            edge.setLineageSource(_NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE);
            edge.setTransformType(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT);
            dao().saveEntity(edge);
        } else {
            edge.setTransformType(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT);
            dao().updateEntity(edge);
        }
    }

    /**
     * 幂等 upsert（列级，架构基线 §2.6.1 列级 sql_parse，P2-5+ D3）：按列级五元组
     * (sourceTableId, targetTableId, sourceColumn, targetColumn, lineageSource='sql_parse') 去重。
     * 存在则更新 transformType，不存在则新建列级边。幂等键不含 transformType（重抽更新 transformType）。
     */
    private void upsertColumnSqlParseEdge(String sourceTableId, String targetTableId,
                                           String sourceColumn, String targetColumn, String transformType) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceTableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetTableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceColumn, sourceColumn));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetColumn, targetColumn));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE));
        NopMetaLineageEdge edge = dao().findFirstByQuery(q);
        if (edge == null) {
            edge = dao().newEntity();
            edge.setSourceTableId(sourceTableId);
            edge.setTargetTableId(targetTableId);
            edge.setSourceColumn(sourceColumn);
            edge.setTargetColumn(targetColumn);
            edge.setLineageSource(_NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE);
            edge.setTransformType(transformType);
            dao().saveEntity(edge);
        } else {
            edge.setTransformType(transformType);
            dao().updateEntity(edge);
        }
    }

    /**
     * 幂等 upsert（expression 型 Measure 列级自环边，架构基线 §2.6.1 D1+D3+D4 measure_parse）：
     * 按五元组 (sourceTableId=T, targetTableId=T, sourceColumn, targetColumn=measureName,
     * lineageSource='measure_parse') 去重。D6 replace 语义下 action 已先删旧边，此处通常走新建路径；
     * upsert 形态保留防御性（防同次 action 内同列+同 measure 的 measure 表中重复行）。
     */
    private void upsertMeasureParseEdge(String tableId, String sourceColumn, String measureName,
                                         String transformType) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceColumn, sourceColumn));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetColumn, measureName));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE));
        NopMetaLineageEdge edge = dao().findFirstByQuery(q);
        if (edge == null) {
            edge = dao().newEntity();
            edge.setSourceTableId(tableId);
            edge.setTargetTableId(tableId);
            edge.setSourceColumn(sourceColumn);
            edge.setTargetColumn(measureName);
            edge.setLineageSource(_NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE);
            edge.setTransformType(transformType);
            dao().saveEntity(edge);
        } else {
            edge.setTransformType(transformType);
            dao().updateEntity(edge);
        }
    }

    /**
     * D6 replace 删除 helper（架构基线 §2.6.1 D6 measure_parse 重抽语义）：按
     * {@code (sourceTableId=T AND targetTableId=T AND lineageSource=measure_parse)} 删旧 measure_parse 边。
     *
     * <p>条件同时指定 source+target+lineageSource：D1 限定自环边（source==target==T），二者当前等价；
     * 同时指定可防止未来 D1 扩展（如 JOIN 上下文跨表 measure 边）时误删非自环 measure_parse 边；
     * lineageSource=measure_parse 确保只删本来源的边，不影响 sql_parse/manual/open_lineage/hook 等。
     */
    private void deleteMeasureParseEdges(String tableId) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE));
        List<NopMetaLineageEdge> stale = dao().findAllByQuery(q);
        for (NopMetaLineageEdge e : stale) {
            dao().deleteEntity(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static String readString(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Double readDouble(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        return Double.parseDouble(String.valueOf(v));
    }

    private static String toErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg != null)
            return msg;
        if (e instanceof NopException)
            return ((NopException) e).getErrorCode();
        return e.getClass().getName();
    }
}
