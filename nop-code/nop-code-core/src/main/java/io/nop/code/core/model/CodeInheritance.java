package io.nop.code.core.model;

import io.nop.api.core.annotations.data.DataBean;
/**
 * 继承关系数据模型
 */
@DataBean
public class CodeInheritance {
    private String id;
    private String subTypeId;
    private String superTypeQualifiedName;
    private CodeRelationType relationType;
    private EdgeProvenance provenance;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSubTypeId() {
        return subTypeId;
    }

    public void setSubTypeId(String subTypeId) {
        this.subTypeId = subTypeId;
    }

    public String getSuperTypeQualifiedName() {
        return superTypeQualifiedName;
    }

    public void setSuperTypeQualifiedName(String superTypeQualifiedName) {
        this.superTypeQualifiedName = superTypeQualifiedName;
    }

    public CodeRelationType getRelationType() {
        return relationType;
    }

    public void setRelationType(CodeRelationType relationType) {
        this.relationType = relationType;
    }

    public EdgeProvenance getProvenance() {
        return provenance;
    }

    public void setProvenance(EdgeProvenance provenance) {
        this.provenance = provenance;
    }
}
