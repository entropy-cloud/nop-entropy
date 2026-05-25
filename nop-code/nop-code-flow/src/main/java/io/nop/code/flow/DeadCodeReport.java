package io.nop.code.flow;

import java.util.List;

public class DeadCodeReport {

    private List<DeadCodeEntry> deadSymbols;
    private List<DeadCodeEntry> suspiciousSymbols;
    private DeadCodeStats stats;

    public List<DeadCodeEntry> getDeadSymbols() { return deadSymbols; }
    public void setDeadSymbols(List<DeadCodeEntry> deadSymbols) { this.deadSymbols = deadSymbols; }
    public List<DeadCodeEntry> getSuspiciousSymbols() { return suspiciousSymbols; }
    public void setSuspiciousSymbols(List<DeadCodeEntry> suspiciousSymbols) { this.suspiciousSymbols = suspiciousSymbols; }
    public DeadCodeStats getStats() { return stats; }
    public void setStats(DeadCodeStats stats) { this.stats = stats; }

    public static class DeadCodeEntry {
        private String symbolId;
        private String qualifiedName;
        private String kind;
        private String filePath;
        private String reason;
        private double confidence;

        public String getSymbolId() { return symbolId; }
        public void setSymbolId(String symbolId) { this.symbolId = symbolId; }
        public String getQualifiedName() { return qualifiedName; }
        public void setQualifiedName(String qualifiedName) { this.qualifiedName = qualifiedName; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }

    public static class DeadCodeStats {
        private int total;
        private int dead;
        private int suspicious;

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getDead() { return dead; }
        public void setDead(int dead) { this.dead = dead; }
        public int getSuspicious() { return suspicious; }
        public void setSuspicious(int suspicious) { this.suspicious = suspicious; }
    }
}
