package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.api.IDaoProvider;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import io.nop.metadata.service.field.MetaTableFieldResolver;
import io.nop.metadata.service.lineage.ColumnLineageCandidate;
import io.nop.metadata.service.lineage.SqlColumnLineageExtractor;
import io.nop.metadata.service.lineage.SqlSourceTableExtractor;
import io.nop.metadata.service.lineage.SqlTableReference;
import io.nop.metadata.service.NopMetadataException;
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

public class NopMetaLineageEdgeQueryAction {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaLineageEdgeQueryAction.class);

    private final SqlSourceTableExtractor sqlExtractor = new SqlSourceTableExtractor();
    private final SqlColumnLineageExtractor columnExtractor = new SqlColumnLineageExtractor();
    private final MetaTableFieldResolver fieldResolver = new MetaTableFieldResolver();

    private final int maxEdges;
    private final int maxTables;

    public NopMetaLineageEdgeQueryAction(int maxEdges, int maxTables) {
        this.maxEdges = maxEdges > 0 ? maxEdges : NopMetaLineageEdgeBizModel.DEFAULT_LINEAGE_MAX_EDGES;
        this.maxTables = maxTables > 0 ? maxTables : NopMetaLineageEdgeBizModel.DEFAULT_LINEAGE_MAX_TABLES;
    }

    public List<String> getUpstream(String metaTableId, IEntityDao<NopMetaLineageEdge> dao) {
        LineageGraph graph = buildLineageGraph(dao);
        Set<String> visited = new HashSet<>();
        visited.add(metaTableId);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(metaTableId);
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

    public List<String> getDownstream(String metaTableId, IEntityDao<NopMetaLineageEdge> dao) {
        LineageGraph graph = buildLineageGraph(dao);
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

    public List<String> getLineagePath(String sourceTableId, String targetTableId,
                                        IEntityDao<NopMetaLineageEdge> dao) {
        LineageGraph graph = buildLineageGraph(dao);
        if (sourceTableId.equals(targetTableId)) {
            return Collections.singletonList(sourceTableId);
        }
        Map<String, String> prev = new HashMap<>();
        Set<String> visited = new HashSet<>();
        visited.add(sourceTableId);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(sourceTableId);
        boolean found = false;
        while (!queue.isEmpty() && !found) {
            String cur = queue.poll();
            List<String> targets = graph.forward.get(cur);
            if (targets == null) continue;
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
        if (!found) return Collections.emptyList();
        List<String> path = new ArrayList<>();
        String node = targetTableId;
        while (node != null) {
            path.add(node);
            node = prev.get(node);
        }
        Collections.reverse(path);
        return path;
    }

    public List<String> getImpactAnalysis(String metaTableId, String columnName,
                                           IEntityDao<NopMetaLineageEdge> dao) {
        LineageGraph graph = buildLineageGraph(dao);
        List<String> tableLevel = bfsForward(graph.forward, metaTableId);
        if (columnName == null || columnName.isEmpty()) return tableLevel;
        List<String> columnFiltered = bfsForwardByColumn(graph.columnForward, metaTableId, columnName);
        return columnFiltered.isEmpty() ? tableLevel : columnFiltered;
    }

    public LineageExtractResult extractLineageFromSql(String metaTableId,
                                                       IDaoProvider daoProvider,
                                                       IEntityDao<NopMetaLineageEdge> dao) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable targetTable = tableDao.getEntityById(metaTableId);
        if (targetTable == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_SQL_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        if (!_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(targetTable.getTableType())) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_NOT_SQL_VIEW_TABLE)
                    .param("metaTableId", metaTableId)
                    .param("tableType", targetTable.getTableType());
        }
        String sourceSql = targetTable.getSourceSql();
        List<Map<String, Object>> errors = new ArrayList<>();
        List<SqlTableReference> refs;
        try {
            refs = sqlExtractor.extract(sourceSql);
        } catch (NopException e) {
            LOG.error("extractLineageFromSql failed for metaTableId={}", metaTableId, e);
            errors.add(errorMap("sql_parse", e));
            refs = Collections.emptyList();
        }
        Map<String, String> nameToId = buildTableNameIndex(daoProvider);
        String targetId = targetTable.getMetaTableId();
        List<String> unresolved = new ArrayList<>();
        int extracted = 0;
        for (SqlTableReference ref : refs) {
            String sourceId = nameToId.get(ref.getSimpleName().toLowerCase());
            if (sourceId == null) {
                unresolved.add(ref.getFullName());
                continue;
            }
            upsertSqlParseEdge(sourceId, targetId, dao);
            extracted++;
        }
        return new LineageExtractResult(extracted, unresolved, errors);
    }

    public LineageExtractResult extractColumnLineageFromSql(String metaTableId,
                                                             IDaoProvider daoProvider,
                                                             IEntityDao<NopMetaLineageEdge> dao) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable targetTable = tableDao.getEntityById(metaTableId);
        if (targetTable == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_SQL_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        if (!_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(targetTable.getTableType())) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_NOT_SQL_VIEW_TABLE)
                    .param("metaTableId", metaTableId)
                    .param("tableType", targetTable.getTableType());
        }
        String sourceSql = targetTable.getSourceSql();
        if (sourceSql == null || sourceSql.trim().isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_SQL_SOURCE_EMPTY).param("metaTableId", metaTableId);
        }
        List<Map<String, Object>> errors = new ArrayList<>();
        List<ColumnLineageCandidate> candidates;
        try {
            candidates = columnExtractor.extract(sourceSql);
        } catch (NopException e) {
            LOG.error("extractColumnLineageFromSql failed for metaTableId={}", metaTableId, e);
            errors.add(errorMap("sql_parse_column", e));
            candidates = Collections.emptyList();
        }
        Map<String, String> nameToId = buildTableNameIndex(daoProvider);
        String targetId = targetTable.getMetaTableId();
        List<String> unresolved = new ArrayList<>();
        int extracted = 0;
        for (ColumnLineageCandidate c : candidates) {
            if (c.isUnresolvable()) {
                unresolved.add(c.getTargetColumn() + " <- " + String.valueOf(c.getSourceColumn())
                        + " (" + c.getUnresolvedReason() + ")");
                continue;
            }
            String sourceId = nameToId.get(c.getSourceTableName().toLowerCase());
            if (sourceId == null) {
                unresolved.add(c.getTargetColumn() + " <- " + c.getSourceTableName() + "."
                        + c.getSourceColumn() + " (source-table-not-in-catalog)");
                continue;
            }
            upsertColumnSqlParseEdge(sourceId, targetId, c.getSourceColumn(), c.getTargetColumn(),
                    c.getTransformType(), dao);
            extracted++;
        }
        return new LineageExtractResult(extracted, unresolved, errors);
    }

    public LineageExtractResult extractMeasureLineage(String metaTableId,
                                                       IDaoProvider daoProvider,
                                                       IEntityDao<NopMetaLineageEdge> dao) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable targetTable = tableDao.getEntityById(metaTableId);
        if (targetTable == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_TABLE_NOT_FOUND).param("tableId", metaTableId);
        }
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);
        Set<String> fieldNames = fieldResolver.resolveFieldNames(targetTable, fieldDao);
        Set<String> fieldNamesLower = new HashSet<>(fieldNames.size());
        for (String n : fieldNames) {
            if (n != null) fieldNamesLower.add(n.toLowerCase());
        }
        String targetId = targetTable.getMetaTableId();
        deleteMeasureParseEdges(targetId, dao);
        IEntityDao<NopMetaTableMeasure> measureDao = daoProvider.daoFor(NopMetaTableMeasure.class);
        QueryBean mq = new QueryBean();
        mq.addFilter(FilterBeans.eq(NopMetaTableMeasure.PROP_NAME_metaTableId, metaTableId));
        List<NopMetaTableMeasure> measures = measureDao.findAllByQuery(mq);
        List<String> unresolved = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int extracted = 0;
        for (NopMetaTableMeasure measure : measures) {
            String expression = measure.getExpression();
            if (expression == null || expression.trim().isEmpty()) continue;
            String measureName = measure.getMeasureName();
            try {
                ExpressionMeasureValidator.ValidatedExpression validated =
                        ExpressionMeasureValidator.validateStatic(expression,
                                ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(),
                                metaTableId, measureName);
                String transformType = measure.getAggFunc() != null && !measure.getAggFunc().isEmpty()
                        ? _NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED
                        : _NopMetadataCoreConstants.LINEAGE_TRANSFORM_DERIVED;
                for (String ident : validated.identifiers) {
                    if (ident == null || ident.isEmpty()) continue;
                    if (ident.indexOf('.') >= 0) {
                        unresolved.add(measureName + " <- " + ident + " (join-context-deferred)");
                        continue;
                    }
                    if (!fieldNamesLower.contains(ident.toLowerCase())) {
                        unresolved.add(measureName + " <- " + ident + " (column-not-in-table-fields)");
                        continue;
                    }
                    upsertMeasureParseEdge(targetId, ident, measureName, transformType, dao);
                    extracted++;
                }
            } catch (NopException e) {
                LOG.warn("extractMeasureLineage validator failed for metaTableId={}, measureName={}",
                        metaTableId, measureName, e);
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("stage", "measure_parse");
                err.put("measureName", measureName);
                err.put("error", toErrorMessage(e));
                errors.add(err);
            }
        }
        return new LineageExtractResult(extracted, unresolved, errors);
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
                String tgt = edge.getTargetTableId();
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

    Map<String, String> buildTableNameIndex(IDaoProvider daoProvider) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.setLimit(maxTables + 1);
        List<NopMetaTable> tables = tableDao.findAllByQuery(q);
        if (tables.size() > maxTables) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINEAGE_TABLE_INDEX_TOO_LARGE)
                    .param("tables", tables.size()).param("limit", maxTables);
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (NopMetaTable t : tables) {
            if (t.getTableName() != null) {
                map.putIfAbsent(t.getTableName().toLowerCase(), t.getMetaTableId());
            }
        }
        return map;
    }

    Set<String> loadExistingTableIds(Set<String> ids, IDaoProvider daoProvider) {
        if (ids.isEmpty()) return Collections.emptySet();
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.in(NopMetaTable.PROP_NAME_metaTableId, ids));
        List<NopMetaTable> tables = tableDao.findAllByQuery(q);
        Set<String> existing = new HashSet<>();
        for (NopMetaTable t : tables) {
            existing.add(t.getMetaTableId());
        }
        return existing;
    }

    void upsertSqlParseEdge(String sourceTableId, String targetTableId,
                             IEntityDao<NopMetaLineageEdge> dao) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceTableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetTableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE));
        q.addFilter(FilterBeans.isNull(NopMetaLineageEdge.PROP_NAME_sourceColumn));
        NopMetaLineageEdge edge = dao.findFirstByQuery(q);
        if (edge == null) {
            edge = dao.newEntity();
            edge.setSourceTableId(sourceTableId);
            edge.setTargetTableId(targetTableId);
            edge.setLineageSource(_NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE);
            edge.setTransformType(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT);
            dao.saveEntity(edge);
        } else {
            edge.setTransformType(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT);
            dao.updateEntity(edge);
        }
    }

    void upsertColumnSqlParseEdge(String sourceTableId, String targetTableId,
                                   String sourceColumn, String targetColumn, String transformType,
                                   IEntityDao<NopMetaLineageEdge> dao) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceTableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetTableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceColumn, sourceColumn));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetColumn, targetColumn));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE));
        NopMetaLineageEdge edge = dao.findFirstByQuery(q);
        if (edge == null) {
            edge = dao.newEntity();
            edge.setSourceTableId(sourceTableId);
            edge.setTargetTableId(targetTableId);
            edge.setSourceColumn(sourceColumn);
            edge.setTargetColumn(targetColumn);
            edge.setLineageSource(_NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE);
            edge.setTransformType(transformType);
            dao.saveEntity(edge);
        } else {
            edge.setTransformType(transformType);
            dao.updateEntity(edge);
        }
    }

    void upsertMeasureParseEdge(String tableId, String sourceColumn, String measureName,
                                 String transformType, IEntityDao<NopMetaLineageEdge> dao) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceColumn, sourceColumn));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetColumn, measureName));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE));
        NopMetaLineageEdge edge = dao.findFirstByQuery(q);
        if (edge == null) {
            edge = dao.newEntity();
            edge.setSourceTableId(tableId);
            edge.setTargetTableId(tableId);
            edge.setSourceColumn(sourceColumn);
            edge.setTargetColumn(measureName);
            edge.setLineageSource(_NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE);
            edge.setTransformType(transformType);
            dao.saveEntity(edge);
        } else {
            edge.setTransformType(transformType);
            dao.updateEntity(edge);
        }
    }

    void deleteMeasureParseEdges(String tableId, IEntityDao<NopMetaLineageEdge> dao) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, tableId));
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
        err.put("error", toErrorMessage(e));
        return err;
    }

    private static String toErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg != null) return msg;
        if (e instanceof NopException) return ((NopException) e).getErrorCode();
        return e.getClass().getName();
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
        public final List<String> unresolved;
        public final List<Map<String, Object>> errors;

        public LineageExtractResult(int edgeCount, List<String> unresolved, List<Map<String, Object>> errors) {
            this.edgeCount = edgeCount;
            this.unresolved = unresolved;
            this.errors = errors;
        }
    }
}
