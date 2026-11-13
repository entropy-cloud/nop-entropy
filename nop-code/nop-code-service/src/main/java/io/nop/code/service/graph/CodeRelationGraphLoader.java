package io.nop.code.service.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.core.graph.CodeEdgeData;
import io.nop.code.core.graph.CodeRelationGraph;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.code.dao.entity.NopCodeAnnotationUsage;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeInheritance;
import io.nop.code.dao.entity.NopCodeSemanticEdge;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;

/**
 * Loads the typed multi-family edge view ({@link CodeRelationGraph}) for an index.
 *
 * Loads the four relation tables (calls / inheritance / annotation usages / semantic edges)
 * in batches and maps them to {@link CodeEdgeData}. The cumulative edge set is capped
 * (default {@value #DEFAULT_MAX_TOTAL_EDGES} edges): once the cap is reached no further
 * batches are pulled — families fill first-come-first-served in the fixed table order
 * (calls &rarr; inheritance &rarr; annotation &rarr; semantic), the projected graph's
 * truncated flag is set and a structured WARN is emitted. The cap bounds the intermediate
 * memory envelope of the public graph-query actions (surprise connections, question
 * generation, wiki export); the downstream adjacency + per-edge attrs projection scales in
 * the same order as the cap. Oversized indices therefore yield results derived from a
 * partial edge set (SEMANTIC/ANNOTATION families systematically dropped first), observable
 * via {@link CodeRelationGraph#isTruncated()} and the WARN.
 *
 * Attr conventions (graph-discovery-and-export-design.md §3.0/§3.1):
 * - Edge.type = family (CALLS / INHERITANCE / ANNOTATION / SEMANTIC)
 * - attrs.relationType = refinement when present (EXTENDS / IMPLEMENTS / SEMANTICALLY_SIMILAR_TO
 *   as stored — uppercase enum names), family otherwise
 * - attrs.confidence = EdgeConfidence enum name. Semantic rows map confidence values
 *   10/20/30 explicitly (any other value, including the persisted 0, omits the attribute instead
 *   of silently degrading to EXTRACTED). Call/inheritance/annotation rows derive it from
 *   provenance: AST_EXTRACTION/SYMBOL_SOLVER -> EXTRACTED, HEURISTIC/FRAMEWORK_INFERENCE ->
 *   INFERRED, anything else -> EXTRACTED. Raw provenance is always preserved in attrs.provenance.
 * - attrs.directed mirrors storage direction; semantic rows honor the directed column.
 * - attrs.sourceFilePath/targetFilePath resolved via the optional symbolId resolver; omitted
 *   when no resolver is given or the symbol is unknown.
 */
public class CodeRelationGraphLoader {
    private static final Logger LOG = LoggerFactory.getLogger(CodeRelationGraphLoader.class);
    private static final int BATCH_SIZE = 5000;
    /**
     * Cumulative edge cap across the four relation tables (aligned with the module's
     * AnnotationPatternExtractor.MAX_EDGES convention). Test seam: instance field +
     * package-visible setter, production default = this compile-time constant.
     */
    static final int DEFAULT_MAX_TOTAL_EDGES = 50000;

    private int maxTotalEdges = DEFAULT_MAX_TOTAL_EDGES;

    public CodeRelationGraphLoader() {
    }

    /**
     * Test seam (TP-001: the cap constant is injectable so tests need not fabricate huge data
     * sets). Package-visible by design — not wired by IoC; production keeps the default.
     */
    void setMaxTotalEdges(int maxTotalEdges) {
        if (maxTotalEdges <= 0)
            throw new IllegalArgumentException("maxTotalEdges must be positive, got: " + maxTotalEdges);
        this.maxTotalEdges = maxTotalEdges;
    }

    /**
     * Static facade retained with its original signature and behavior: the three graph-query
     * call sites delegate here and load with the default cumulative cap.
     */
    public static CodeRelationGraph load(String indexId, IDaoProvider daoProvider,
                                          Function<String, String> filePathResolver) {
        return new CodeRelationGraphLoader().loadGraph(indexId, daoProvider, filePathResolver);
    }

    /**
     * Instance load path sharing the truncation branch with the static facade. Families fill
     * first-come-first-served in the fixed table order until the cumulative cap is exhausted.
     */
    CodeRelationGraph loadGraph(String indexId, IDaoProvider daoProvider,
                                Function<String, String> filePathResolver) {
        List<CodeEdgeData> edges = new ArrayList<>();
        int[] remaining = {maxTotalEdges};
        // fixed table order calls -> inheritance -> annotation -> semantic (first-come-first-served)
        edges.addAll(loadCalls(indexId, daoProvider, filePathResolver, remaining));
        boolean truncated = budgetExhausted(remaining);
        edges.addAll(loadInheritances(indexId, daoProvider, filePathResolver, remaining));
        truncated |= budgetExhausted(remaining);
        edges.addAll(loadAnnotationUsages(indexId, daoProvider, filePathResolver, remaining));
        truncated |= budgetExhausted(remaining);
        edges.addAll(loadSemanticEdges(indexId, daoProvider, filePathResolver, remaining));
        truncated |= budgetExhausted(remaining);
        if (truncated) {
            LOG.warn("nop.code.relation-graph-truncated:indexId={},cap={},edges={}"
                            + " (families beyond the cap are dropped in fixed table order)",
                    indexId, maxTotalEdges, edges.size());
        }
        LOG.info("nop.code.relation-graph-loaded:indexId={},edges={},truncated={}", indexId, edges.size(), truncated);
        CodeRelationGraph graph = new CodeRelationGraph(edges);
        graph.setTruncated(truncated);
        return graph;
    }

    /** Budget exhausted (cap reached) = truncation event, aligned with the module's
     *  AnnotationPatternExtractor cap-and-return convention. */
    private static boolean budgetExhausted(int[] remaining) {
        return remaining[0] <= 0;
    }

    static List<CodeEdgeData> loadCalls(String indexId, IDaoProvider daoProvider,
                                        Function<String, String> filePathResolver) {
        return loadCalls(indexId, daoProvider, filePathResolver, new int[]{Integer.MAX_VALUE});
    }

    static List<CodeEdgeData> loadCalls(String indexId, IDaoProvider daoProvider,
                                        Function<String, String> filePathResolver, int[] remaining) {
        IEntityDao<NopCodeCall> dao = daoProvider.daoFor(NopCodeCall.class);
        List<CodeEdgeData> result = new ArrayList<>();
        for (NopCodeCall call : batched(dao, indexId, remaining)) {
            result.add(toCallEdge(call, filePathResolver));
        }
        return result;
    }

    static CodeEdgeData toCallEdge(NopCodeCall call, Function<String, String> filePathResolver) {
        return CodeEdgeData.of("CALLS", call.getCallerId(), call.getCalleeId())
                .confidence(confidenceFromProvenance(call.getProvenance()))
                .provenance(call.getProvenance())
                .sourceFilePath(resolve(filePathResolver, call.getCallerId()))
                .targetFilePath(resolve(filePathResolver, call.getCalleeId()))
                .build();
    }

    static List<CodeEdgeData> loadInheritances(String indexId, IDaoProvider daoProvider,
                                               Function<String, String> filePathResolver) {
        return loadInheritances(indexId, daoProvider, filePathResolver, new int[]{Integer.MAX_VALUE});
    }

    static List<CodeEdgeData> loadInheritances(String indexId, IDaoProvider daoProvider,
                                               Function<String, String> filePathResolver, int[] remaining) {
        IEntityDao<NopCodeInheritance> dao = daoProvider.daoFor(NopCodeInheritance.class);
        List<CodeEdgeData> result = new ArrayList<>();
        for (NopCodeInheritance inh : batched(dao, indexId, remaining)) {
            result.add(toInheritanceEdge(inh, filePathResolver));
        }
        return result;
    }

    static CodeEdgeData toInheritanceEdge(NopCodeInheritance inh, Function<String, String> filePathResolver) {
        return CodeEdgeData.of("INHERITANCE", inh.getSubTypeId(), inh.getSuperTypeId())
                .relationType(inh.getRelationType())
                .confidence(confidenceFromProvenance(inh.getProvenance()))
                .provenance(inh.getProvenance())
                .sourceFilePath(resolve(filePathResolver, inh.getSubTypeId()))
                .targetFilePath(resolve(filePathResolver, inh.getSuperTypeId()))
                .build();
    }

    static List<CodeEdgeData> loadAnnotationUsages(String indexId, IDaoProvider daoProvider,
                                                   Function<String, String> filePathResolver) {
        return loadAnnotationUsages(indexId, daoProvider, filePathResolver, new int[]{Integer.MAX_VALUE});
    }

    static List<CodeEdgeData> loadAnnotationUsages(String indexId, IDaoProvider daoProvider,
                                                   Function<String, String> filePathResolver, int[] remaining) {
        IEntityDao<NopCodeAnnotationUsage> dao = daoProvider.daoFor(NopCodeAnnotationUsage.class);
        List<CodeEdgeData> result = new ArrayList<>();
        for (NopCodeAnnotationUsage usage : batched(dao, indexId, remaining)) {
            result.add(toAnnotationEdge(usage, filePathResolver));
        }
        return result;
    }

    static CodeEdgeData toAnnotationEdge(NopCodeAnnotationUsage usage, Function<String, String> filePathResolver) {
        return CodeEdgeData.of("ANNOTATION", usage.getAnnotatedSymbolId(), usage.getAnnotationTypeId())
                .confidence(confidenceFromProvenance(usage.getProvenance()))
                .provenance(usage.getProvenance())
                .sourceFilePath(resolve(filePathResolver, usage.getAnnotatedSymbolId()))
                .targetFilePath(resolve(filePathResolver, usage.getAnnotationTypeId()))
                .build();
    }

    static List<CodeEdgeData> loadSemanticEdges(String indexId, IDaoProvider daoProvider,
                                                Function<String, String> filePathResolver) {
        return loadSemanticEdges(indexId, daoProvider, filePathResolver, new int[]{Integer.MAX_VALUE});
    }

    static List<CodeEdgeData> loadSemanticEdges(String indexId, IDaoProvider daoProvider,
                                                Function<String, String> filePathResolver, int[] remaining) {
        IEntityDao<NopCodeSemanticEdge> dao = daoProvider.daoFor(NopCodeSemanticEdge.class);
        List<CodeEdgeData> result = new ArrayList<>();
        for (NopCodeSemanticEdge semanticEdge : batched(dao, indexId, remaining)) {
            result.add(toSemanticEdge(semanticEdge, filePathResolver));
        }
        return result;
    }

    static CodeEdgeData toSemanticEdge(NopCodeSemanticEdge semanticEdge, Function<String, String> filePathResolver) {
        double weight = semanticEdge.getConfidenceScore() != null ? semanticEdge.getConfidenceScore() : 1.0;
        return CodeEdgeData.of("SEMANTIC", semanticEdge.getSourceSymbolId(), semanticEdge.getTargetSymbolId())
                .relationType(semanticEdge.getRelationType())
                .confidence(confidenceFromValue(semanticEdge.getConfidence()))
                .provenance(semanticEdge.getProvenance())
                .directed(semanticEdge.getDirected() == null || semanticEdge.getDirected())
                .sourceFilePath(resolve(filePathResolver, semanticEdge.getSourceSymbolId()))
                .targetFilePath(resolve(filePathResolver, semanticEdge.getTargetSymbolId()))
                .weight(weight)
                .build();
    }

    /**
     * Explicit mapping from the stored integer confidence (10/20/30). Unknown values — including
     * the persisted 0 for absent confidence — yield null so the attribute is omitted, never
     * silently degraded to EXTRACTED.
     */
    static String confidenceFromValue(Integer confidence) {
        if (confidence == null)
            return null;
        switch (confidence) {
            case 10:
                return EdgeConfidence.EXTRACTED.name();
            case 20:
                return EdgeConfidence.INFERRED.name();
            case 30:
                return EdgeConfidence.AMBIGUOUS.name();
            default:
                return null;
        }
    }

    static String confidenceFromProvenance(String provenance) {
        if (provenance == null)
            return null;
        switch (provenance) {
            case "HEURISTIC":
            case "FRAMEWORK_INFERENCE":
                return EdgeConfidence.INFERRED.name();
            default:
                // AST_EXTRACTION / SYMBOL_SOLVER / MANUAL / unknown -> EXTRACTED
                return EdgeConfidence.EXTRACTED.name();
        }
    }

    private static String resolve(Function<String, String> resolver, String symbolId) {
        if (resolver == null || symbolId == null)
            return null;
        // resolver contract: return null when the symbol is unknown; it never throws for misses
        return resolver.apply(symbolId);
    }

    private static <T extends IDaoEntity> Iterable<T> batched(IEntityDao<T> dao, String indexId) {
        return batched(dao, indexId, new int[]{Integer.MAX_VALUE});
    }

    /**
     * Offset-paginated batch pull that stops once the cumulative edge budget is exhausted
     * ({@code remaining[0] <= 0}) — the budget is shared across all four tables so the cap is
     * cumulative, not per-table.
     */
    private static <T extends IDaoEntity> Iterable<T> batched(IEntityDao<T> dao, String indexId, int[] remaining) {
        List<T> all = new ArrayList<>();
        long offset = 0;
        while (remaining[0] > 0) {
            int requested = Math.min(BATCH_SIZE, remaining[0]);
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq("indexId", indexId));
            query.setOffset(offset);
            query.setLimit(requested);
            List<T> batch = dao.findPageByQuery(query);
            if (batch.isEmpty())
                break;
            all.addAll(batch);
            remaining[0] -= batch.size();
            if (batch.size() < requested)
                break;
            offset += batch.size();
        }
        return all;
    }
}
