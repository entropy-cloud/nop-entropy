package io.nop.code.service.graph;

import io.nop.code.api.dto.GraphWikiDTO;
import io.nop.code.core.graph.CodeRelationGraph;
import io.nop.graph.api.Edge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Default {@link IGraphWikiExporter}: renders index.md plus one article per exported
 * community and per exported hub node, all deterministic (slug rules, tie-breaks by
 * symbolId/communityId lexicographic where unspecified) and capped.
 *
 * <p>DTO semantics (adjudicated): dto.index = the index.md body; articles holds only the
 * community/hub articles. Cross-community edges are listed in the source-side article only
 * when both endpoints have community membership; endpoint-missing edges are omitted.
 * Hubs use the wiki's own node-level threshold HUB_MIN_DEGREE=5 on typed-graph degree
 * (the §3.1 max(deg)>=5 rule is a per-edge criterion and does not apply here).</p>
 */
public class GraphWikiExporter implements IGraphWikiExporter {

    public static final int HUB_MIN_DEGREE = 5;
    private static final int SLUG_MAX_LENGTH = 64;
    private static final int DEFAULT_CAP = 20;

    @Override
    public GraphWikiDTO exportWiki(CodeRelationGraph graph,
                                   Map<String, Integer> communities,
                                   Map<Integer, Double> communityCohesion,
                                   Function<String, String> nameResolver,
                                   Function<String, String> filePathResolver,
                                   int maxCommunities, int maxHubNodes) {
        int effectiveMaxCommunities = maxCommunities > 0 ? maxCommunities : DEFAULT_CAP;
        int effectiveMaxHubs = maxHubNodes > 0 ? maxHubNodes : DEFAULT_CAP;

        // pass 1: degrees over the projected edge set
        Map<String, int[]> degrees = new HashMap<>();
        int edgeCount = 0;
        for (String nodeId : graph.nodeIds()) {
            for (Edge edge : graph.getOutEdges(nodeId)) {
                degrees.computeIfAbsent(edge.getSourceId(), k -> new int[2])[1]++;
                degrees.computeIfAbsent(edge.getTargetId(), k -> new int[2])[0]++;
                edgeCount++;
            }
        }
        Set<String> nodeIds = graph.nodeIds();

        // group members by community
        Map<Integer, List<String>> membersByCommunity = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : communities.entrySet()) {
            membersByCommunity.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        // communities by size desc, communityId asc; hubs by degree desc, symbolId asc
        List<Integer> communityOrder = new ArrayList<>(membersByCommunity.keySet());
        communityOrder.sort(Comparator
                .comparingInt((Integer id) -> membersByCommunity.get(id).size()).reversed()
                .thenComparing(id -> id));
        List<String> hubOrder = nodeIds.stream()
                .filter(id -> {
                    // external ids (name-resolution misses, e.g. unresolved super types) are
                    // not project symbols and do not qualify as hubs
                    String label = nameResolver.apply(id);
                    return label != null && !label.equals(id);
                })
                .filter(id -> {
                    int[] deg = degrees.getOrDefault(id, new int[2]);
                    return deg[0] + deg[1] >= HUB_MIN_DEGREE;
                })
                .sorted(Comparator
                        .comparingInt((String id) -> degrees.getOrDefault(id, new int[2])[0]
                                + degrees.getOrDefault(id, new int[2])[1]).reversed()
                        .thenComparing(id -> id))
                .toList();

        int exportedCommunities = Math.min(effectiveMaxCommunities, communityOrder.size());
        int exportedHubs = Math.min(effectiveMaxHubs, hubOrder.size());

        Map<String, String> articles = new LinkedHashMap<>();
        List<String> communityIndexRows = new ArrayList<>();
        List<String> hubIndexRows = new ArrayList<>();

        for (int i = 0; i < exportedCommunities; i++) {
            Integer communityId = communityOrder.get(i);
            List<String> members = membersByCommunity.get(communityId);
            String label = communityLabel(members, nameResolver, communityId);
            String slug = uniqueSlug("community-" + slug(label), articles.keySet());

            StringBuilder body = new StringBuilder();
            body.append("# 社区 ").append(communityId).append("(").append(label).append(")\n\n");
            body.append("内聚度:").append(communityCohesion.getOrDefault(communityId, 0.0)).append("\n\n");
            body.append("## 关键概念(按度排序)\n\n");
            members.sort(Comparator
                    .comparingInt((String id) -> degrees.getOrDefault(id, new int[2])[0]
                            + degrees.getOrDefault(id, new int[2])[1]).reversed()
                    .thenComparing(id -> id));
            for (String memberId : members) {
                int[] deg = degrees.getOrDefault(memberId, new int[2]);
                body.append("- ").append(nameResolver.apply(memberId))
                        .append("(度 ").append(deg[0] + deg[1]).append(")\n");
            }

            body.append("\n## 跨社区关系\n\n");
            boolean hasCross = false;
            for (String nodeId : members) {
                for (Edge edge : graph.getOutEdges(nodeId)) {
                    Integer srcCommunity = communities.get(edge.getSourceId());
                    Integer tgtCommunity = communities.get(edge.getTargetId());
                    if (srcCommunity == null || tgtCommunity == null || srcCommunity.equals(tgtCommunity)) {
                        continue;
                    }
                    hasCross = true;
                    body.append("- ").append(nameResolver.apply(edge.getSourceId())).append(" -> ")
                            .append(nameResolver.apply(edge.getTargetId()))
                            .append(" [").append(edge.getType()).append(", confidence=")
                            .append(attrsString(edge)).append("] -> 社区 ").append(tgtCommunity).append("\n");
                }
            }
            if (!hasCross) {
                body.append("(无)\n");
            }

            body.append("\n## 源文件\n\n");
            Set<String> files = new java.util.LinkedHashSet<>();
            for (String memberId : members) {
                String file = filePathResolver.apply(memberId);
                if (file != null) {
                    files.add(file);
                }
            }
            if (files.isEmpty()) {
                body.append("(无)\n");
            } else {
                for (String file : files) {
                    body.append("- ").append(file).append("\n");
                }
            }

            articles.put(slug + ".md", body.toString());
            communityIndexRows.add("- [" + label + "](" + slug + ".md) 规模 " + members.size()
                    + " 内聚度 " + communityCohesion.getOrDefault(communityId, 0.0));
        }

        for (int i = 0; i < exportedHubs; i++) {
            String hubId = hubOrder.get(i);
            String label = nameResolver.apply(hubId);
            String slug = uniqueSlug("hub-" + slug(label), articles.keySet());

            StringBuilder body = new StringBuilder();
            int[] deg = degrees.getOrDefault(hubId, new int[2]);
            body.append("# 枢纽 ").append(label).append("\n\n");
            body.append("度 ").append(deg[0] + deg[1]).append("(入 ").append(deg[0])
                    .append(" / 出 ").append(deg[1]).append(")\n\n");

            Map<String, List<String>> neighborsByType = new TreeMap<>();
            for (Edge edge : graph.getOutEdges(hubId)) {
                neighborsByType.computeIfAbsent(String.valueOf(edge.getType()), k -> new ArrayList<>())
                        .add(nameResolver.apply(edge.getTargetId()) + " (confidence=" + attrsString(edge) + ")");
            }
            for (Edge edge : graph.getInEdges(hubId)) {
                neighborsByType.computeIfAbsent(String.valueOf(edge.getType()), k -> new ArrayList<>())
                        .add(nameResolver.apply(edge.getSourceId()) + " (confidence=" + attrsString(edge) + ")");
            }
            body.append("## 邻居(按关系类型)\n\n");
            for (Map.Entry<String, List<String>> entry : neighborsByType.entrySet()) {
                body.append("### ").append(entry.getKey()).append("\n\n");
                for (String neighbor : entry.getValue()) {
                    body.append("- ").append(neighbor).append("\n");
                }
            }

            articles.put(slug + ".md", body.toString());
            hubIndexRows.add("- [" + label + "](" + slug + ".md) 度 " + (deg[0] + deg[1]));
        }

        StringBuilder index = new StringBuilder();
        index.append("# 代码图谱 Wiki\n\n");
        index.append("- 节点数:").append(nodeIds.size()).append("\n");
        index.append("- 边数:").append(edgeCount).append("\n");
        index.append("- 社区数:").append(communityOrder.size()).append("(导出 ").append(exportedCommunities).append(")\n");
        index.append("- 枢纽数:").append(hubOrder.size()).append("(导出 ").append(exportedHubs).append(")\n\n");
        index.append("## 社区\n\n");
        if (communityIndexRows.isEmpty()) {
            index.append("(无)\n");
        } else {
            communityIndexRows.forEach(row -> index.append(row).append('\n'));
        }
        index.append("\n## 枢纽节点\n\n");
        if (hubIndexRows.isEmpty()) {
            index.append("(无)\n");
        } else {
            hubIndexRows.forEach(row -> index.append(row).append('\n'));
        }

        GraphWikiDTO dto = new GraphWikiDTO();
        dto.setIndex(index.toString());
        dto.setArticles(articles);
        return dto;
    }

    private String communityLabel(List<String> members, Function<String, String> nameResolver,
                                  Integer communityId) {
        Map<String, Integer> packageCounts = new HashMap<>();
        for (String memberId : members) {
            String qn = nameResolver.apply(memberId);
            if (qn == null || qn.equals(memberId)) continue; // unresolved external
            int dot = qn.lastIndexOf('.');
            String pkg = dot > 0 ? qn.substring(0, dot) : qn;
            packageCounts.merge(pkg, 1, Integer::sum);
        }
        return packageCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("community-" + communityId);
    }

    static String slug(String label) {
        String lowered = label.toLowerCase();
        StringBuilder sb = new StringBuilder();
        boolean lastDash = true; // avoid leading dash
        for (char c : lowered.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-') {
                sb.append(c);
                lastDash = false;
            } else if (!lastDash) {
                sb.append('-');
                lastDash = true;
            }
        }
        String result = sb.toString();
        while (result.endsWith("-")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.length() > SLUG_MAX_LENGTH) {
            result = result.substring(0, SLUG_MAX_LENGTH);
        }
        while (result.endsWith("-")) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? "article" : result;
    }

    private static String uniqueSlug(String base, Set<String> usedKeys) {
        String candidate = base;
        int seq = 2;
        while (usedKeys.contains(candidate + ".md")) {
            candidate = base + "-" + seq;
            seq++;
        }
        return candidate;
    }

    private static String attrsString(Edge edge) {
        Object confidence = edge.getAttrs().get("confidence");
        return confidence != null ? confidence.toString() : "n/a";
    }
}
