package io.nop.code.graph.impact;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;

import java.util.*;

import io.nop.core.lang.json.JsonTool;

public class ImpactAnalyzer {
    
    public static class ImpactResult {
        private String targetSymbolId;
        private String targetQualifiedName;
        private List<ImpactedSymbol> upstream;
        private List<ImpactedSymbol> downstream;
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
    
    public static class ImpactedSymbol {
        private String symbolId;
        private String qualifiedName;
        private String name;
        private CodeSymbolKind kind;
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
        
        public CodeSymbolKind getKind() {
            return kind;
        }
        
        public void setKind(CodeSymbolKind kind) {
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
    
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
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
    
    public ImpactResult analyzeImpact(
            String targetQualifiedName,
            CallGraph callGraph,
            SymbolTable symbolTable,
            int maxDepth) {
        
        ImpactConfig config = new ImpactConfig();
        config.setMaxDepth(maxDepth);
        return analyzeImpact(targetQualifiedName, callGraph, symbolTable, config);
    }
    
    public ImpactResult analyzeImpact(
            String targetQualifiedName,
            CallGraph callGraph,
            SymbolTable symbolTable,
            ImpactConfig config) {
        
        CodeSymbol targetSymbol = findSymbolByQualifiedName(symbolTable, targetQualifiedName);
        if (targetSymbol == null) {
            ImpactResult result = new ImpactResult();
            result.setTargetQualifiedName(targetQualifiedName);
            result.setRiskLevel("not-found");
            return result;
        }
        
        String targetId = targetSymbol.getId();
        
        List<ImpactedSymbol> upstream = traceImpact(
                targetId, callGraph, true, symbolTable, config.getMaxDepth(), config.getMaxNodes()
        );
        
        List<ImpactedSymbol> downstream = traceImpact(
                targetId, callGraph, false, symbolTable, config.getMaxDepth(), config.getMaxNodes()
        );
        
        String riskLevel = evaluateRisk(upstream, downstream);
        
        ImpactResult result = new ImpactResult();
        result.setTargetSymbolId(targetId);
        result.setTargetQualifiedName(targetQualifiedName);
        result.setUpstream(upstream);
        result.setDownstream(downstream);
        result.setRiskLevel(riskLevel);
        
        return result;
    }
    
    private static List<ImpactedSymbol> traceImpact(
            String startId,
            CallGraph callGraph,
            boolean reverse,
            SymbolTable symbolTable,
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
            
            List<String> neighbors = reverse ? callGraph.getCallers(nodeId) : callGraph.getCallees(nodeId);
            for (String neighborId : neighbors) {
                if (!visited.contains(neighborId)) {
                    visited.add(neighborId);
                    
                    CodeSymbol symbol = symbolTable.getById(neighborId);
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
    
    private static String evaluateRisk(List<ImpactedSymbol> upstream, List<ImpactedSymbol> downstream) {
        int totalImpacted = upstream.size() + downstream.size();
        int maxDepth = Math.max(
                upstream.stream().mapToInt(ImpactedSymbol::getDepth).max().orElse(0),
                downstream.stream().mapToInt(ImpactedSymbol::getDepth).max().orElse(0)
        );
        
        RiskLevel level;
        if (totalImpacted > 50 || maxDepth > 5) {
            level = RiskLevel.CRITICAL;
        } else if (totalImpacted > 20 || maxDepth > 3) {
            level = RiskLevel.HIGH;
        } else if (totalImpacted > 5) {
            level = RiskLevel.MEDIUM;
        } else {
            level = RiskLevel.LOW;
        }
        return level.name().toLowerCase();
    }
    
    private static CodeSymbol findSymbolByQualifiedName(
            SymbolTable symbolTable, 
            String qualifiedName) {
        
        CodeSymbol exact = symbolTable.getByQualifiedName(qualifiedName);
        if (exact != null) {
            return exact;
        }
        
        int parenIndex = qualifiedName.indexOf('(');
        if (parenIndex > 0) {
            String withoutParams = qualifiedName.substring(0, parenIndex);
            
            CodeSymbol exactWithoutParams = symbolTable.getByQualifiedName(withoutParams);
            if (exactWithoutParams != null) {
                return exactWithoutParams;
            }
            
            CodeSymbol bestMatch = null;
            for (CodeSymbol symbol : symbolTable.getAll()) {
                if (symbol.getQualifiedName() != null && 
                    symbol.getQualifiedName().startsWith(withoutParams)) {
                    if (symbol.getQualifiedName().equals(withoutParams)) {
                        return symbol;
                    }
                    if (bestMatch == null) {
                        bestMatch = symbol;
                    }
                }
            }
            return bestMatch;
        }
        
        return null;
    }
    
    private static String extractFilePath(CodeSymbol symbol) {
        String extData = symbol.getExtData();
        if (extData == null || extData.isEmpty()) {
            return null;
        }
        try {
            Object parsed = JsonTool.parseNonStrict(extData);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) parsed;
                Object filePath = map.get("filePath");
                return filePath != null ? filePath.toString() : null;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
    
    public static Map<Integer, List<ImpactedSymbol>> groupByDepth(List<ImpactedSymbol> symbols) {
        Map<Integer, List<ImpactedSymbol>> grouped = new TreeMap<>();
        for (ImpactedSymbol symbol : symbols) {
            grouped.computeIfAbsent(symbol.getDepth(), k -> new ArrayList<>()).add(symbol);
        }
        return grouped;
    }
    
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
