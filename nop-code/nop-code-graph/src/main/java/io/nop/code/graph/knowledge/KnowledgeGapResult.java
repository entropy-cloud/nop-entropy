package io.nop.code.graph.knowledge;

import java.util.List;
public class KnowledgeGapResult {
    private List<IsolatedSymbol> isolatedSymbols;
    private List<WeakCommunity> weakCommunities;

    public List<IsolatedSymbol> getIsolatedSymbols() {
        return isolatedSymbols != null ? isolatedSymbols : List.of();
    }

    public void setIsolatedSymbols(List<IsolatedSymbol> isolatedSymbols) {
        this.isolatedSymbols = isolatedSymbols;
    }

    public List<WeakCommunity> getWeakCommunities() {
        return weakCommunities != null ? weakCommunities : List.of();
    }

    public void setWeakCommunities(List<WeakCommunity> weakCommunities) {
        this.weakCommunities = weakCommunities;
    }

    public static class IsolatedSymbol {
        private String symbolId;
        private String qualifiedName;
        private String name;
        private String kind;

        public String getSymbolId() {
            return symbolId;
        }

        public void setSymbolId(String symbolId) {
            this.symbolId = symbolId;
        }

        public String getQualifiedName() {
            return qualifiedName;
        }

        public void setQualifiedName(String qualifiedName) {
            this.qualifiedName = qualifiedName;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }
    }

    public static class WeakCommunity {
        private String communityId;
        private String label;
        private int symbolCount;
        private double cohesion;
        private double threshold;

        public String getCommunityId() {
            return communityId;
        }

        public void setCommunityId(String communityId) {
            this.communityId = communityId;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public int getSymbolCount() {
            return symbolCount;
        }

        public void setSymbolCount(int symbolCount) {
            this.symbolCount = symbolCount;
        }

        public double getCohesion() {
            return cohesion;
        }

        public void setCohesion(double cohesion) {
            this.cohesion = cohesion;
        }

        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double threshold) {
            this.threshold = threshold;
        }
    }
}
