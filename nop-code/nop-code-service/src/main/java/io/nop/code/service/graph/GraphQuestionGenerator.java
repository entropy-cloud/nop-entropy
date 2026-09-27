package io.nop.code.service.graph;

import io.nop.code.api.dto.ExplorationQuestionDTO;
import io.nop.code.core.graph.CodeRelationGraph;
import io.nop.code.core.model.CodeSymbol;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.Set;

/**
 * Default {@link IGraphQuestionGenerator}: collects the five signal families from the typed
 * relation graph plus materialized metric rows, one question per type (machine-executable
 * suggestedQuery per §3.2 templates), priority-descending, explicit no_signal when empty.
 *
 * <p>Signal contracts (pinned in plan 08): verify_inferred requires a hub node (typed-graph
 * degree >= 5) with >= 2 incident INFERRED edges; isolated_nodes candidates come from the
 * project symbol table union graph nodes (degree 0 coverage) excluding CONSTRUCTOR and
 * external ids; ambiguous_edge scans attrs.confidence for AMBIGUOUS (no producer today, so
 * production is always empty but the detection is real and auto-activates).</p>
 */
public class GraphQuestionGenerator implements IGraphQuestionGenerator {

    public static final String TYPE_NO_SIGNAL = "no_signal";
    static final int HUB_DEGREE_MIN = 5;
    static final double LOW_COHESION_MAX = 0.1;
    static final int LOW_COHESION_MIN_SIZE = 5;
    static final int BRIDGE_TOP = 5;
    static final int ISOLATED_MEMBERS_MAX = 10;
    static final int LOW_COHESION_MEMBERS_MAX = 10;

    @Override
    public List<ExplorationQuestionDTO> generate(CodeRelationGraph graph,
                                                 java.util.Collection<CodeSymbol> projectSymbols,
                                                 Map<String, Integer> communities,
                                                 Map<Integer, Integer> communitySizes,
                                                 Map<Integer, Double> communityCohesion,
                                                 List<Map.Entry<String, Double>> betweenness,
                                                 String indexId, int topN) {
        int effectiveTopN = topN > 0 ? topN : 10;
        List<ExplorationQuestionDTO> questions = new ArrayList<>();

        // ---- signal collection over the typed graph ----
        Map<String, int[]> degrees = new HashMap<>();
        Map<String, Integer> inferredByNode = new HashMap<>();
        List<String> ambiguousEdges = new ArrayList<>();
        for (String nodeId : graph.nodeIds()) {
            for (io.nop.graph.api.Edge edge : graph.getOutEdges(nodeId)) {
                degrees.computeIfAbsent(edge.getSourceId(), k -> new int[2])[1]++;
                degrees.computeIfAbsent(edge.getTargetId(), k -> new int[2])[0]++;
                Map<String, Object> attrs = edge.getAttrs();
                if ("INFERRED".equals(attrs.get("confidence"))) {
                    inferredByNode.merge(edge.getSourceId(), 1, Integer::sum);
                    inferredByNode.merge(edge.getTargetId(), 1, Integer::sum);
                }
                if ("AMBIGUOUS".equals(attrs.get("confidence"))) {
                    ambiguousEdges.add(edge.getSourceId() + "->" + edge.getTargetId());
                }
            }
        }

        Set<String> projectSymbolIds = new HashSet<>();
        Map<String, CodeSymbol> symbolById = new LinkedHashMap<>();
        if (projectSymbols != null) {
            for (CodeSymbol symbol : projectSymbols) {
                if (symbol.getId() == null) continue;
                projectSymbolIds.add(symbol.getId());
                symbolById.putIfAbsent(symbol.getId(), symbol);
            }
        }
        Function<String, String> qn = symbolId -> {
            CodeSymbol sym = symbolById.get(symbolId);
            return sym != null && sym.getQualifiedName() != null ? sym.getQualifiedName() : symbolId;
        };

        // ---- ambiguous_edge (real detection; no producer today) ----
        if (!ambiguousEdges.isEmpty()) {
            List<String> targets = ambiguousEdges.stream().map(e -> e.split("->")[0]).toList();
            ExplorationQuestionDTO q = base("ambiguous_edge", 5);
            q.setQuestion(ambiguousEdges.size() + " 条歧义边待确认(置信度 AMBIGUOUS)");
            q.setWhy("存在 " + ambiguousEdges.size() + " 条置信度为 AMBIGUOUS 的边");
            q.setTargetSymbolIds(targets);
            q.setSuggestedQuery("NopCodeSymbol__getBySymbolId(id:\"" + targets.get(0)
                    + "\", indexId:\"" + indexId + "\")");
            questions.add(q);
        }

        // ---- bridge_node (betweenness top-5; skipped when rows absent, e.g. >10000 nodes) ----
        if (betweenness != null && !betweenness.isEmpty()) {
            List<Map.Entry<String, Double>> top = betweenness.stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(BRIDGE_TOP)
                    .toList();
            String top1 = top.get(0).getKey();
            ExplorationQuestionDTO q = base("bridge_node", 5);
            q.setQuestion("桥接节点 " + qn.apply(top1) + " 的改动影响面最大(介数 " + top.get(0).getValue() + ")");
            q.setWhy("介数中心性 top-" + top.size() + " 信号");
            q.setTargetSymbolIds(top.stream().map(Map.Entry::getKey).toList());
            q.setSuggestedQuery("NopCodeSymbol__getCallHierarchy(indexId:\"" + indexId
                    + "\", qualifiedName:\"" + qn.apply(top1)
                    + "\", direction:\"both\", maxDepth:2)");
            questions.add(q);
        }

        // ---- verify_inferred (hub node with >= 2 INFERRED incident edges) ----
        List<String> verifyTargets = inferredByNode.entrySet().stream()
                .filter(e -> {
                    int[] deg = degrees.getOrDefault(e.getKey(), new int[2]);
                    return deg[0] + deg[1] >= HUB_DEGREE_MIN && e.getValue() >= 2
                            && projectSymbolIds.contains(e.getKey());
                })
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (!verifyTargets.isEmpty()) {
            ExplorationQuestionDTO q = base("verify_inferred", 4);
            q.setQuestion("枢纽节点 " + qn.apply(verifyTargets.get(0)) + " 存在推断(INFERRED)调用边,建议人工核实");
            q.setWhy("枢纽节点(度 >= " + HUB_DEGREE_MIN + ")含 >= 2 条 INFERRED 边");
            q.setTargetSymbolIds(verifyTargets);
            q.setSuggestedQuery("NopCodeSymbol__getCallHierarchy(indexId:\"" + indexId
                    + "\", qualifiedName:\"" + qn.apply(verifyTargets.get(0))
                    + "\", direction:\"outgoing\", maxDepth:1)");
            questions.add(q);
        }

        // ---- low_cohesion (cohesion < 0.1 and size >= 5) ----
        List<Integer> lowCohesionIds = communityCohesion.entrySet().stream()
                .filter(e -> {
                    Integer size = communitySizes.get(e.getKey());
                    return e.getValue() < LOW_COHESION_MAX && size != null && size >= LOW_COHESION_MIN_SIZE;
                })
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (!lowCohesionIds.isEmpty()) {
            Integer cid = lowCohesionIds.get(0);
            List<String> members = communities.entrySet().stream()
                    .filter(e -> cid.equals(e.getValue()))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            List<String> targets = members.size() > LOW_COHESION_MEMBERS_MAX
                    ? members.subList(0, LOW_COHESION_MEMBERS_MAX) : members;
            ExplorationQuestionDTO q = base("low_cohesion", 3);
            q.setQuestion("社区 " + cid + " 内聚度 " + String.format("%.2f", communityCohesion.get(cid))
                    + " 低于 0.1(规模 " + communitySizes.get(cid) + "),建议审视内聚性");
            q.setWhy("社区 " + cid + " cohesion=" + communityCohesion.get(cid) + ",规模=" + communitySizes.get(cid));
            q.setTargetSymbolIds(targets);
            q.setSuggestedQuery("NopCodeIndex__getKnowledgeGaps(indexId:\"" + indexId + "\")");
            questions.add(q);
        }

        // ---- isolated_nodes (project symbols with degree <= 1, excluding constructors) ----
        List<String> isolated = projectSymbols.stream()
                .filter(sym -> sym.getKind() != null
                        && ("METHOD".equals(sym.getKind().name()) || "FUNCTION".equals(sym.getKind().name())))
                .filter(sym -> {
                    int[] deg = degrees.getOrDefault(sym.getId(), new int[2]);
                    return deg[0] + deg[1] <= 1;
                })
                .map(CodeSymbol::getId)
                .sorted()
                .toList();
        if (!isolated.isEmpty()) {
            List<String> targets = isolated.size() > ISOLATED_MEMBERS_MAX
                    ? isolated.subList(0, ISOLATED_MEMBERS_MAX) : isolated;
            ExplorationQuestionDTO q = base("isolated_nodes", 2);
            q.setQuestion(isolated.size() + " 个符号近乎孤立(度数 <= 1),建议确认是否死代码");
            q.setWhy("度数 <= 1 的项目符号 " + isolated.size() + " 个");
            q.setTargetSymbolIds(targets);
            q.setSuggestedQuery("NopCodeSymbol__getBySymbolId(id:\"" + targets.get(0)
                    + "\", indexId:\"" + indexId + "\")");
            questions.add(q);
        }

        // priority desc, tie-break by type lexicographic
        questions.sort(Comparator
                .comparingInt(ExplorationQuestionDTO::getPriority).reversed()
                .thenComparing(ExplorationQuestionDTO::getType));
        if (questions.size() > effectiveTopN) {
            questions = new ArrayList<>(questions.subList(0, effectiveTopN));
        }
        if (questions.isEmpty()) {
            ExplorationQuestionDTO noSignal = new ExplorationQuestionDTO();
            noSignal.setType(TYPE_NO_SIGNAL);
            noSignal.setTargetSymbolIds(new ArrayList<>());
            noSignal.setPriority(0);
            return List.of(noSignal);
        }
        return questions;
    }

    private static ExplorationQuestionDTO base(String type, int priority) {
        ExplorationQuestionDTO dto = new ExplorationQuestionDTO();
        dto.setType(type);
        dto.setPriority(priority);
        dto.setTargetSymbolIds(new ArrayList<>());
        return dto;
    }
}
