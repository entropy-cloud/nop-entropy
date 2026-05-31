package io.nop.code.service.api.dto;

import java.io.Serializable;
import java.util.List;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class TypeHierarchyDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private SymbolInfoDTO symbol;
    private List<TypeHierarchyDTO> superTypes;
    private List<TypeHierarchyDTO> subTypes;

    public SymbolInfoDTO getSymbol() {
        return symbol;
    }

    public void setSymbol(SymbolInfoDTO symbol) {
        this.symbol = symbol;
    }

    public List<TypeHierarchyDTO> getSuperTypes() {
        return superTypes;
    }

    public void setSuperTypes(List<TypeHierarchyDTO> superTypes) {
        this.superTypes = superTypes;
    }

    public List<TypeHierarchyDTO> getSubTypes() {
        return subTypes;
    }

    public void setSubTypes(List<TypeHierarchyDTO> subTypes) {
        this.subTypes = subTypes;
    }
}
