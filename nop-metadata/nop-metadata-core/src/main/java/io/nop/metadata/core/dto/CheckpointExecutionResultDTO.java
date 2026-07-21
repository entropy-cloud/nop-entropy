/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.core.dto;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.metadata.core.dto.ErrorDTO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质量检查点执行结果 DTO（来源：{@code NopMetaQualityCheckpointBizModel.executeCheckpoint}）。
 */
@DataBean
public class CheckpointExecutionResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String checkpointId;
    private int totalRuleCount;
    private int executedRuleCount;
    private int passCount;
    private int failCount;
    private int errorCount;
    private List<String> affectedTableIds = new ArrayList<>();
    private List<QualityRuleResultDTO> ruleResults = new ArrayList<>();
    private List<ErrorDTO> errors = new ArrayList<>();
    private List<Map<String, Object>> executionResults = new ArrayList<>();
    private List<Map<String, Object>> executionErrors = new ArrayList<>();

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
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

    public List<String> getAffectedTableIds() {
        return affectedTableIds;
    }

    public void setAffectedTableIds(List<String> affectedTableIds) {
        this.affectedTableIds = affectedTableIds;
    }

    public List<Map<String, Object>> getExecutionResults() {
        return executionResults;
    }

    public void setExecutionResults(List<Map<String, Object>> executionResults) {
        this.executionResults = executionResults;
    }

    public List<Map<String, Object>> getExecutionErrors() {
        return executionErrors;
    }

    public void setExecutionErrors(List<Map<String, Object>> executionErrors) {
        this.executionErrors = executionErrors;
    }
}
