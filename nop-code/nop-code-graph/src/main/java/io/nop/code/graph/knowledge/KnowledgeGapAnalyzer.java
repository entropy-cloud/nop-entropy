package io.nop.code.graph.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.graph.community.CommunityDetector;
public class KnowledgeGapAnalyzer {

    private double weakCommunityCohesionThreshold = 0.1;

    public KnowledgeGapResult analyze(CallGraph callGraph, SymbolTable symbolTable,
                                      CommunityDetector.CommunityDetectionResult communities) {
        if (callGraph == null || symbolTable == null) {
            KnowledgeGapResult result = new KnowledgeGapResult();
            result.setIsolatedSymbols(Collections.emptyList());
            result.setWeakCommunities(Collections.emptyList());
            return result;
        }
        KnowledgeGapResult result = new KnowledgeGapResult();
        result.setIsolatedSymbols(detectIsolatedNodes(callGraph, symbolTable));
        result.setWeakCommunities(detectWeakCommunities(callGraph, communities));
        return result;
    }

    private List<KnowledgeGapResult.IsolatedSymbol> detectIsolatedNodes(
            CallGraph callGraph, SymbolTable symbolTable) {
        List<KnowledgeGapResult.IsolatedSymbol> isolated = new ArrayList<>();

        for (CodeSymbol symbol : symbolTable.getAll()) {
            String id = symbol.getId();
            if (id == null) continue;

            List<String> callees = callGraph.getCallees(id);
            List<String> callers = callGraph.getCallers(id);
            if ((callees == null || callees.isEmpty()) && (callers == null || callers.isEmpty())) {
                KnowledgeGapResult.IsolatedSymbol iso = new KnowledgeGapResult.IsolatedSymbol();
                iso.setSymbolId(id);
                iso.setQualifiedName(symbol.getQualifiedName());
                iso.setName(symbol.getName());
                iso.setKind(symbol.getKind() != null ? symbol.getKind().name() : null);
                isolated.add(iso);
            }
        }
        return isolated;
    }

    private List<KnowledgeGapResult.WeakCommunity> detectWeakCommunities(
            CallGraph callGraph, CommunityDetector.CommunityDetectionResult communities) {
        if (communities == null || communities.getCommunities() == null) {
            return Collections.emptyList();
        }

        List<KnowledgeGapResult.WeakCommunity> weak = new ArrayList<>();
        for (CommunityDetector.Community community : communities.getCommunities()) {
            double cohesion = computeCohesion(community.getSymbolIds(), callGraph);
            if (cohesion < weakCommunityCohesionThreshold) {
                KnowledgeGapResult.WeakCommunity wc = new KnowledgeGapResult.WeakCommunity();
                wc.setCommunityId(community.getId());
                wc.setLabel(community.getLabel());
                wc.setSymbolCount(community.getSymbolCount());
                wc.setCohesion(cohesion);
                wc.setThreshold(weakCommunityCohesionThreshold);
                weak.add(wc);
            }
        }
        return weak;
    }

    private double computeCohesion(List<String> symbolIds, CallGraph callGraph) {
        if (symbolIds == null || symbolIds.size() < 2) return 1.0;

        Set<String> memberSet = new HashSet<>(symbolIds);
        int internalEdges = 0;
        int externalEdges = 0;
        Set<String> countedEdges = new HashSet<>();

        for (String node : memberSet) {
            List<String> callees = callGraph.getCallees(node);
            for (String callee : callees) {
                String edgeKey = node + "->" + callee;
                if (countedEdges.add(edgeKey)) {
                    if (memberSet.contains(callee)) {
                        internalEdges++;
                    } else {
                        externalEdges++;
                    }
                }
            }
            List<String> callers = callGraph.getCallers(node);
            for (String caller : callers) {
                String edgeKey = caller + "->" + node;
                if (countedEdges.add(edgeKey)) {
                    if (memberSet.contains(caller)) {
                        internalEdges++;
                    } else {
                        externalEdges++;
                    }
                }
            }
        }

        int total = internalEdges + externalEdges;
        return total == 0 ? 1.0 : (double) internalEdges / total;
    }

    public double getWeakCommunityCohesionThreshold() {
        return weakCommunityCohesionThreshold;
    }

    public void setWeakCommunityCohesionThreshold(double threshold) {
        this.weakCommunityCohesionThreshold = threshold;
    }
}
