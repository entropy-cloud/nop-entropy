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
import java.util.List;

/**
 * 数据源下全部质量规则执行结果 DTO（来源：{@code NopMetaQualityRuleBizModel.executeQualityRulesForDataSource}）。
 */
@DataBean
public class QualityRulesForDataSourceResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String dataSourceId;
    private int totalRuleCount;
    private int executedCount;
    private List<QualityRuleExecuteResultDTO> results = new ArrayList<>();
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

    public int getExecutedCount() {
        return executedCount;
    }

    public void setExecutedCount(int executedCount) {
        this.executedCount = executedCount;
    }

    public List<QualityRuleExecuteResultDTO> getResults() {
        return results;
    }

    public void setResults(List<QualityRuleExecuteResultDTO> results) {
        this.results = results;
    }

    public List<ErrorDTO> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorDTO> errors) {
        this.errors = errors;
    }
}
