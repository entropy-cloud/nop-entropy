package io.nop.metadata.service.entity;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.api.IDaoProvider;
import io.nop.metadata.service.NopMetadataHelper;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityMeasure;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import io.nop.metadata.service.field.MetaEntityFieldResolver;
import io.nop.metadata.service.lineage.ColumnLineageCandidate;
import io.nop.metadata.service.lineage.SqlColumnLineageExtractor;
import io.nop.metadata.service.lineage.SqlSourceEntityExtractor;
import io.nop.metadata.service.lineage.SqlTableReference;
import io.nop.metadata.service.NopMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class NopMetaLineageEdgeQueryAction {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaLineageEdgeQueryAction.class);

    private final SqlSourceEntityExtractor sqlExtractor = new SqlSourceEntityExtractor();
    private final SqlColumnLineageExtractor columnExtractor = new SqlColumnLineageExtractor();
    private final MetaEntityFieldResolver fieldResolver = new MetaEntityFieldResolver();

    private final int maxEdges;
    private final int maxTables;

    public NopMetaLineageEdgeQueryAction(int maxEdges, int maxTables) {
        this.maxEdges = maxEdges > 0 ? maxEdges : NopMetaLineageEdgeBizModel.DEFAULT_LINEAGE_MAX_EDGES;
        this.maxTables = maxTables > 0 ? maxTables : NopMetaLineageEdgeBizModel.DEFAULT_LINEAGE_MAX_TABLES;
    }

    public List<String> getUpstream(String metaEntityId, IEntityDao<NopMetaLineageEdge> dao) {
        LineageGraph graph = buildLineageGraph(dao);
        Set<String> visited = new HashSet<>();
        visited.add(metaEntityId);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(metaEntityId);
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String cur = queue.poll();
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

    public List<String> getDownstream(String metaEntityId, IEntityDao<NopMetaLineageEdge> dao) {
        LineageGraph graph = buildLineageGraph(dao);
        Set<String> visited = new HashSet<>();
        visited.add(metaEntityId);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(metaEntityId);
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

    public List<String> getLineagePath(String sourceEntityId, String targetEntityId,
                                        IEntityDao<NopMetaLineageEdge> dao) {
        LineageGraph graph = buildLineageGraph(dao);
        if (sourceEntityId.equals(targetEntityId)) {
            return Collections.singletonList(sourceEntityId);
        }
        Map<String, String> prev = new HashMap<>();
        Set<String> visited = new HashSet<>();
        visited.add(sourceEntityId);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(sourceEntityId);
        boolean found = false;
        while (!queue.isEmpty() && !found) {
            String cur = queue.poll();
            List<String> targets = graph.forward.get(cur);
            if (targets == null) continue;
            for (String tgt : targets) {
                if (visited.add(tgt)) {
                    prev.put(tgt, cur);
                    if (tgt.equals(targetEntityId)) {
                        found = true;
                        break;
                    }
                    queue.add(tgt);
                }
            }
        }
        if (!found) return Collections.emptyList();
        List<String> path = new ArrayList<>();
        String node = targetEntityId;
        while (node != null) {
            path.add(node);
            node = prev.get(node);
        }
        Collections.reverse(path);
        return path;
    }

    public List<String> getImpactAnalysis(String metaEntityId, String columnName,
                                           IEntityDao<NopMetaLineageEdge> dao) {
        LineageGraph graph = buildLineageGraph(dao);
        List<String> tableLevel = bfsForward(graph.forward, metaEntityId);
        if (columnName == null || columnName.isEmpty()) return tableLevel;
        List<String> columnFiltered = bfsForwardByColumn(graph.columnForward, metaEntityId, columnName);
        return columnFiltered.isEmpty() ? tableLevel : columnFiltered;
    }

    public LineageExtractResult extractLineageFromSql(String metaEntityId,
                                                       IDaoProvider daoProvider,
                                                       IEntityDao<NopMetaLineageEdge> dao) {
        IEntityDao<NopMetaEntity> tableDao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity targetEntity = tableDao.getEntityById(metaEntityId);
        if (targetEntity == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_SQL_TABLE_NOT_FOUND).param("metaEntityId", metaEntityId);
        }
        if (!_NopMetadataCoreConstants.ENTITY_KIND_SQL_VIEW.equals(targetEntity.getEntityKind())) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_NOT_SQL_VIEW_TABLE)
                    .param("metaEntityId", metaEntityId)
                    .param("entityKind", targetEntity.getEntityKind());
        }
        String sourceSql = targetEntity.getSourceSql();
        if (sourceSql == null || sourceSql.trim().isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_SQL_SOURCE_EMPTY).param("metaEntityId", metaEntityId);
        }
        List<Map<String, Object>> errors = new ArrayList<>();
        List<SqlTableReference> refs;
        try {
            refs = sqlExtractor.extract(sourceSql);
        } catch (NopException e) {
            LOG.error("extractLineageFromSql failed for metaEntityId={}, errorCode={}",
                    metaEntityId, NopMetadataErrors.ERR_LINEAGE_QUERY_ISOLATED.getErrorCode(), e);
            errors.add(errorMap("sql_parse", e));
            refs = Collections.emptyList();
        }
        Map<String, String> nameToId = buildTableNameIndex(daoProvider);
        String targetId = targetEntity.getMetaEntityId();
        List<String> unresolved = new ArrayList<>();
        List<String> candidateSourceIds = new ArrayList<>();
        for (SqlTableReference ref : refs) {
            String sourceId = nameToId.get(ref.getSimpleName().toLowerCase(Locale.ROOT));
            if (sourceId == null) {
                unresolved.add(ref.getFullName());
                continue;
            }
            candidateSourceIds.add(sourceId);
        }
        // check2 P2-04（2026-08-23 审计）：sql_parse 通道语义是"从当前 SQL 重解析"——先按
        // targetEntityId 删除本通道（表级：sourceColumn IS NULL）全部旧边再插入本次解析结果
        // （对齐 measure 通道 deleteMeasureParseEdges 先清后建），消除 sourceSql 变更后
        // 已移出源表的陈旧边残留（血缘图累积过期边、影响分析失真、消耗 maxEdges 配额）。
        // 只清表级通道（sourceColumn IS NULL），不误删列级通道边（两通道可独立重抽取）。
        deleteSqlParseEdges(targetId, dao, false);
        List<NopMetaLineageEdge> newEdges = new ArrayList<>();
        Set<String> insertedSourceIds = new LinkedHashSet<>();
        for (String sourceId : candidateSourceIds) {
            if (insertedSourceIds.add(sourceId)) {
                NopMetaLineageEdge edge = dao.newEntity();
                edge.setSourceEntityId(sourceId);
                edge.setTargetEntityId(targetId);
                edge.setLineageSource(_NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE);
                edge.setTransformType(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT);
                newEdges.add(edge);
            }
        }
        if (!newEdges.isEmpty()) {
            dao.batchSaveEntities(newEdges);
        }
        return new LineageExtractResult(candidateSourceIds.size(),
                dedupPreservingOrder(candidateSourceIds), unresolved, errors);
    }

    public LineageExtractResult extractColumnLineageFromSql(String metaEntityId,
                                                             IDaoProvider daoProvider,
                                                             IEntityDao<NopMetaLineageEdge> dao) {
        IEntityDao<NopMetaEntity> tableDao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity targetEntity = tableDao.getEntityById(metaEntityId);
        if (targetEntity == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_SQL_TABLE_NOT_FOUND).param("metaEntityId", metaEntityId);
        }
        if (!_NopMetadataCoreConstants.ENTITY_KIND_SQL_VIEW.equals(targetEntity.getEntityKind())) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_NOT_SQL_VIEW_TABLE)
                    .param("metaEntityId", metaEntityId)
                    .param("entityKind", targetEntity.getEntityKind());
        }
        String sourceSql = targetEntity.getSourceSql();
        if (sourceSql == null || sourceSql.trim().isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_SQL_SOURCE_EMPTY).param("metaEntityId", metaEntityId);
        }
        List<Map<String, Object>> errors = new ArrayList<>();
        List<ColumnLineageCandidate> candidates;
        try {
            candidates = columnExtractor.extract(sourceSql);
        } catch (NopException e) {
            LOG.error("extractColumnLineageFromSql failed for metaEntityId={}, errorCode={}",
                    metaEntityId, NopMetadataErrors.ERR_LINEAGE_QUERY_ISOLATED.getErrorCode(), e);
            errors.add(errorMap("sql_parse_column", e));
            candidates = Collections.emptyList();
        }
        Map<String, String> nameToId = buildTableNameIndex(daoProvider);
        String targetId = targetEntity.getMetaEntityId();
        List<String> unresolved = new ArrayList<>();
        List<String> resolvedSourceIds = new ArrayList<>();
        List<ColumnLineageCandidate> resolvedCandidates = new ArrayList<>();
        for (ColumnLineageCandidate c : candidates) {
            if (c.isUnresolvable()) {
                unresolved.add(c.getTargetColumn() + " <- " + String.valueOf(c.getSourceColumn())
                        + " (" + c.getUnresolvedReason() + ")");
                continue;
            }
            String sourceId = nameToId.get(c.getSourceEntityName().toLowerCase(Locale.ROOT));
            if (sourceId == null) {
                unresolved.add(c.getTargetColumn() + " <- " + c.getSourceEntityName() + "."
                        + c.getSourceColumn() + " (source-table-not-in-catalog)");
                continue;
            }
            resolvedSourceIds.add(sourceId);
            resolvedCandidates.add(c);
        }
        // check2 P2-04（2026-08-23 审计）：先按 targetEntityId 删除本通道（列级：sourceColumn 非空）
        // 全部旧边再插入本次解析结果（对齐 measure 通道先清后建）——修复前仅按 existingEdgeMap
        // 增量插入/更新 transformType，sourceSql 变更后不再被引用的列边永久残留（DB UK 只防重复
        // 插入，不清理陈旧行），getUpstream/getImpactAnalysis 血缘图累积过期边。只清列级通道，
        // 不误删表级通道边（两通道可独立重抽取）；delete 后重插也顺带覆盖 transformType 更新场景。
        deleteSqlParseEdges(targetId, dao, true);
        List<NopMetaLineageEdge> toSave = new ArrayList<>();
        // 同批去重：同一表达式内重复列引用（如 a.x + a.x）产生同键候选，第二条同键候选会重复
        // INSERT（MA7.4-02）——用已见键集合拦截。
        // D5（Cycle 2，adjudication-table-cycle2 §5）：键为结构性 List（值级 equals/hashCode），
        // 非 "|" 拼接 String——带引号 SQL 派生列名可含 "|"，拼接键碰撞会导致第二条边被静默吞掉
        // （沿 AR-03 结构性键先例）。
        Set<List<String>> seenKeys = new HashSet<>();
        int extracted = 0;
        for (int i = 0; i < resolvedCandidates.size(); i++) {
            ColumnLineageCandidate c = resolvedCandidates.get(i);
            String sourceId = resolvedSourceIds.get(i);
            List<String> key = Arrays.asList(sourceId, c.getSourceColumn(), c.getTargetColumn());
            if (seenKeys.add(key)) {
                NopMetaLineageEdge edge = dao.newEntity();
                edge.setSourceEntityId(sourceId);
                edge.setTargetEntityId(targetId);
                edge.setSourceColumn(c.getSourceColumn());
                edge.setTargetColumn(c.getTargetColumn());
                edge.setLineageSource(_NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE);
                edge.setTransformType(c.getTransformType());
                toSave.add(edge);
            }
            extracted++;
        }
        if (!toSave.isEmpty()) {
            dao.batchSaveEntities(toSave);
        }
        return new LineageExtractResult(extracted, dedupPreservingOrder(resolvedSourceIds), unresolved, errors);
    }

    public LineageExtractResult extractMeasureLineage(String metaEntityId,
                                                       IDaoProvider daoProvider,
                                                       IEntityDao<NopMetaLineageEdge> dao) {
        IEntityDao<NopMetaEntity> tableDao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity targetEntity = tableDao.getEntityById(metaEntityId);
        if (targetEntity == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_TABLE_NOT_FOUND).param("tableId", metaEntityId);
        }
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);
        Set<String> fieldNames = fieldResolver.resolveFieldNames(targetEntity, fieldDao);
        Set<String> fieldNamesLower = new HashSet<>(fieldNames.size());
        for (String n : fieldNames) {
            if (n != null) fieldNamesLower.add(n.toLowerCase(Locale.ROOT));
        }
        String targetId = targetEntity.getMetaEntityId();
        deleteMeasureParseEdges(targetId, dao);
        IEntityDao<NopMetaEntityMeasure> measureDao = daoProvider.daoFor(NopMetaEntityMeasure.class);
        QueryBean mq = new QueryBean();
        mq.addFilter(FilterBeans.eq(NopMetaEntityMeasure.PROP_NAME_metaEntityId, metaEntityId));
        List<NopMetaEntityMeasure> measures = measureDao.findAllByQuery(mq);
        List<String> unresolved = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int extracted = 0;
        List<NopMetaLineageEdge> toSave = new ArrayList<>();
        for (NopMetaEntityMeasure measure : measures) {
            String expression = measure.getExpression();
            if (expression == null || expression.trim().isEmpty()) continue;
            String measureName = measure.getMeasureName();
            try {
                ExpressionMeasureValidator.ValidatedExpression validated =
                        ExpressionMeasureValidator.validateStatic(expression,
                                ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(),
                                metaEntityId, measureName);
                String transformType = measure.getAggFunc() != null && !measure.getAggFunc().isEmpty()
                        ? _NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED
                        : _NopMetadataCoreConstants.LINEAGE_TRANSFORM_DERIVED;
                for (String ident : validated.identifiers) {
                    if (ident == null || ident.isEmpty()) continue;
                    if (ident.indexOf('.') >= 0) {
                        unresolved.add(measureName + " <- " + ident + " (join-context-deferred)");
                        continue;
                    }
                    if (!fieldNamesLower.contains(ident.toLowerCase(Locale.ROOT))) {
                        unresolved.add(measureName + " <- " + ident + " (column-not-in-table-fields)");
                        continue;
                    }
                    NopMetaLineageEdge edge = dao.newEntity();
                    edge.setSourceEntityId(targetId);
                    edge.setTargetEntityId(targetId);
                    edge.setSourceColumn(ident);
                    edge.setTargetColumn(measureName);
                    edge.setLineageSource(_NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE);
                    edge.setTransformType(transformType);
                    toSave.add(edge);
                    extracted++;
                }
            } catch (NopException e) {
                LOG.warn("extractMeasureLineage validator failed for metaEntityId={}, measureName={}, errorCode={}",
                        metaEntityId, measureName, NopMetadataErrors.ERR_LINEAGE_QUERY_ISOLATED.getErrorCode(), e);
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("stage", "measure_parse");
                err.put("measureName", measureName);
                err.put("error", NopMetadataHelper.toErrorMessage(e));
                errors.add(err);
            }
        }
        if (!toSave.isEmpty()) {
            dao.batchSaveEntities(toSave);
        }
        // 指标级语义裁定（P1-3）：measure 边全部为自环（sourceEntityId=targetId），无独立 resolved 计算——
        // sourceEntitys = 宿主表自身 [metaEntityId]（与边语义一致）；产出 0 条边时为空列表（不伪造源）。
        List<String> measureSourceEntitys = extracted > 0
                ? Collections.singletonList(targetId)
                : Collections.emptyList();
        return new LineageExtractResult(extracted, measureSourceEntitys, unresolved, errors);
    }

    // ============================================================
    // shared helpers
    // ============================================================

    List<String> bfsForward(Map<String, List<String>> forward, String start) {
        Set<String> visited = new HashSet<>();
        visited.add(start);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            List<String> targets = forward.get(cur);
            if (targets == null) continue;
            for (String tgt : targets) {
                if (visited.add(tgt)) {
                    result.add(tgt);
                    queue.add(tgt);
                }
            }
        }
        return result;
    }

    List<String> bfsForwardByColumn(Map<String, List<NopMetaLineageEdge>> columnForward,
                                     String start, String columnName) {
        Set<String> visited = new HashSet<>();
        visited.add(start);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            List<NopMetaLineageEdge> edges = columnForward.get(cur);
            if (edges == null) continue;
            for (NopMetaLineageEdge edge : edges) {
                if (!matchesColumn(edge, columnName)) continue;
                String tgt = edge.getTargetEntityId();
                if (visited.add(tgt)) {
                    result.add(tgt);
                    queue.add(tgt);
                }
            }
        }
        return result;
    }

    private LineageGraph buildLineageGraph(IEntityDao<NopMetaLineageEdge> dao) {
        QueryBean q = new QueryBean();
        q.setLimit(maxEdges + 1);
        List<NopMetaLineageEdge> allEdges = dao.findAllByQuery(q);
        if (allEdges.size() > maxEdges) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_GRAPH_TOO_LARGE)
                    .param("edges", allEdges.size()).param("limit", maxEdges);
        }
        Map<String, List<String>> forward = new HashMap<>();
        Map<String, List<String>> reverse = new HashMap<>();
        Map<String, List<NopMetaLineageEdge>> columnForward = new HashMap<>();
        for (NopMetaLineageEdge edge : allEdges) {
            String src = edge.getSourceEntityId();
            String tgt = edge.getTargetEntityId();
            forward.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
            reverse.computeIfAbsent(tgt, k -> new ArrayList<>()).add(src);
            if (edge.getSourceColumn() != null || edge.getTargetColumn() != null) {
                columnForward.computeIfAbsent(src, k -> new ArrayList<>()).add(edge);
            }
        }
        return new LineageGraph(forward, reverse, columnForward);
    }

    Map<String, String> buildTableNameIndex(IDaoProvider daoProvider) {
        IEntityDao<NopMetaEntity> tableDao = daoProvider.daoFor(NopMetaEntity.class);
        QueryBean q = new QueryBean();
        q.setLimit(maxTables + 1);
        List<NopMetaEntity> tables = tableDao.findAllByQuery(q);
        if (tables.size() > maxTables) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_TABLE_INDEX_TOO_LARGE)
                    .param("tables", tables.size()).param("limit", maxTables);
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (NopMetaEntity t : tables) {
            if (t.getTableName() != null) {
                map.putIfAbsent(t.getTableName().toLowerCase(Locale.ROOT), t.getMetaEntityId());
            }
        }
        return map;
    }

    Set<String> loadExistingTableIds(Set<String> ids, IDaoProvider daoProvider) {
        if (ids.isEmpty()) return Collections.emptySet();
        IEntityDao<NopMetaEntity> tableDao = daoProvider.daoFor(NopMetaEntity.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.in(NopMetaEntity.PROP_NAME_metaEntityId, ids));
        List<NopMetaEntity> tables = tableDao.findAllByQuery(q);
        Set<String> existing = new HashSet<>();
        for (NopMetaEntity t : tables) {
            existing.add(t.getMetaEntityId());
        }
        return existing;
    }

    /**
     * 删除指定 targetEntityId 的 sql_parse 通道旧边（check2 P2-04，重抽取前对账删除）。
     *
     * @param columnLevel true 清列级通道（sourceColumn 非空）；false 清表级通道（sourceColumn IS NULL）
     */
    void deleteSqlParseEdges(String targetEntityId, IEntityDao<NopMetaLineageEdge> dao, boolean columnLevel) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetEntityId, targetEntityId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE));
        if (columnLevel) {
            q.addFilter(FilterBeans.notNull(NopMetaLineageEdge.PROP_NAME_sourceColumn));
        } else {
            q.addFilter(FilterBeans.isNull(NopMetaLineageEdge.PROP_NAME_sourceColumn));
        }
        // deleteByQuery 单语句删除（不装载实体）：陈旧边集合可大（maxEdges 配额 10 万），
        // load+delete 循环既慢也在无事务上下文的直调路径上跨 session 失败
        dao.deleteByQuery(q);
    }

    void deleteMeasureParseEdges(String tableId, IEntityDao<NopMetaLineageEdge> dao) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceEntityId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetEntityId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE));
        List<NopMetaLineageEdge> stale = dao.findAllByQuery(q);
        for (NopMetaLineageEdge e : stale) {
            dao.deleteEntity(e);
        }
    }

    private static boolean matchesColumn(NopMetaLineageEdge edge, String columnName) {
        return columnName != null && (columnName.equals(edge.getSourceColumn())
                || columnName.equals(edge.getTargetColumn()));
    }

    private static Map<String, Object> errorMap(String stage, NopException e) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("stage", stage);
        err.put("error", NopMetadataHelper.toErrorMessage(e));
        return err;
    }

    /** 去重保序（跨 schema 同 simpleName 可解析到同一 metaEntity ID，sourceEntitys 语义为"源表集"）。 */
    private static List<String> dedupPreservingOrder(List<String> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    public static final class LineageGraph {
        public final Map<String, List<String>> forward;
        public final Map<String, List<String>> reverse;
        public final Map<String, List<NopMetaLineageEdge>> columnForward;

        public LineageGraph(Map<String, List<String>> forward,
                            Map<String, List<String>> reverse,
                            Map<String, List<NopMetaLineageEdge>> columnForward) {
            this.forward = forward;
            this.reverse = reverse;
            this.columnForward = columnForward;
        }
    }

    public static final class LineageExtractResult {
        public final int edgeCount;
        /**
         * 已解析源表标识（metaEntity ID 集，去重保序）。
         * 表级 = nameToId 命中的 candidateSourceIds；列级 = 命中的 resolvedSourceIds；
         * 指标级 = 宿主表自身（自环边语义，仅当产出 ≥1 条边，否则空列表）。
         * 与 unresolved（完整名/诊断串）异质并存，语义见 owner doc。
         */
        public final List<String> resolvedSourceEntitys;
        public final List<String> unresolved;
        public final List<Map<String, Object>> errors;

        public LineageExtractResult(int edgeCount, List<String> resolvedSourceEntitys,
                                    List<String> unresolved, List<Map<String, Object>> errors) {
            this.edgeCount = edgeCount;
            this.resolvedSourceEntitys = resolvedSourceEntitys;
            this.unresolved = unresolved;
            this.errors = errors;
        }
    }
}
