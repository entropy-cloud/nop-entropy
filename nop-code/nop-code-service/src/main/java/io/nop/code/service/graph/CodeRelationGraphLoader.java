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
 * Loads all four relation tables (calls / inheritance / annotation usages / semantic edges)
 * in batches and maps them to {@link CodeEdgeData}. Deliberately unbounded: global scoring
 * (surprise connections, question generation) needs the complete edge set of an index; memory
 * guards are the domain of result materialization and the invariant-loop gates.
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

    public static CodeRelationGraph load(String indexId, IDaoProvider daoProvider,
                                          Function<String, String> filePathResolver) {
        List<CodeEdgeData> edges = new ArrayList<>();
        edges.addAll(loadCalls(indexId, daoProvider, filePathResolver));
        edges.addAll(loadInheritances(indexId, daoProvider, filePathResolver));
        edges.addAll(loadAnnotationUsages(indexId, daoProvider, filePathResolver));
        edges.addAll(loadSemanticEdges(indexId, daoProvider, filePathResolver));
        LOG.info("nop.code.relation-graph-loaded:indexId={},edges={}", indexId, edges.size());
        return new CodeRelationGraph(edges);
    }

    static List<CodeEdgeData> loadCalls(String indexId, IDaoProvider daoProvider,
                                        Function<String, String> filePathResolver) {
        IEntityDao<NopCodeCall> dao = daoProvider.daoFor(NopCodeCall.class);
        List<CodeEdgeData> result = new ArrayList<>();
        for (NopCodeCall call : batched(dao, indexId)) {
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
        IEntityDao<NopCodeInheritance> dao = daoProvider.daoFor(NopCodeInheritance.class);
        List<CodeEdgeData> result = new ArrayList<>();
        for (NopCodeInheritance inh : batched(dao, indexId)) {
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
        IEntityDao<NopCodeAnnotationUsage> dao = daoProvider.daoFor(NopCodeAnnotationUsage.class);
        List<CodeEdgeData> result = new ArrayList<>();
        for (NopCodeAnnotationUsage usage : batched(dao, indexId)) {
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
        IEntityDao<NopCodeSemanticEdge> dao = daoProvider.daoFor(NopCodeSemanticEdge.class);
        List<CodeEdgeData> result = new ArrayList<>();
        for (NopCodeSemanticEdge semanticEdge : batched(dao, indexId)) {
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
        List<T> all = new ArrayList<>();
        long offset = 0;
        while (true) {
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq("indexId", indexId));
            query.setOffset(offset);
            query.setLimit(BATCH_SIZE);
            List<T> batch = dao.findPageByQuery(query);
            if (batch.isEmpty())
                break;
            all.addAll(batch);
            if (batch.size() < BATCH_SIZE)
                break;
            offset += BATCH_SIZE;
        }
        return all;
    }
}
