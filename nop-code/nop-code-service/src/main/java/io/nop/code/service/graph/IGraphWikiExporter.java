package io.nop.code.service.graph;

import io.nop.code.api.dto.GraphWikiDTO;
import io.nop.code.core.graph.CodeRelationGraph;

import java.util.Map;
import java.util.function.Function;

/**
 * Renders the typed relation graph as an interlinked Markdown article set
 * (graph-discovery-and-export-design.md §3.3). Not Obsidian syntax; nothing is written
 * to disk — the caller receives a {@link GraphWikiDTO}.
 */
public interface IGraphWikiExporter {

    /**
     * @param graph            typed edge view
     * @param communities      symbolId -> communityId (empty = no community articles)
     * @param communityCohesion communityId -> cohesion (shown in index.md community rows)
     * @param nameResolver     symbolId -> qualified name (labels; miss falls back to raw id)
     * @param filePathResolver symbolId -> file path (community source-file lists; miss skipped)
     * @param maxCommunities   cap on community articles (<=0 normalized to 20)
     * @param maxHubNodes      cap on hub articles (<=0 normalized to 20)
     */
    GraphWikiDTO exportWiki(CodeRelationGraph graph,
                            Map<String, Integer> communities,
                            Map<Integer, Double> communityCohesion,
                            Function<String, String> nameResolver,
                            Function<String, String> filePathResolver,
                            int maxCommunities, int maxHubNodes);
}
