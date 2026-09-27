package io.nop.code.service.graph;

import io.nop.code.api.dto.SurprisingConnectionDTO;
import io.nop.code.core.graph.CodeRelationGraph;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Scores the typed relation graph for "surprising" (non-obvious) connections
 * (graph-discovery-and-export-design.md §3.1).
 *
 * <p>Degradation contract (§3.0): a dimension whose prerequisite is missing contributes 0
 * and no reason — missing community membership for an endpoint, missing filePath attrs, or
 * missing confidence never fabricate signal.</p>
 */
public interface ISurprisingConnectionAnalyzer {

    /**
     * @param graph          typed edge view (CALLS/INHERITANCE/ANNOTATION/SEMANTIC)
     * @param communities    symbolId -> communityId; may be empty (cross-community dimension skipped)
     * @param nameResolver   symbolId -> qualified name (label); miss falls back to the raw id
     * @param topN           max connections returned
     * @param minScore       minimum score; edges below are dropped
     */
    List<SurprisingConnectionDTO> analyze(CodeRelationGraph graph,
                                          Map<String, Integer> communities,
                                          Function<String, String> nameResolver,
                                          int topN, Integer minScore);
}
