package io.nop.metadata.core.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@DataBean
public class QualityRuleExecuteResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String qualityResultId;
    private String status;
    private Object actualValue;
    private Object expectedValue;
    private String message;
    private Map<String, Object> details = new LinkedHashMap<>();

    public String getQualityResultId() {
        return qualityResultId;
    }

    public void setQualityResultId(String qualityResultId) {
        this.qualityResultId = qualityResultId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Object getActualValue() {
        return actualValue;
    }

    public void setActualValue(Object actualValue) {
        this.actualValue = actualValue;
    }

    public Object getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(Object expectedValue) {
        this.expectedValue = expectedValue;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}
