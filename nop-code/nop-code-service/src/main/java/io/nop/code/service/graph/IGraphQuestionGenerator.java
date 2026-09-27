package io.nop.code.service.graph;

import io.nop.code.api.dto.ExplorationQuestionDTO;
import io.nop.code.core.graph.CodeRelationGraph;
import io.nop.code.core.model.CodeSymbol;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Derives machine-executable exploration questions from graph signals
 * (graph-discovery-and-export-design.md §3.2).
 *
 * <p>One question per type (max 5). When no signal exists the generator returns a single
 * explicit no_signal item (type="no_signal", question=null) — the documented exception to
 * the empty-list convention.</p>
 */
public interface IGraphQuestionGenerator {

    /**
     * @param graph            typed edge view (CALLS/INHERITANCE/ANNOTATION/SEMANTIC)
     * @param projectSymbols   all project symbols (isolated candidate set; degree-0 coverage)
     * @param communities      symbolId -> communityId (may be empty: low_cohesion skipped)
     * @param communitySizes   communityId -> member count
     * @param communityCohesion communityId -> cohesion
     * @param betweenness      betweenness top list in rank order (empty on large graphs: bridge skipped)
     * @param indexId          index id for suggestedQuery templates
     * @param topN             max questions returned (default 10, <=0 normalized)
     */
    List<ExplorationQuestionDTO> generate(CodeRelationGraph graph,
                                          Collection<CodeSymbol> projectSymbols,
                                          Map<String, Integer> communities,
                                          Map<Integer, Integer> communitySizes,
                                          Map<Integer, Double> communityCohesion,
                                          List<Map.Entry<String, Double>> betweenness,
                                          String indexId, int topN);
}
