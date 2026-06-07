package io.nop.code.graph.entrypoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
public class EntryPointScorer {

    public static class EntryPointScore {
        private String symbolId;
        private String qualifiedName;
        private String name;
        private CodeSymbolKind kind;
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

        public CodeSymbolKind getKind() {
            return kind;
        }

        public void setKind(CodeSymbolKind kind) {
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

    public enum EntryPointType {
        ENTRY_POINT,
        UTILITY,
        MIDDLEWARE,
        LEAF,
        ISOLATED
    }

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

    public List<EntryPointScore> scoreEntryPoints(
            CallGraph callGraph,
            SymbolTable symbolTable) {
        return scoreEntryPoints(callGraph, symbolTable, ScoringConfig.defaultConfig());
    }

    public List<EntryPointScore> scoreEntryPoints(
            CallGraph callGraph,
            SymbolTable symbolTable,
            ScoringConfig config) {

        List<EntryPointScore> scores = new ArrayList<>();

        for (CodeSymbol symbol : symbolTable.getAll()) {
            if (symbol.getKind() != CodeSymbolKind.METHOD &&
                symbol.getKind() != CodeSymbolKind.CONSTRUCTOR) {
                continue;
            }

            String id = symbol.getId();
            int calleeCount = callGraph.getCallees(id).size();
            int callerCount = callGraph.getCallers(id).size();

            double score = (double) calleeCount / (callerCount + 1);

            EntryPointScore eps = new EntryPointScore();
            eps.setSymbolId(id);
            eps.setQualifiedName(symbol.getQualifiedName());
            eps.setName(symbol.getName());
            eps.setKind(symbol.getKind());
            eps.setScore(score);
            eps.setCallerCount(callerCount);
            eps.setCalleeCount(calleeCount);

            eps.setEntryPointType(classify(score, callerCount, calleeCount, config));

            scores.add(eps);
        }

        scores.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return scores;
    }

    private static EntryPointType classify(double score, int callerCount, int calleeCount, ScoringConfig config) {
        if (callerCount == 0 && calleeCount == 0) {
            return EntryPointType.ISOLATED;
        }

        if (calleeCount == 0) {
            return EntryPointType.LEAF;
        }

        if (score >= config.getEntryPointThreshold() &&
            callerCount <= config.getMaxCallerCountForEntry()) {
            return EntryPointType.ENTRY_POINT;
        }

        if (callerCount >= config.getUtilityMinCallerCount() &&
            calleeCount <= config.getUtilityMaxCalleeCount()) {
            return EntryPointType.UTILITY;
        }

        return EntryPointType.MIDDLEWARE;
    }

    public static List<EntryPointScore> getEntryPoints(List<EntryPointScore> scores) {
        return scores.stream()
                .filter(s -> s.getEntryPointType() == EntryPointType.ENTRY_POINT)
                .collect(Collectors.toList());
    }

    public static List<EntryPointScore> getUtilities(List<EntryPointScore> scores) {
        return scores.stream()
                .filter(s -> s.getEntryPointType() == EntryPointType.UTILITY)
                .collect(Collectors.toList());
    }

    public static Map<EntryPointType, Long> countByType(List<EntryPointScore> scores) {
        return scores.stream()
                .collect(Collectors.groupingBy(EntryPointScore::getEntryPointType, Collectors.counting()));
    }

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
