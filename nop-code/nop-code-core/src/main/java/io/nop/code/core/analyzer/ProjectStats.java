package io.nop.code.core.analyzer;

import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.model.LanguageFamily;

import java.util.Map;

/**
 * 项目统计信息
 */
public class ProjectStats {
    private int totalFiles;
    private int totalSymbols;
    private int totalCalls;
    private int resolvedCalls;
    private int unresolvedCalls;
    private Map<CodeSymbolKind, Integer> symbolCounts;
    private Map<LanguageFamily, Integer> languageFamilyCounts;

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public int getTotalSymbols() {
        return totalSymbols;
    }

    public void setTotalSymbols(int totalSymbols) {
        this.totalSymbols = totalSymbols;
    }

    public int getTotalCalls() {
        return totalCalls;
    }

    public void setTotalCalls(int totalCalls) {
        this.totalCalls = totalCalls;
    }

    public int getResolvedCalls() {
        return resolvedCalls;
    }

    public void setResolvedCalls(int resolvedCalls) {
        this.resolvedCalls = resolvedCalls;
    }

    public int getUnresolvedCalls() {
        return unresolvedCalls;
    }

    public void setUnresolvedCalls(int unresolvedCalls) {
        this.unresolvedCalls = unresolvedCalls;
    }

    public Map<CodeSymbolKind, Integer> getSymbolCounts() {
        return symbolCounts;
    }

    public void setSymbolCounts(Map<CodeSymbolKind, Integer> symbolCounts) {
        this.symbolCounts = symbolCounts;
    }

    public Map<LanguageFamily, Integer> getLanguageFamilyCounts() {
        return languageFamilyCounts;
    }

    public void setLanguageFamilyCounts(Map<LanguageFamily, Integer> languageFamilyCounts) {
        this.languageFamilyCounts = languageFamilyCounts;
    }

    public double getResolutionRate() {
        if (totalCalls == 0) return 0;
        return (double) resolvedCalls / totalCalls * 100;
    }

    @Override
    public String toString() {
        return "ProjectStats{" +
                "totalFiles=" + totalFiles +
                ", totalSymbols=" + totalSymbols +
                ", totalCalls=" + totalCalls +
                ", resolvedCalls=" + resolvedCalls +
                ", unresolvedCalls=" + unresolvedCalls +
                ", resolutionRate=" + String.format("%.1f%%", getResolutionRate()) +
                '}';
    }
}
