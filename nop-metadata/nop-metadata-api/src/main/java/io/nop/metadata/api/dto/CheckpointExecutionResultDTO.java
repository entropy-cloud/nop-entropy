
package io.nop.metadata.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 质量检查点执行结果 DTO（来源：{@code NopMetaQualityCheckpointBizModel.executeCheckpoint}）。
 *
 * <p>P2-20（plan 2026-08-16-0549-2）：移除规则结果明细/执行错误明细的两个冗余
 * {@code List<Map<String,Object>>} 字段——全部键均有类型化承接（{@link QualityRuleResultDTO} 六键全承接；
 * {@link ErrorDTO} 经 source/refType/refValue 增补 + code/detail 标识符惯例全承接），消费面清点见 owner doc。
 */
@DataBean
public class CheckpointExecutionResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String checkpointId;
    private String runId;
    private int totalRuleCount;
    private int executedRuleCount;
    private int passCount;
    private int failCount;
    private int errorCount;
    private int skipCount;
    private List<String> affectedTableIds = new ArrayList<>();
    private List<QualityRuleResultDTO> ruleResults = new ArrayList<>();
    private List<ErrorDTO> errors = new ArrayList<>();
    private boolean autoScore;
    private boolean scoreSkipped;

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public int getTotalRuleCount() {
        return totalRuleCount;
    }

    public void setTotalRuleCount(int totalRuleCount) {
        this.totalRuleCount = totalRuleCount;
    }

    public int getExecutedRuleCount() {
        return executedRuleCount;
    }

    public void setExecutedRuleCount(int executedRuleCount) {
        this.executedRuleCount = executedRuleCount;
    }

    public List<QualityRuleResultDTO> getRuleResults() {
        return ruleResults;
    }

    public void setRuleResults(List<QualityRuleResultDTO> ruleResults) {
        this.ruleResults = ruleResults;
    }

    public List<ErrorDTO> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorDTO> errors) {
        this.errors = errors;
    }

    public int getPassCount() {
        return passCount;
    }

    public void setPassCount(int passCount) {
        this.passCount = passCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    /** AR-14（R8.1）：显式 SKIP 计数（database 规则 / 方言不支持等），与 totalRuleCount 对账。 */
    public int getSkipCount() {
        return skipCount;
    }

    public void setSkipCount(int skipCount) {
        this.skipCount = skipCount;
    }

    public List<String> getAffectedTableIds() {
        return affectedTableIds;
    }

    public void setAffectedTableIds(List<String> affectedTableIds) {
        this.affectedTableIds = affectedTableIds;
    }

    public boolean isAutoScore() {
        return autoScore;
    }

    public void setAutoScore(boolean autoScore) {
        this.autoScore = autoScore;
    }

    public boolean isScoreSkipped() {
        return scoreSkipped;
    }

    public void setScoreSkipped(boolean scoreSkipped) {
        this.scoreSkipped = scoreSkipped;
    }
}
