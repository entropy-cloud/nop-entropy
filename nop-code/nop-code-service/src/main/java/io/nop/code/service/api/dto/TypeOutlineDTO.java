package io.nop.code.service.api.dto;

import java.io.Serializable;
import java.util.List;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class TypeOutlineDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String qualifiedName;
    private String kind;
    private String accessModifier;
    private List<SymbolInfoDTO> methods;
    private List<SymbolInfoDTO> fields;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getAccessModifier() {
        return accessModifier;
    }

    public void setAccessModifier(String accessModifier) {
        this.accessModifier = accessModifier;
    }

    public List<SymbolInfoDTO> getMethods() {
        return methods;
    }

    public void setMethods(List<SymbolInfoDTO> methods) {
        this.methods = methods;
    }

    public List<SymbolInfoDTO> getFields() {
        return fields;
    }

    public void setFields(List<SymbolInfoDTO> fields) {
        this.fields = fields;
    }
}
