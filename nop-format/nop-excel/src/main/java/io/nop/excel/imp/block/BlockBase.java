/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp.block;

public class BlockBase {
    private int rowIndex;
    private int colIndex;
    private int maxRowIndex;
    private int maxColIndex;

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public int getColIndex() {
        return colIndex;
    }

    public void setColIndex(int colIndex) {
        this.colIndex = colIndex;
    }

    public int getMaxRowIndex() {
        return maxRowIndex;
    }

    public void setMaxRowIndex(int maxRowIndex) {
        this.maxRowIndex = maxRowIndex;
    }

    public int getMaxColIndex() {
        return maxColIndex;
    }

    public void setMaxColIndex(int maxColIndex) {
        this.maxColIndex = maxColIndex;
    }

    public void init(){

    }
}