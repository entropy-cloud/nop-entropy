package io.nop.code.service.api.dto;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class DepEdgeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String source;
    private String target;
    private String importStatement;
    private boolean resolved;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getImportStatement() {
        return importStatement;
    }

    public void setImportStatement(String importStatement) {
        this.importStatement = importStatement;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }
}
