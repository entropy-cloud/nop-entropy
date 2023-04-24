/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.util.FreezeHelper;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.IJsonSerializable;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.IRow;

public class ProxyCell implements ICell, IJsonSerializable {
    /**
     * 合并单元格所对应的左上角的单元格
     */
    private final ICell cell;
    private boolean frozen;
    private IRow row;

    /**
     * 从真实单元格到本单元格的行的偏移量，从0开始
     */
    private int rowOffset;

    /**
     * 从真实单元格到本单元格的列的偏移量，从0开始
     */
    private int colOffset;

    public ProxyCell(ICell cell) {
        this.cell = cell;
    }

    public ProxyCell(ICell cell, int rowOffset, int colOffset) {
        this(cell);
        setRowOffset(rowOffset);
        setColOffset(colOffset);
    }

    public String toString() {
        return "ProxyCell[rowOffset=" + rowOffset + ",colOffset=" + colOffset + ",rowSpan=" + this.getRowSpan() + ","
                + "colSpan=" + this.getColSpan() + "]";
    }

    public boolean frozen() {
        return frozen;
    }

    public void freeze(boolean cascade) {
        frozen = true;
    }

    @Override
    public ProxyCell cloneInstance() {
        throw new UnsupportedOperationException("ProxyCell.cloneInstance");
    }

    @Override
    public String getStyleId() {
        return null;
    }

    @Override
    public int getRowOffset() {
        return rowOffset;
    }

    public void setRowOffset(int rowOffset) {
        checkAllowChange();
        this.rowOffset = rowOffset;
    }

    protected void checkAllowChange() {
        FreezeHelper.checkNotFrozen(this);
    }

    @Override
    public int getColOffset() {
        return colOffset;
    }

    public void setColOffset(int colOffset) {
        checkAllowChange();
        this.colOffset = colOffset;
    }

    @Override
    public boolean isProxyCell() {
        return true;
    }

    @Override
    public boolean isBlankCell() {
        return true;
    }

    @JsonIgnore
    @Override
    public ICell getRealCell() {
        return cell;
    }

    @Override
    public IRow getRow() {
        return row;
    }

    public void setRow(IRow row) {
        checkAllowChange();
        this.row = row;
    }

    @Override
    public void serializeToJson(IJsonHandler out) {
        out.beginObject(null);
        out.endObject();
    }

    public int getMergeAcross() {
        return 0;
    }

    public int getMergeDown() {
        return 0;
    }

    @Override
    public void setMergeAcross(int mergeAcross) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMergeDown(int mergeDown) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getValue() {
        return null;
    }

    public String getComment() {
        return null;
    }

    @Override
    public void setComment(String comment) {

    }

    @Override
    public void setValue(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getId() {
        return null;
    }

    public Object prop_get(String propName) {
        return null;
    }

    public boolean prop_has(String propName) {
        return false;
    }

    public void prop_set(String propName, Object value) {
        throw new UnsupportedOperationException();
    }
}