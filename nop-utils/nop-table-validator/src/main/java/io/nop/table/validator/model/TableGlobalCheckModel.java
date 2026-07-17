package io.nop.table.validator.model;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.xml.XNode;

import java.util.Map;

@DataBean
public class TableGlobalCheckModel {
    private String id;
    private String errorCode;
    private int severity;
    private String errorDescription;
    private Integer rowCountMin;
    private Integer rowCountMax;
    private Integer columnCountMin;
    private Integer columnCountMax;
    private XNode condition;
    private Map<String, String> errorParams;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }

    public Integer getRowCountMin() {
        return rowCountMin;
    }

    public void setRowCountMin(Integer rowCountMin) {
        this.rowCountMin = rowCountMin;
    }

    public Integer getRowCountMax() {
        return rowCountMax;
    }

    public void setRowCountMax(Integer rowCountMax) {
        this.rowCountMax = rowCountMax;
    }

    public Integer getColumnCountMin() {
        return columnCountMin;
    }

    public void setColumnCountMin(Integer columnCountMin) {
        this.columnCountMin = columnCountMin;
    }

    public Integer getColumnCountMax() {
        return columnCountMax;
    }

    public void setColumnCountMax(Integer columnCountMax) {
        this.columnCountMax = columnCountMax;
    }

    public XNode getCondition() {
        return condition;
    }

    public void setCondition(XNode condition) {
        this.condition = condition;
    }

    public Map<String, String> getErrorParams() {
        return errorParams;
    }

    public void setErrorParams(Map<String, String> errorParams) {
        this.errorParams = errorParams;
    }
}
