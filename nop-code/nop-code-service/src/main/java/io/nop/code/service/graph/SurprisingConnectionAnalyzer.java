package io.nop.code.service.graph;

import io.nop.code.api.dto.SurprisingConnectionDTO;
import io.nop.code.core.graph.CodeEdgeData;
import io.nop.code.core.graph.CodeRelationGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Default {@link ISurprisingConnectionAnalyzer}: scores each edge of the typed relation view
 * with the fixed-order composite surprise score and returns the top connections with
 * per-dimension reasons. O(E) over the edge set.
 *
 * <p>Value conventions (pinned against live storage): score is int throughout; the
 * semantically_similar_to multiplier truncates via {@code (int) (score * multiplier)}; the
 * similarity check reads attrs.relationType (uppercase stored enum name), never
 * Edge.getType() which carries the edge family; missing confidence/relation attrs degrade
 * to 0/no-reason rather than fabricating signal.</p>
 */
public class SurprisingConnectionAnalyzer implements ISurprisingConnectionAnalyzer {

    public static final String SIMILAR_RELATION = "SEMANTICALLY_SIMILAR_TO";

    private final SurpriseConfig config;

    public SurprisingConnectionAnalyzer() {
        this(SurpriseConfig.defaults());
    }

    public SurprisingConnectionAnalyzer(SurpriseConfig config) {
        this.config = config;
    }

    @Override
    public List<SurprisingConnectionDTO> analyze(CodeRelationGraph graph,
                                                 Map<String, Integer> communities,
                                                 Function<String, String> nameResolver,
                                                 int topN, Integer minScore) {
        int effectiveTopN = topN > 0 ? topN : 20;
        int effectiveMinScore = minScore != null ? minScore : 1;

        // pass 1: degrees over the deduplicated projected edge set; directed=false rows count
        // 1 per endpoint. Degrees must be complete before scoring (edge-to-hub is a node property).
        Map<String, int[]> degrees = new HashMap<>();
        for (String nodeId : graph.nodeIds()) {
            for (io.nop.graph.api.Edge edge : graph.getOutEdges(nodeId)) {
                degrees.computeIfAbsent(edge.getSourceId(), k -> new int[2])[1]++;
                degrees.computeIfAbsent(edge.getTargetId(), k -> new int[2])[0]++;
            }
        }

        // pass 2: scoring
        List<SurprisingConnectionDTO> all = new ArrayList<>();

        for (String nodeId : graph.nodeIds()) {
            for (io.nop.graph.api.Edge edge : graph.getOutEdges(nodeId)) {
                Map<String, Object> attrs = edge.getAttrs();
                String relation = (String) attrs.get("relationType");
                String confidence = (String) attrs.get("confidence");
                String sourceFilePath = (String) attrs.get("sourceFilePath");
                String targetFilePath = (String) attrs.get("targetFilePath");

                int score = 0;
                List<String> reasons = new ArrayList<>();

                int bonus = config.confBonus(confidence);
                if (bonus > 0) {
                    score += bonus;
                    reasons.add("confidence:" + confidence + "(+" + bonus + ")");
                }

                // cross file kind dimension: dormant — single-source code corpus has no
                // document nodes, so sourceFileKind(u) == sourceFileKind(v) always holds

                if (sourceFilePath != null && targetFilePath != null) {
                    String srcDir = topLevelDir(sourceFilePath);
                    String tgtDir = topLevelDir(targetFilePath);
                    if (srcDir != null && !srcDir.equals(tgtDir)) {
                        score += config.getCrossDirBonus();
                        reasons.add("cross-dir:" + srcDir + "->" + tgtDir + "(+" + config.getCrossDirBonus() + ")");
                    }
                }

                Integer srcCommunity = communities.get(edge.getSourceId());
                Integer tgtCommunity = communities.get(edge.getTargetId());
                if (srcCommunity != null && tgtCommunity != null && !srcCommunity.equals(tgtCommunity)) {
                    score += config.getCrossCommunityBonus();
                    reasons.add("cross-community:" + srcCommunity + "->" + tgtCommunity
                            + "(+" + config.getCrossCommunityBonus() + ")");
                }

                if (SIMILAR_RELATION.equals(relation)) {
                    score = (int) (score * config.getSimilarMultiplier());
                    reasons.add("similar-relation(x" + config.getSimilarMultiplier() + ")");
                }

                int[] srcDeg = degrees.getOrDefault(edge.getSourceId(), new int[2]);
                int[] tgtDeg = degrees.getOrDefault(edge.getTargetId(), new int[2]);
                int srcTotal = srcDeg[0] + srcDeg[1];
                int tgtTotal = tgtDeg[0] + tgtDeg[1];
                // edge-to-hub evaluated after the multiplier, per the fixed contract order
                int minDeg = Math.min(srcTotal, tgtTotal);
                int maxDeg = Math.max(srcTotal, tgtTotal);
                if (minDeg <= config.getHubLowDegreeMax() && maxDeg >= config.getHubHighDegreeMin()) {
                    score += config.getEdgeHubBonus();
                    reasons.add("edge-to-hub(min=" + minDeg + ",max=" + maxDeg
                            + "(+" + config.getEdgeHubBonus() + "))");
                }

                if (score < effectiveMinScore) {
                    continue;
                }

                SurprisingConnectionDTO dto = new SurprisingConnectionDTO();
                dto.setSourceSymbolId(edge.getSourceId());
                dto.setTargetSymbolId(edge.getTargetId());
                dto.setSourceLabel(nameResolver.apply(edge.getSourceId()));
                dto.setTargetLabel(nameResolver.apply(edge.getTargetId()));
                dto.setSourceFilePath(sourceFilePath);
                dto.setTargetFilePath(targetFilePath);
                dto.setRelation(relation);
                dto.setConfidence(confidence);
                dto.setScore(score);
                dto.setReasons(reasons);
                all.add(dto);
            }
        }

        all.sort(Comparator
                .comparingInt(SurprisingConnectionDTO::getScore).reversed()
                .thenComparing(dto -> dto.getSourceSymbolId() + "->" + dto.getTargetSymbolId()));
        return all.size() > effectiveTopN ? new ArrayList<>(all.subList(0, effectiveTopN)) : all;
    }

    /** First path segment; null-safe for null/empty/relative-without-segment paths. */
    private static String topLevelDir(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        String normalized = filePath.replace('\\', '/');
        int slash = normalized.indexOf('/');
        return slash > 0 ? normalized.substring(0, slash) : null;
    }
}
