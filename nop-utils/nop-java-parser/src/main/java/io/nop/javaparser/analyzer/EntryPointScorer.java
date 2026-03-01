package io.nop.javaparser.analyzer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 入口点评分器
 * 
 * 基于调用图分析，自动识别重要函数（入口点）
 * 
 * 评分公式: score = calleeCount / (callerCount + 1)
 * 
 * 解释:
 * - calleeCount: 该函数调用其他函数的数量
 * - callerCount: 调用该函数的函数数量
 * - 高分 = 调用多但被调用少 = 入口点
 * - 低分 = 调用少但被调用多 = 工具函数
 */
public class EntryPointScorer {
    
    /**
     * 入口点评分结果
     */
    public static class EntryPointScore {
        private String symbolId;
        private String qualifiedName;
        private String name;
        private SymbolKind kind;
        private double score;
        private int callerCount;
        private int calleeCount;
        private EntryPointType entryPointType;
        
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
        
        public double getScore() {
            return score;
        }
        
        public void setScore(double score) {
            this.score = score;
        }
        
        public int getCallerCount() {
            return callerCount;
        }
        
        public void setCallerCount(int callerCount) {
            this.callerCount = callerCount;
        }
        
        public int getCalleeCount() {
            return calleeCount;
        }
        
        public void setCalleeCount(int calleeCount) {
            this.calleeCount = calleeCount;
        }
        
        public EntryPointType getEntryPointType() {
            return entryPointType;
        }
        
        public void setEntryPointType(EntryPointType entryPointType) {
            this.entryPointType = entryPointType;
        }
        
        @Override
        public String toString() {
            return String.format("EntryPointScore{name=%s, score=%.2f, type=%s, callers=%d, callees=%d}",
                    qualifiedName, score, entryPointType, callerCount, calleeCount);
        }
    }
    
    /**
     * 入口点类型
     */
    public enum EntryPointType {
        /**
         * 入口点 - 调用多，被调用少
         */
        ENTRY_POINT,
        
        /**
         * 工具函数 - 被调用多，调用少
         */
        UTILITY,
        
        /**
         * 中间层 - 调用和被调用都多
         */
        MIDDLEWARE,
        
        /**
         * 叶子节点 - 不调用其他函数
         */
        LEAF,
        
        /**
         * 孤立节点 - 既不调用也不被调用
         */
        ISOLATED
    }
    
    /**
     * 评分配置
     */
    public static class ScoringConfig {
        private double entryPointThreshold = 2.0;
        private int maxCallerCountForEntry = 3;
        private int utilityMinCallerCount = 5;
        private int utilityMaxCalleeCount = 3;
        
        public double getEntryPointThreshold() {
            return entryPointThreshold;
        }
        
        public void setEntryPointThreshold(double entryPointThreshold) {
            this.entryPointThreshold = entryPointThreshold;
        }
        
        public int getMaxCallerCountForEntry() {
            return maxCallerCountForEntry;
        }
        
        public void setMaxCallerCountForEntry(int maxCallerCountForEntry) {
            this.maxCallerCountForEntry = maxCallerCountForEntry;
        }
        
        public int getUtilityMinCallerCount() {
            return utilityMinCallerCount;
        }
        
        public void setUtilityMinCallerCount(int utilityMinCallerCount) {
            this.utilityMinCallerCount = utilityMinCallerCount;
        }
        
        public int getUtilityMaxCalleeCount() {
            return utilityMaxCalleeCount;
        }
        
        public void setUtilityMaxCalleeCount(int utilityMaxCalleeCount) {
            this.utilityMaxCalleeCount = utilityMaxCalleeCount;
        }
        
        public static ScoringConfig defaultConfig() {
            return new ScoringConfig();
        }
    }
    
    /**
     * 对项目中的方法进行入口点评分
     * 
     * @param callGraph 调用图 (callerId -> [calleeId])
     * @param reverseCallGraph 反向调用图 (calleeId -> [callerId])
     * @param symbolTable 符号表 (symbolId -> SymbolInfo)
     * @return 评分结果列表，按分数降序排列
     */
    public static List<EntryPointScore> scoreEntryPoints(
            Map<String, List<String>> callGraph,
            Map<String, List<String>> reverseCallGraph,
            Map<String, SymbolInfo> symbolTable) {
        return scoreEntryPoints(callGraph, reverseCallGraph, symbolTable, ScoringConfig.defaultConfig());
    }
    
    /**
     * 对项目中的方法进行入口点评分（带配置）
     */
    public static List<EntryPointScore> scoreEntryPoints(
            Map<String, List<String>> callGraph,
            Map<String, List<String>> reverseCallGraph,
            Map<String, SymbolInfo> symbolTable,
            ScoringConfig config) {
        
        List<EntryPointScore> scores = new ArrayList<>();
        
        for (SymbolInfo symbol : symbolTable.values()) {
            // 只对方法和构造器评分
            if (symbol.getKind() != SymbolKind.METHOD && 
                symbol.getKind() != SymbolKind.CONSTRUCTOR) {
                continue;
            }
            
            String id = symbol.getId();
            int calleeCount = callGraph.getOrDefault(id, Collections.emptyList()).size();
            int callerCount = reverseCallGraph.getOrDefault(id, Collections.emptyList()).size();
            
            // 核心公式
            double score = (double) calleeCount / (callerCount + 1);
            
            EntryPointScore eps = new EntryPointScore();
            eps.setSymbolId(id);
            eps.setQualifiedName(symbol.getQualifiedName());
            eps.setName(symbol.getName());
            eps.setKind(symbol.getKind());
            eps.setScore(score);
            eps.setCallerCount(callerCount);
            eps.setCalleeCount(calleeCount);
            
            // 分类
            eps.setEntryPointType(classify(score, callerCount, calleeCount, config));
            
            scores.add(eps);
        }
        
        // 按分数降序排序
        scores.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return scores;
    }
    
    /**
     * 分类入口点类型
     */
    private static EntryPointType classify(double score, int callerCount, int calleeCount, ScoringConfig config) {
        // 孤立节点
        if (callerCount == 0 && calleeCount == 0) {
            return EntryPointType.ISOLATED;
        }
        
        // 叶子节点
        if (calleeCount == 0) {
            return EntryPointType.LEAF;
        }
        
        // 入口点 - 高分且被调用少
        if (score >= config.getEntryPointThreshold() && 
            callerCount <= config.getMaxCallerCountForEntry()) {
            return EntryPointType.ENTRY_POINT;
        }
        
        // 工具函数 - 被调用多，调用少
        if (callerCount >= config.getUtilityMinCallerCount() && 
            calleeCount <= config.getUtilityMaxCalleeCount()) {
            return EntryPointType.UTILITY;
        }
        
        // 中间层 - 其他情况
        return EntryPointType.MIDDLEWARE;
    }
    
    /**
     * 获取所有入口点（按分数排序）
     */
    public static List<EntryPointScore> getEntryPoints(List<EntryPointScore> scores) {
        return scores.stream()
                .filter(s -> s.getEntryPointType() == EntryPointType.ENTRY_POINT)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有工具函数
     */
    public static List<EntryPointScore> getUtilities(List<EntryPointScore> scores) {
        return scores.stream()
                .filter(s -> s.getEntryPointType() == EntryPointType.UTILITY)
                .collect(Collectors.toList());
    }
    
    /**
     * 按类型分组统计
     */
    public static Map<EntryPointType, Long> countByType(List<EntryPointScore> scores) {
        return scores.stream()
                .collect(Collectors.groupingBy(EntryPointScore::getEntryPointType, Collectors.counting()));
    }
    
    /**
     * 打印评分摘要
     */
    public static String printSummary(List<EntryPointScore> scores) {
        Map<EntryPointType, Long> counts = countByType(scores);
        StringBuilder sb = new StringBuilder();
        sb.append("=== EntryPoint Scoring Summary ===\n");
        sb.append(String.format("Total methods: %d\n", scores.size()));
        sb.append(String.format("  Entry Points: %d\n", counts.getOrDefault(EntryPointType.ENTRY_POINT, 0L)));
        sb.append(String.format("  Utilities:    %d\n", counts.getOrDefault(EntryPointType.UTILITY, 0L)));
        sb.append(String.format("  Middleware:   %d\n", counts.getOrDefault(EntryPointType.MIDDLEWARE, 0L)));
        sb.append(String.format("  Leaf:         %d\n", counts.getOrDefault(EntryPointType.LEAF, 0L)));
        sb.append(String.format("  Isolated:     %d\n", counts.getOrDefault(EntryPointType.ISOLATED, 0L)));
        sb.append("\nTop 10 Entry Points:\n");
        
        getEntryPoints(scores).stream()
                .limit(10)
                .forEach(s -> sb.append(String.format("  %.2f - %s\n", s.getScore(), s.getQualifiedName())));
        
        return sb.toString();
    }
}
