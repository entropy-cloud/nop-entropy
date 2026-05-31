package io.nop.code.graph.critical;

import java.util.List;
public class CriticalNodeResult {
    private List<NodeScore> hubNodes;
    private List<NodeScore> bridgeNodes;
    private int totalNodes;
    private int topN;

    public List<NodeScore> getHubNodes() {
        return hubNodes != null ? hubNodes : List.of();
    }

    public void setHubNodes(List<NodeScore> hubNodes) {
        this.hubNodes = hubNodes;
    }

    public List<NodeScore> getBridgeNodes() {
        return bridgeNodes != null ? bridgeNodes : List.of();
    }

    public void setBridgeNodes(List<NodeScore> bridgeNodes) {
        this.bridgeNodes = bridgeNodes;
    }

    public int getTotalNodes() {
        return totalNodes;
    }

    public void setTotalNodes(int totalNodes) {
        this.totalNodes = totalNodes;
    }

    public int getTopN() {
        return topN;
    }

    public void setTopN(int topN) {
        this.topN = topN;
    }

    public static class NodeScore {
        private String symbolId;
        private String qualifiedName;
        private double score;
        private int inDegree;
        private int outDegree;
        private int totalDegree;

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

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public int getInDegree() {
            return inDegree;
        }

        public void setInDegree(int inDegree) {
            this.inDegree = inDegree;
        }

        public int getOutDegree() {
            return outDegree;
        }

        public void setOutDegree(int outDegree) {
            this.outDegree = outDegree;
        }

        public int getTotalDegree() {
            return totalDegree;
        }

        public void setTotalDegree(int totalDegree) {
            this.totalDegree = totalDegree;
        }
    }
}
