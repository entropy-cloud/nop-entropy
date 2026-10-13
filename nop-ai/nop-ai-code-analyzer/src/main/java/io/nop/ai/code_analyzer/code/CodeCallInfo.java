package io.nop.ai.code_analyzer.code;

import io.nop.api.core.annotations.data.DataBean;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

@DataBean
public class CodeCallInfo {
    private String ownerClassName;
    private String fnName;
    private List<CodeVariableInfo> params;

    public String getOwnerClassName() {
        return ownerClassName;
    }

    public void setOwnerClassName(String ownerClassName) {
        this.ownerClassName = ownerClassName;
    }

    public String getFnName() {
        return fnName;
    }

    public void setFnName(String fnName) {
        this.fnName = fnName;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<CodeVariableInfo> getParams() {
        return params;
    }

    public void setParams(List<CodeVariableInfo> params) {
        this.params = params;
    }

    public void intern() {
        ownerClassName = CodeSymbolInterning.internString(ownerClassName);
        fnName = CodeSymbolInterning.internString(fnName);

        if (params != null) {
            params.forEach(CodeVariableInfo::intern);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CodeCallInfo that = (CodeCallInfo) o;
        return Objects.equals(ownerClassName, that.ownerClassName) &&
                Objects.equals(fnName, that.fnName) &&
                Objects.equals(params, that.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerClassName, fnName, params);
    }
}
