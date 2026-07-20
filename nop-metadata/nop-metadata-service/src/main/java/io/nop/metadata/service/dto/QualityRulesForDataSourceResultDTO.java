package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.metadata.dao.dto.ErrorDTO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据源下全部质量规则执行结果 DTO（来源：{@code NopMetaQualityRuleBizModel.executeQualityRulesForDataSource}）。
 */
@DataBean
public class QualityRulesForDataSourceResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String dataSourceId;
    private int totalRuleCount;
    private int executedRuleCount;
    private List<QualityRuleResultDTO> ruleResults = new ArrayList<>();
    private List<ErrorDTO> errors = new ArrayList<>();

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
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
}
