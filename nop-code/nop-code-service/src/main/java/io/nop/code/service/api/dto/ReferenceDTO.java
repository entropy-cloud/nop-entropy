package io.nop.code.service.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

@DataBean
public class ReferenceDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String filePath;
    private String enclosingSymbolName;
    private String enclosingQualifiedName;
    private String kind;
    private int line;
    private int column;
    private String context;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getEnclosingSymbolName() {
        return enclosingSymbolName;
    }

    public void setEnclosingSymbolName(String enclosingSymbolName) {
        this.enclosingSymbolName = enclosingSymbolName;
    }

    public String getEnclosingQualifiedName() {
        return enclosingQualifiedName;
    }

    public void setEnclosingQualifiedName(String enclosingQualifiedName) {
        this.enclosingQualifiedName = enclosingQualifiedName;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
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

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }
}
