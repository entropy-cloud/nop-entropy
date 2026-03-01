package io.nop.javaparser.analyzer;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 继承关系信息
 * 对应 nop-code 中的 NopCodeInheritance 表
 */
@DataBean
public class InheritanceInfo {
    /**
     * ID
     */
    private String id;

    /**
     * 子类型符号ID
     */
    private String subTypeId;

    /**
     * 父类型符号ID
     */
    private String superTypeId;

    /**
     * 父类型全限定名（解析时使用）
     */
    private String superTypeQualifiedName;

    /**
     * 关系类型
     */
    private RelationType relationType;

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

    public String getSuperTypeId() {
        return superTypeId;
    }

    public void setSuperTypeId(String superTypeId) {
        this.superTypeId = superTypeId;
    }

    public String getSuperTypeQualifiedName() {
        return superTypeQualifiedName;
    }

    public void setSuperTypeQualifiedName(String superTypeQualifiedName) {
        this.superTypeQualifiedName = superTypeQualifiedName;
    }

    public RelationType getRelationType() {
        return relationType;
    }

    public void setRelationType(RelationType relationType) {
        this.relationType = relationType;
    }
}
