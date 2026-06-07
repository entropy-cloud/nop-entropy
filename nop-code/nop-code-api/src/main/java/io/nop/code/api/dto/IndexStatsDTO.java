package io.nop.code.api.dto;

import java.io.Serializable;
import java.util.Map;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class IndexStatsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String indexId;
    private int fileCount;
    private int symbolCount;
    private Map<String, Integer> symbolCounts;

    public String getIndexId() {
        return indexId;
    }

    public void setIndexId(String indexId) {
        this.indexId = indexId;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public int getSymbolCount() {
        return symbolCount;
    }

    public void setSymbolCount(int symbolCount) {
        this.symbolCount = symbolCount;
    }

    public Map<String, Integer> getSymbolCounts() {
        return symbolCounts;
    }

    public void setSymbolCounts(Map<String, Integer> symbolCounts) {
        this.symbolCounts = symbolCounts;
    }
}
