package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaLineageEdgeBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.lineage.ColumnLineageCandidate;
import io.nop.metadata.service.lineage.SqlColumnLineageExtractor;
import io.nop.metadata.service.lineage.SqlSourceTableExtractor;
import io.nop.metadata.service.lineage.TableReference;
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

    static final ErrorCode ERR_LINEAGE_NO_EDGES =
            ErrorCode.define("metadata.lineage-no-edges", "No lineage edges provided to record", "size");
    static final ErrorCode ERR_LINEAGE_TABLE_ID_MISSING =
            ErrorCode.define("metadata.lineage-table-id-missing",
                    "Lineage edge is missing required table id (sourceTableId or targetTableId)", "index", "edge");
    static final ErrorCode ERR_LINEAGE_TABLE_NOT_FOUND =
            ErrorCode.define("metadata.lineage-table-not-found",
                    "Referenced table does not exist in catalog: {tableId}", "tableId");
    static final ErrorCode ERR_LINEAGE_SQL_TABLE_NOT_FOUND =
            ErrorCode.define("metadata.lineage-sql-table-not-found",
                    "Lineage sql table not found: {metaTableId}", "metaTableId");
    static final ErrorCode ERR_LINEAGE_NOT_SQL_TABLE =
            ErrorCode.define("metadata.lineage-not-sql-table",
                    "Table is not a sql-view table, cannot extract lineage: {metaTableId} (tableType={tableType})",
                    "metaTableId", "tableType");
    static final ErrorCode ERR_LINEAGE_SQL_SOURCE_EMPTY =
            ErrorCode.define("metadata.lineage-sql-source-empty",
                    "Sql table sourceSql is empty, cannot extract column lineage: {metaTableId}", "metaTableId");

    /** SQL 源表抽取器（架构基线 §2.6.1，复用 nop-orm-eql AST）。无状态。 */
    private final SqlSourceTableExtractor sqlExtractor = new SqlSourceTableExtractor();

    /** 列级 SQL 血缘抽取器（架构基线 §2.6.1 列级 sql_parse，P2-5+）。无状态。 */
    private final SqlColumnLineageExtractor columnExtractor = new SqlColumnLineageExtractor();

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
    public Map<String, Object> recordLineage(@Name("edges") List<Map<String, Object>> edges,
                                              IServiceContext context) {
        if (edges == null || edges.isEmpty()) {
            throw new NopException(ERR_LINEAGE_NO_EDGES).param("size", 0);
        }

        // 1. 解析 + 收集引用的表 ID
        List<NopMetaLineageEdge> parsed = new ArrayList<>(edges.size());
        Set<String> referencedTableIds = new LinkedHashSet<>();
        for (int i = 0; i < edges.size(); i++) {
            Map<String, Object> m = edges.get(i);
            String sourceTableId = readString(m, "sourceTableId");
            String targetTableId = readString(m, "targetTableId");
            if (sourceTableId == null || sourceTableId.isEmpty() || targetTableId == null || targetTableId.isEmpty()) {
                throw new NopException(ERR_LINEAGE_TABLE_ID_MISSING).param("index", i).param("edge", m);
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
                throw new NopException(ERR_LINEAGE_TABLE_NOT_FOUND).param("tableId", id);
            }
        }

        // 3. 保存全部（原子：校验通过才保存）
        for (NopMetaLineageEdge edge : parsed) {
            dao().saveEntity(edge);
        }
        orm().flushSession();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordedEdgeCount", parsed.size());
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
    public Map<String, Object> extractLineageFromSql(@Name("metaTableId") String metaTableId,
                                                     IServiceContext context) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        NopMetaTable targetTable = tableDao.getEntityById(metaTableId);
        if (targetTable == null) {
            throw new NopException(ERR_LINEAGE_SQL_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        if (!_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(targetTable.getTableType())) {
            throw new NopException(ERR_LINEAGE_NOT_SQL_TABLE)
                    .param("metaTableId", metaTableId)
                    .param("tableType", targetTable.getTableType());
        }

        String sourceSql = targetTable.getSourceSql();
        List<Map<String, Object>> errors = new ArrayList<>();

        // 抽取表引用（解析失败进 errors，不静默返回空）
        List<TableReference> refs;
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
        for (TableReference ref : refs) {
            String sourceId = nameToId.get(ref.getSimpleName().toLowerCase());
            if (sourceId == null) {
                // dangling：不丢、不静默。schema 约束（sourceTableId mandatory）不允许建悬空边，故只收集
                unresolved.add(ref.getFullName());
                continue;
            }
            upsertSqlParseEdge(sourceId, targetId);
            extracted++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("extractedEdgeCount", extracted);
        result.put("unresolved", unresolved);
        result.put("errors", errors);
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
    public Map<String, Object> extractColumnLineageFromSql(@Name("metaTableId") String metaTableId,
                                                            IServiceContext context) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        NopMetaTable targetTable = tableDao.getEntityById(metaTableId);
        if (targetTable == null) {
            throw new NopException(ERR_LINEAGE_SQL_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        if (!_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(targetTable.getTableType())) {
            throw new NopException(ERR_LINEAGE_NOT_SQL_TABLE)
                    .param("metaTableId", metaTableId)
                    .param("tableType", targetTable.getTableType());
        }
        String sourceSql = targetTable.getSourceSql();
        if (sourceSql == null || sourceSql.trim().isEmpty()) {
            throw new NopException(ERR_LINEAGE_SQL_SOURCE_EMPTY).param("metaTableId", metaTableId);
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("extractedEdgeCount", extracted);
        result.put("unresolved", unresolved);
        result.put("errors", errors);
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
    public List<String> getUpstream(@Name("metaTableId") String metaTableId) {
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
    public List<String> getDownstream(@Name("metaTableId") String metaTableId) {
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
                                       @Name("targetTableId") String targetTableId) {
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
                                          @Optional @Name("columnName") String columnName) {
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
    // shared helpers（Phase 1 + Phase 2）
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
     */
    private LineageGraph buildLineageGraph() {
        List<NopMetaLineageEdge> edges = dao().findAll();
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

    /** 构建 tableName(lower) → metaTableId 索引（全量加载，元数据目录量级，见架构基线 §2.6.2 BFS 策略说明）。 */
    private Map<String, String> buildTableNameIndex() {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        List<NopMetaTable> tables = tableDao.findAll();
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
