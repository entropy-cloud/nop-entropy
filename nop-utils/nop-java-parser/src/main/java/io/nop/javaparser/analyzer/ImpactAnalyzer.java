package io.nop.javaparser.analyzer;

import java.util.*;

/**
 * 影响范围分析器
 * 
 * 分析代码变更的影响范围，支持上游（调用者）和下游（被调用者）分析
 */
public class ImpactAnalyzer {
    
    /**
     * 影响分析结果
     */
    public static class ImpactResult {
        private String targetSymbolId;
        private String targetQualifiedName;
        private List<ImpactedSymbol> upstream;   // 调用者（受影响）
        private List<ImpactedSymbol> downstream; // 被调用者（可能需要关注）
        private String riskLevel;
        
        public String getTargetSymbolId() {
            return targetSymbolId;
        }
        
        public void setTargetSymbolId(String targetSymbolId) {
            this.targetSymbolId = targetSymbolId;
        }
        
        public String getTargetQualifiedName() {
            return targetQualifiedName;
        }
        
        public void setTargetQualifiedName(String targetQualifiedName) {
            this.targetQualifiedName = targetQualifiedName;
        }
        
        public List<ImpactedSymbol> getUpstream() {
            return upstream != null ? upstream : Collections.emptyList();
        }
        
        public void setUpstream(List<ImpactedSymbol> upstream) {
            this.upstream = upstream;
        }
        
        public List<ImpactedSymbol> getDownstream() {
            return downstream != null ? downstream : Collections.emptyList();
        }
        
        public void setDownstream(List<ImpactedSymbol> downstream) {
            this.downstream = downstream;
        }
        
        public String getRiskLevel() {
            return riskLevel;
        }
        
        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }
        
        public int getTotalImpacted() {
            return getUpstream().size() + getDownstream().size();
        }
        
        public int getMaxDepth() {
            int maxUpstream = getUpstream().stream()
                    .mapToInt(ImpactedSymbol::getDepth)
                    .max()
                    .orElse(0);
            int maxDownstream = getDownstream().stream()
                    .mapToInt(ImpactedSymbol::getDepth)
                    .max()
                    .orElse(0);
            return Math.max(maxUpstream, maxDownstream);
        }
        
        @Override
        public String toString() {
            return String.format("ImpactResult{target=%s, upstream=%d, downstream=%d, risk=%s, maxDepth=%d}",
                    targetQualifiedName, getUpstream().size(), getDownstream().size(), riskLevel, getMaxDepth());
        }
    }
    
    /**
     * 被影响的符号
     */
    public static class ImpactedSymbol {
        private String symbolId;
        private String qualifiedName;
        private String name;
        private SymbolKind kind;
        private int depth;
        private String filePath;
        
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
        
        public SymbolKind getKind() {
            return kind;
        }
        
        public void setKind(SymbolKind kind) {
            this.kind = kind;
        }
        
        public int getDepth() {
            return depth;
        }
        
        public void setDepth(int depth) {
            this.depth = depth;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
        
        @Override
        public String toString() {
            return String.format("ImpactedSymbol{depth=%d, %s}", depth, qualifiedName);
        }
    }
    
    /**
     * 风险等级
     */
    public enum RiskLevel {
        LOW,      // 影响范围小
        MEDIUM,   // 影响范围中等
        HIGH,     // 影响范围大
        CRITICAL  // 影响范围非常大
    }
    
    /**
     * 分析配置
     */
    public static class ImpactConfig {
        private int maxDepth = 3;
        private int maxNodes = 100;
        
        public int getMaxDepth() {
            return maxDepth;
        }
        
        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }
        
        public int getMaxNodes() {
            return maxNodes;
        }
        
        public void setMaxNodes(int maxNodes) {
            this.maxNodes = maxNodes;
        }
        
        public static ImpactConfig defaultConfig() {
            return new ImpactConfig();
        }
    }
    
    /**
     * 分析指定符号的影响范围
     * 
     * @param targetQualifiedName 目标符号的全限定名
     * @param callGraph 调用图 (callerId -> [calleeId])
     * @param reverseCallGraph 反向调用图 (calleeId -> [callerId])
     * @param symbolTable 符号表 (symbolId -> SymbolInfo)
     * @param maxDepth 最大分析深度
     * @return 影响分析结果
     */
    public static ImpactResult analyzeImpact(
            String targetQualifiedName,
            Map<String, List<String>> callGraph,
            Map<String, List<String>> reverseCallGraph,
            Map<String, SymbolInfo> symbolTable,
            int maxDepth) {
        
        ImpactConfig config = new ImpactConfig();
        config.setMaxDepth(maxDepth);
        return analyzeImpact(targetQualifiedName, callGraph, reverseCallGraph, symbolTable, config);
    }
    
    /**
     * 分析指定符号的影响范围（带配置）
     */
    public static ImpactResult analyzeImpact(
            String targetQualifiedName,
            Map<String, List<String>> callGraph,
            Map<String, List<String>> reverseCallGraph,
            Map<String, SymbolInfo> symbolTable,
            ImpactConfig config) {
        
        // 1. 查找目标符号
        SymbolInfo targetSymbol = findSymbolByQualifiedName(symbolTable, targetQualifiedName);
        if (targetSymbol == null) {
            ImpactResult result = new ImpactResult();
            result.setTargetQualifiedName(targetQualifiedName);
            result.setRiskLevel("not-found");
            return result;
        }
        
        String targetId = targetSymbol.getId();
        
        // 2. 分析上游影响（调用者）
        List<ImpactedSymbol> upstream = traceImpact(
                targetId, reverseCallGraph, symbolTable, config.getMaxDepth(), config.getMaxNodes()
        );
        
        // 3. 分析下游影响（被调用者）
        List<ImpactedSymbol> downstream = traceImpact(
                targetId, callGraph, symbolTable, config.getMaxDepth(), config.getMaxNodes()
        );
        
        // 4. 评估风险等级
        String riskLevel = evaluateRisk(upstream, downstream);
        
        // 5. 构建结果
        ImpactResult result = new ImpactResult();
        result.setTargetSymbolId(targetId);
        result.setTargetQualifiedName(targetQualifiedName);
        result.setUpstream(upstream);
        result.setDownstream(downstream);
        result.setRiskLevel(riskLevel);
        
        return result;
    }
    
    /**
     * BFS 追踪影响范围
     */
    private static List<ImpactedSymbol> traceImpact(
            String startId,
            Map<String, List<String>> graph,
            Map<String, SymbolInfo> symbolTable,
            int maxDepth,
            int maxNodes) {
        
        List<ImpactedSymbol> impacted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String[]> queue = new LinkedList<>();
        
        queue.add(new String[]{startId, "0"});
        visited.add(startId);
        
        while (!queue.isEmpty() && impacted.size() < maxNodes) {
            String[] current = queue.poll();
            String nodeId = current[0];
            int depth = Integer.parseInt(current[1]);
            
            if (depth >= maxDepth) continue;
            
            List<String> neighbors = graph.getOrDefault(nodeId, Collections.emptyList());
            for (String neighborId : neighbors) {
                if (!visited.contains(neighborId)) {
                    visited.add(neighborId);
                    
                    SymbolInfo symbol = symbolTable.get(neighborId);
                    if (symbol != null) {
                        ImpactedSymbol is = new ImpactedSymbol();
                        is.setSymbolId(neighborId);
                        is.setQualifiedName(symbol.getQualifiedName());
                        is.setName(symbol.getName());
                        is.setKind(symbol.getKind());
                        is.setDepth(depth + 1);
                        is.setFilePath(extractFilePath(symbol));
                        impacted.add(is);
                        
                        if (impacted.size() >= maxNodes) break;
                    }
                    
                    queue.add(new String[]{neighborId, String.valueOf(depth + 1)});
                }
            }
        }
        
        return impacted;
    }
    
    /**
     * 评估风险等级
     */
    private static String evaluateRisk(List<ImpactedSymbol> upstream, List<ImpactedSymbol> downstream) {
        int totalImpacted = upstream.size() + downstream.size();
        int maxDepth = Math.max(
                upstream.stream().mapToInt(ImpactedSymbol::getDepth).max().orElse(0),
                downstream.stream().mapToInt(ImpactedSymbol::getDepth).max().orElse(0)
        );
        
        if (totalImpacted > 50 || maxDepth > 5) return "critical";
        if (totalImpacted > 20 || maxDepth > 3) return "high";
        if (totalImpacted > 5) return "medium";
        return "low";
    }
    
    /**
     * 按全限定名查找符号
     */
    private static SymbolInfo findSymbolByQualifiedName(
            Map<String, SymbolInfo> symbolTable, 
            String qualifiedName) {
        
        // 精确匹配
        for (SymbolInfo symbol : symbolTable.values()) {
            if (qualifiedName.equals(symbol.getQualifiedName())) {
                return symbol;
            }
        }
        
        // 模糊匹配（处理方法重载）
        int parenIndex = qualifiedName.indexOf('(');
        if (parenIndex > 0) {
            String withoutParams = qualifiedName.substring(0, parenIndex);
            for (SymbolInfo symbol : symbolTable.values()) {
                if (symbol.getQualifiedName() != null && 
                    symbol.getQualifiedName().startsWith(withoutParams)) {
                    return symbol;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 提取文件路径
     */
    private static String extractFilePath(SymbolInfo symbol) {
        // SymbolInfo 目前没有 filePath，可以从 declaringSymbolId 追溯
        return null;
    }
    
    /**
     * 按深度分组
     */
    public static Map<Integer, List<ImpactedSymbol>> groupByDepth(List<ImpactedSymbol> symbols) {
        Map<Integer, List<ImpactedSymbol>> grouped = new TreeMap<>();
        for (ImpactedSymbol symbol : symbols) {
            grouped.computeIfAbsent(symbol.getDepth(), k -> new ArrayList<>()).add(symbol);
        }
        return grouped;
    }
    
    /**
     * 打印影响分析结果
     */
    public static String printResult(ImpactResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Impact Analysis ===\n");
        sb.append(String.format("Target: %s\n", result.getTargetQualifiedName()));
        sb.append(String.format("Risk Level: %s\n\n", result.getRiskLevel()));
        
        sb.append(String.format("Upstream (Callers): %d\n", result.getUpstream().size()));
        Map<Integer, List<ImpactedSymbol>> upstreamByDepth = groupByDepth(result.getUpstream());
        for (Map.Entry<Integer, List<ImpactedSymbol>> entry : upstreamByDepth.entrySet()) {
            sb.append(String.format("  Depth %d: %d symbols\n", entry.getKey(), entry.getValue().size()));
        }
        
        sb.append(String.format("\nDownstream (Callees): %d\n", result.getDownstream().size()));
        Map<Integer, List<ImpactedSymbol>> downstreamByDepth = groupByDepth(result.getDownstream());
        for (Map.Entry<Integer, List<ImpactedSymbol>> entry : downstreamByDepth.entrySet()) {
            sb.append(String.format("  Depth %d: %d symbols\n", entry.getKey(), entry.getValue().size()));
        }
        
        return sb.toString();
    }
}
