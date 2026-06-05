package io.nop.code.api.dto;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class DepNodeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String filePath;
    private int inDegree;
    private int outDegree;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getInDegree() {
        return inDegree;
    }

    public void setInDegree(int inDegree) {
        this.inDegree = inDegree;
    }

    public int getOutDegree() {
        return outDegree;
    }

    public void setOutDegree(int outDegree) {
        this.outDegree = outDegree;
    }
}
