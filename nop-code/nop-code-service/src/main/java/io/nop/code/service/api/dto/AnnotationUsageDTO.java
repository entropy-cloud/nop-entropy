package io.nop.code.service.api.dto;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.code.core.model.CodeAnnotationUsage;
@DataBean
public class AnnotationUsageDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String annotationTypeQualifiedName;
    private String annotatedSymbolId;
    private String attributes;
    private int line;
    private int column;

    public static AnnotationUsageDTO fromCodeAnnotationUsage(CodeAnnotationUsage usage) {
        AnnotationUsageDTO dto = new AnnotationUsageDTO();
        dto.setId(usage.getId());
        dto.setAnnotationTypeQualifiedName(usage.getAnnotationTypeQualifiedName());
        dto.setAnnotatedSymbolId(usage.getAnnotatedSymbolId());
        dto.setAttributes(usage.getAttributes());
        dto.setLine(usage.getLine());
        dto.setColumn(usage.getColumn());
        return dto;
    }

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
}
