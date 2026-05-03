package io.nop.code.service.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.List;

@DataBean
public class CallHierarchyDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private SymbolInfoDTO symbol;
    private List<CallHierarchyDTO> callees;
    private List<CallHierarchyDTO> callers;

    public SymbolInfoDTO getSymbol() {
        return symbol;
    }

    public void setSymbol(SymbolInfoDTO symbol) {
        this.symbol = symbol;
    }

    public List<CallHierarchyDTO> getCallees() {
        return callees;
    }

    public void setCallees(List<CallHierarchyDTO> callees) {
        this.callees = callees;
    }

    public List<CallHierarchyDTO> getCallers() {
        return callers;
    }

    public void setCallers(List<CallHierarchyDTO> callers) {
        this.callers = callers;
    }
}
