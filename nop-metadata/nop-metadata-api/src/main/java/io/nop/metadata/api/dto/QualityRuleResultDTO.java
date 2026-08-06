
package io.nop.metadata.api.dto;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.metadata.api.dto.ErrorDTO;

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
    /** AR-14（R8.1）：判定状态（PASS/FAIL/ERROR/SKIP）——确定性新增，单规则路径语义保持。 */
    private String status;
    /** AR-14（R8.1）：判定消息（失败原因/SKIP 原因/通过摘要）——确定性新增。 */
    private String message;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
