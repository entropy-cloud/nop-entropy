package io.nop.code.core.model;

import io.nop.api.core.annotations.data.DataBean;
/**
 * 注解使用数据模型
 */
@DataBean
public class CodeAnnotationUsage {
    private String id;
    private String annotationTypeQualifiedName;
    private String annotatedSymbolId;
    private String attributes;
    private int line;
    private int column;
    private EdgeProvenance provenance;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
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

    public EdgeProvenance getProvenance() {
        return provenance;
    }

    public void setProvenance(EdgeProvenance provenance) {
        this.provenance = provenance;
    }
}
