package io.nop.code.api.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * N6.3: indexFileSet 的结果——索引计数与显式跳过清单（无分析器/读取失败），不静默。
 */
public class IndexFileSetResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private int indexedCount;
    private List<String> skippedPaths = new ArrayList<>();

    public int getIndexedCount() {
        return indexedCount;
    }

    public void setIndexedCount(int indexedCount) {
        this.indexedCount = indexedCount;
    }

    public List<String> getSkippedPaths() {
        return skippedPaths;
    }

    public void setSkippedPaths(List<String> skippedPaths) {
        this.skippedPaths = skippedPaths;
    }
}
