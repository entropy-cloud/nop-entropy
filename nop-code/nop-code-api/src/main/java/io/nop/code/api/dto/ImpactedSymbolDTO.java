package io.nop.code.api.dto;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class ImpactedSymbolDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String symbolId;
    private String qualifiedName;
    private String name;
    private String kind;
    private int depth;
    private String filePath;

    public String getSymbolId() {
        return symbolId;
    }

    public void setSymbolId(String symbolId) {
        this.symbolId = symbolId;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
