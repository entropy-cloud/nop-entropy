package io.nop.code.service.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

@DataBean
public class GodNodeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String symbolId;
    private String qualifiedName;
    private String kind;
    private int degree;
    private int callerCount;
    private int calleeCount;

    public String getSymbolId() {
        return symbolId;
    }

    public void setSymbolId(String symbolId) {
        this.symbolId = symbolId;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public int getDegree() {
        return degree;
    }

    public void setDegree(int degree) {
        this.degree = degree;
    }

    public int getCallerCount() {
        return callerCount;
    }

    public void setCallerCount(int callerCount) {
        this.callerCount = callerCount;
    }

    public int getCalleeCount() {
        return calleeCount;
    }

    public void setCalleeCount(int calleeCount) {
        this.calleeCount = calleeCount;
    }
}
