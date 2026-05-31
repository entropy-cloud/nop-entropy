package io.nop.code.flow;

import java.util.List;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class ChangeAnalysisResult {

    private List<String> changedFiles;
    private List<AffectedSymbol> affectedSymbols;
    private RiskSummary riskSummary;
    private List<String> suggestedActions;

    public List<String> getChangedFiles() { return changedFiles; }
    public void setChangedFiles(List<String> changedFiles) { this.changedFiles = changedFiles; }
    public List<AffectedSymbol> getAffectedSymbols() { return affectedSymbols; }
    public void setAffectedSymbols(List<AffectedSymbol> affectedSymbols) { this.affectedSymbols = affectedSymbols; }
    public RiskSummary getRiskSummary() { return riskSummary; }
    public void setRiskSummary(RiskSummary riskSummary) { this.riskSummary = riskSummary; }
    public List<String> getSuggestedActions() { return suggestedActions; }
    public void setSuggestedActions(List<String> suggestedActions) { this.suggestedActions = suggestedActions; }

    public static class AffectedSymbol {
        private String symbolId;
        private String qualifiedName;
        private String kind;
        private double riskScore;
        private RiskBreakdown riskBreakdown;
        private List<ExecutionFlow> affectedFlows;

        public String getSymbolId() { return symbolId; }
        public void setSymbolId(String symbolId) { this.symbolId = symbolId; }
        public String getQualifiedName() { return qualifiedName; }
        public void setQualifiedName(String qualifiedName) { this.qualifiedName = qualifiedName; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
        public RiskBreakdown getRiskBreakdown() { return riskBreakdown; }
        public void setRiskBreakdown(RiskBreakdown riskBreakdown) { this.riskBreakdown = riskBreakdown; }
        public List<ExecutionFlow> getAffectedFlows() { return affectedFlows; }
        public void setAffectedFlows(List<ExecutionFlow> affectedFlows) { this.affectedFlows = affectedFlows; }
    }

    public static class RiskBreakdown {
        private double flowParticipation;
        private double communityCrossing;
        private double testCoverageGap;
        private double securitySensitivity;
        private double callerCount;

        public double getFlowParticipation() { return flowParticipation; }
        public void setFlowParticipation(double flowParticipation) { this.flowParticipation = flowParticipation; }
        public double getCommunityCrossing() { return communityCrossing; }
        public void setCommunityCrossing(double communityCrossing) { this.communityCrossing = communityCrossing; }
        public double getTestCoverageGap() { return testCoverageGap; }
        public void setTestCoverageGap(double testCoverageGap) { this.testCoverageGap = testCoverageGap; }
        public double getSecuritySensitivity() { return securitySensitivity; }
        public void setSecuritySensitivity(double securitySensitivity) { this.securitySensitivity = securitySensitivity; }
        public double getCallerCount() { return callerCount; }
        public void setCallerCount(double callerCount) { this.callerCount = callerCount; }
    }

    public static class RiskSummary {
        private int high;
        private int medium;
        private int low;

        public int getHigh() { return high; }
        public void setHigh(int high) { this.high = high; }
        public int getMedium() { return medium; }
        public void setMedium(int medium) { this.medium = medium; }
        public int getLow() { return low; }
        public void setLow(int low) { this.low = low; }
    }
}
