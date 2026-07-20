package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.metadata.dao.dto.ErrorDTO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 单条质量规则执行结果 DTO（来源：{@code NopMetaQualityRuleBizModel.executeQualityRule}）。
 */
@DataBean
public class QualityRuleResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String qualityRuleId;
    private int resultCount;
    private int passCount;
    private int failCount;
    private List<ErrorDTO> errors = new ArrayList<>();

    public String getQualityRuleId() {
        return qualityRuleId;
    }

    public void setQualityRuleId(String qualityRuleId) {
        this.qualityRuleId = qualityRuleId;
    }

    public int getResultCount() {
        return resultCount;
    }

    public void setResultCount(int resultCount) {
        this.resultCount = resultCount;
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

    public List<ErrorDTO> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorDTO> errors) {
        this.errors = errors;
    }
}
