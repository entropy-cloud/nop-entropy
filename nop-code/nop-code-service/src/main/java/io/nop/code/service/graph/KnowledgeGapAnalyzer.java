package io.nop.code.service.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.graph.api.CommunityInfo;
import io.nop.graph.api.CommunityResult;

public class KnowledgeGapAnalyzer {

    private double weakCommunityCohesionThreshold = 0.1;

    public KnowledgeGapResult analyze(CallGraph callGraph, SymbolTable symbolTable,
                                      CommunityResult communities) {
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
            CallGraph callGraph, CommunityResult communities) {
        if (communities == null || communities.getCommunities() == null) {
            return Collections.emptyList();
        }

        List<KnowledgeGapResult.WeakCommunity> weak = new ArrayList<>();
        for (CommunityInfo community : communities.getCommunities()) {
            double cohesion = community.getCohesion();
            if (cohesion < weakCommunityCohesionThreshold) {
                KnowledgeGapResult.WeakCommunity wc = new KnowledgeGapResult.WeakCommunity();
                wc.setCommunityId(String.valueOf(community.getId()));
                wc.setLabel("community_" + community.getId());
                wc.setSymbolCount(community.getNodeCount());
                wc.setCohesion(cohesion);
                wc.setThreshold(weakCommunityCohesionThreshold);
                weak.add(wc);
            }
        }
        return weak;
    }

    public double getWeakCommunityCohesionThreshold() {
        return weakCommunityCohesionThreshold;
    }

    public void setWeakCommunityCohesionThreshold(double threshold) {
        this.weakCommunityCohesionThreshold = threshold;
    }
}
