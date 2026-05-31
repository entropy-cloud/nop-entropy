package io.nop.code.service.api.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class FileTreeNode implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String path;
    private String type; // "package" or "file"
    private List<FileTreeNode> children = new ArrayList<>();
    private int symbolCount;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<FileTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<FileTreeNode> children) {
        this.children = children;
    }

    public int getSymbolCount() {
        return symbolCount;
    }

    public void setSymbolCount(int symbolCount) {
        this.symbolCount = symbolCount;
    }
}
