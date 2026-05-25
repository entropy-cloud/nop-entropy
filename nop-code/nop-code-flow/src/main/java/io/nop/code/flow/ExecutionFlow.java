package io.nop.code.flow;

import java.util.List;

public class ExecutionFlow {

    private String id;
    private String name;
    private String indexId;
    private String entryPointSymbolId;
    private String entryPointQualifiedName;
    private int depth;
    private double criticality;
    private List<String> pathNodeIds;
    private FlowStats stats;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIndexId() { return indexId; }
    public void setIndexId(String indexId) { this.indexId = indexId; }
    public String getEntryPointSymbolId() { return entryPointSymbolId; }
    public void setEntryPointSymbolId(String entryPointSymbolId) { this.entryPointSymbolId = entryPointSymbolId; }
    public String getEntryPointQualifiedName() { return entryPointQualifiedName; }
    public void setEntryPointQualifiedName(String entryPointQualifiedName) { this.entryPointQualifiedName = entryPointQualifiedName; }
    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
    public double getCriticality() { return criticality; }
    public void setCriticality(double criticality) { this.criticality = criticality; }
    public List<String> getPathNodeIds() { return pathNodeIds; }
    public void setPathNodeIds(List<String> pathNodeIds) { this.pathNodeIds = pathNodeIds; }
    public FlowStats getStats() { return stats; }
    public void setStats(FlowStats stats) { this.stats = stats; }

    public static class FlowStats {
        private int fileCount;
        private int symbolCount;
        private int maxDepth;

        public int getFileCount() { return fileCount; }
        public void setFileCount(int fileCount) { this.fileCount = fileCount; }
        public int getSymbolCount() { return symbolCount; }
        public void setSymbolCount(int symbolCount) { this.symbolCount = symbolCount; }
        public int getMaxDepth() { return maxDepth; }
        public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
    }
}
