package io.nop.table.validator.model;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.xml.XNode;

import java.util.Map;

@DataBean
public class TableStatCheckModel {
    private String id;
    private String column;
    private String errorCode;
    private int severity;
    private String errorDescription;
    private String statExpr;
    private Double geValue;
    private Double leValue;
    private Double gtValue;
    private Double ltValue;
    private Double betweenMin;
    private Double betweenMax;
    private XNode condition;
    private Map<String, String> errorParams;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
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

    public String getStatExpr() {
        return statExpr;
    }

    public void setStatExpr(String statExpr) {
        this.statExpr = statExpr;
    }

    public Double getGeValue() {
        return geValue;
    }

    public void setGeValue(Double geValue) {
        this.geValue = geValue;
    }

    public Double getLeValue() {
        return leValue;
    }

    public void setLeValue(Double leValue) {
        this.leValue = leValue;
    }

    public Double getGtValue() {
        return gtValue;
    }

    public void setGtValue(Double gtValue) {
        this.gtValue = gtValue;
    }

    public Double getLtValue() {
        return ltValue;
    }

    public void setLtValue(Double ltValue) {
        this.ltValue = ltValue;
    }

    public Double getBetweenMin() {
        return betweenMin;
    }

    public void setBetweenMin(Double betweenMin) {
        this.betweenMin = betweenMin;
    }

    public Double getBetweenMax() {
        return betweenMax;
    }

    public void setBetweenMax(Double betweenMax) {
        this.betweenMax = betweenMax;
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
