package io.nop.javaparser.analyzer;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 注解使用信息
 * 对应 nop-code 中的 NopCodeAnnotationUsage 表
 */
@DataBean
public class AnnotationUsage {
    /**
     * ID
     */
    private String id;

    /**
     * 注解类型符号ID
     */
    private String annotationTypeId;

    /**
     * 注解类型全限定名（解析时使用）
     */
    private String annotationTypeQualifiedName;

    /**
     * 被注解符号ID
     */
    private String annotatedSymbolId;

    /**
     * 行号
     */
    private int line;

    /**
     * 列号
     */
    private int column;

    /**
     * 属性值（JSON格式）
     */
    private String attributes;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAnnotationTypeId() {
        return annotationTypeId;
    }

    public void setAnnotationTypeId(String annotationTypeId) {
        this.annotationTypeId = annotationTypeId;
    }

    public String getAnnotationTypeQualifiedName() {
        return annotationTypeQualifiedName;
    }

    public void setAnnotationTypeQualifiedName(String annotationTypeQualifiedName) {
        this.annotationTypeQualifiedName = annotationTypeQualifiedName;
    }

    public String getAnnotatedSymbolId() {
        return annotatedSymbolId;
    }

    public void setAnnotatedSymbolId(String annotatedSymbolId) {
        this.annotatedSymbolId = annotatedSymbolId;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }
}
