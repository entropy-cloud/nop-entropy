package io.nop.metadata.core.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

@DataBean
public class ContractCheckResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Timestamp timestamp;
    private String status;
    private String message;
    private Map<String, Object> qualitySummary = new LinkedHashMap<>();
    private Map<String, Object> slaSummary = new LinkedHashMap<>();

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
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

    public Map<String, Object> getQualitySummary() {
        return qualitySummary;
    }

    public void setQualitySummary(Map<String, Object> qualitySummary) {
        this.qualitySummary = qualitySummary;
    }

    public Map<String, Object> getSlaSummary() {
        return slaSummary;
    }

    public void setSlaSummary(Map<String, Object> slaSummary) {
        this.slaSummary = slaSummary;
    }
}
