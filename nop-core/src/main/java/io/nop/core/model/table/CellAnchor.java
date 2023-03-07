/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table;

import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.IJsonSerializable;
import io.nop.core.resource.component.AbstractFreezable;

import java.io.Serializable;

/**
 * 在表格上定位的位置，例如图片在Excel表中的位置，图片的左上角和右下角都可能位于单元格的某个内部位置。 dx和dy的单位为pt
 */
public class CellAnchor extends AbstractFreezable implements Serializable, IJsonSerializable {

    private static final long serialVersionUID = -367036275965864813L;

    private int row1;
    private int col1;
    private int rowDelta = 1;
    private int colDelta = 1;
    private double dx1;
    private double dx2;
    private double dy1;
    private double dy2;

    public int getRow1() {
        return row1;
    }

    public void setRow1(int row1) {
        checkAllowChange();
        this.row1 = row1;
    }

    public CellAnchor row1(int row1) {
        setRow1(row1);
        return this;
    }

    public CellAnchor rowDelta(int rowDelta) {
        this.rowDelta = rowDelta;
        return this;
    }

    public CellAnchor colDelta(int colDelta) {
        this.colDelta = colDelta;
        return this;
    }

    public int getRowDelta() {
        return rowDelta;
    }

    public void setRowDelta(int rowDelta) {
        checkAllowChange();
        this.rowDelta = rowDelta;
    }

    public int getColDelta() {
        return colDelta;
    }

    public void setColDelta(int colDelta) {
        checkAllowChange();
        this.colDelta = colDelta;
    }

    public int getCol1() {
        return col1;
    }

    public void setCol1(int col1) {
        checkAllowChange();
        this.col1 = col1;
    }

    public CellAnchor col1(int col1) {
        setCol1(col1);
        return this;
    }

    public int getCol2() {
        return col1 + colDelta;
    }

    public double getDx1() {
        return dx1;
    }

    public void setDx1(double dx1) {
        checkAllowChange();
        this.dx1 = dx1;
    }

    public CellAnchor dx1(double dx1) {
        setDx1(dx1);
        return this;
    }

    public double getDx2() {
        return dx2;
    }

    public void setDx2(double dx2) {
        checkAllowChange();
        this.dx2 = dx2;
    }

    public CellAnchor dx2(double dx2) {
        setDx2(dx2);
        return this;
    }

    public double getDy1() {
        return dy1;
    }

    public void setDy1(double dy1) {
        checkAllowChange();
        this.dy1 = dy1;
    }

    public CellAnchor dy1(double dy1) {
        setDy1(dy1);
        return this;
    }

    public double getDy2() {
        return dy2;
    }

    public void setDy2(double dy2) {
        checkAllowChange();
        this.dy2 = dy2;
    }

    public CellAnchor dy2(double dy2) {
        setDy2(dy2);
        return this;
    }

    public int getRow2() {
        return row1 + rowDelta;
    }

    @Override
    public void serializeToJson(IJsonHandler out) {
        out.beginObject(getLocation());
        outputJson(out);
        out.endObject();
    }

    protected void outputJson(IJsonHandler out) {
        out.put("row1", row1);
        out.put("col1", col1);
        out.put("rowDelta", rowDelta);
        out.put("colDelta", colDelta);
        out.put("dx1", dx1);
        out.put("dx2", dx2);
        out.put("dy1", dy1);
        out.put("dy2", dy2);
    }
}