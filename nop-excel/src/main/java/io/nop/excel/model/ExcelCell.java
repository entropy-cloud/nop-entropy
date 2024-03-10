/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import io.nop.core.model.table.ICell;
import io.nop.excel.model._gen._ExcelCell;

public class ExcelCell extends _ExcelCell {
    private ExcelCell realCell;
    private int rowOffset;
    private int colOffset;

    public ExcelCell() {

    }

    public boolean isProxyCell() {
        return realCell != null && realCell != this;
    }


    @Override
    public ExcelCell getRealCell() {
        if (realCell == null)
            return this;

        return realCell;
    }

    public void setRealCell(ExcelCell realCell) {
        this.realCell = realCell;
    }

    @Override
    public int getRowOffset() {
        return rowOffset;
    }

    @Override
    public void setRowOffset(int rowOffset) {
        this.rowOffset = rowOffset;
    }

    @Override
    public int getColOffset() {
        return colOffset;
    }

    @Override
    public void setColOffset(int colOffset) {
        this.colOffset = colOffset;
    }

    public String toString() {
        return getClass().getSimpleName() + "[text=" + getText() + (getModelCellName() == null ? "," + getModelCellName() : "")
                + (isProxyCell() ? ",proxy" : "") + ",loc=" + getLocation() + "]";
    }

    public String getModelCellName() {
        XptCellModel cellModel = getModel();
        return cellModel == null ? null : cellModel.getName();
    }

    public XptCellModel makeModel() {
        XptCellModel model = getModel();
        if (model == null) {
            model = new XptCellModel();
            setModel(model);
        }
        return model;
    }

    @Override
    public ExcelCell cloneInstance() {
        ExcelCell cell = new ExcelCell();
        cell.setLocation(getLocation());
        cell.setStyleId(getStyleId());
        cell.setComment(getComment());
        cell.setType(getType());
        cell.setValue(getValue());
        cell.setFormula(getFormula());
        cell.setRichText(getRichText());
        cell.setMergeAcross(getMergeAcross());
        cell.setMergeDown(getMergeDown());
        cell.setModel(getModel());
        cell.setId(getId());
        return cell;
    }

    @Override
    public boolean isExportFormattedValue() {
        XptCellModel model = getModel();
        return model != null && model.isExportFormattedValue();
    }

    public int getRowIndex() {
        return getRow().getRowIndex();
    }

    public int getColIndex() {
        return getRow().getCells().indexOf(this);
    }

    public ExcelCell getTopRealCell() {
        ExcelCell real = getRealCell();
        int rowIndex = real.getRowIndex();
        if (rowIndex <= 0)
            return null;

        int index = real.getColIndex();

        ICell cell = getRow().getTable().getRow(rowIndex - 1).getCell(index);
        if (cell == null)
            return null;
        return (ExcelCell) cell.getRealCell();
    }


    public ExcelCell getDownRealCell() {
        ExcelCell real = getRealCell();
        int rowIndex = real.getRowIndex();
        if (rowIndex == real.getRow().getTable().getRowCount() - 1)
            return null;

        int index = real.getColIndex();

        ICell cell = getRow().getTable().getRow(rowIndex + 1).getCell(index);
        if (cell == null)
            return null;
        return (ExcelCell) cell.getRealCell();
    }

    public ExcelCell getLeftRealCell() {
        ExcelCell real = getRealCell();
        int rowIndex = real.getRowIndex();

        int index = real.getColIndex();
        if (index <= 0)
            return null;

        ICell cell = getRow().getTable().getRow(rowIndex).getCell(index - 1);
        if (cell == null)
            return null;
        return (ExcelCell) cell.getRealCell();
    }

    public ExcelCell getRightRealCell() {
        ExcelCell real = getRealCell();
        int rowIndex = real.getRowIndex();

        int index = real.getColIndex();
        if (index == real.getRow().getColCount() - 1)
            return null;

        ICell cell = getRow().getTable().getRow(rowIndex).getCell(index + 1);
        if (cell == null)
            return null;
        return (ExcelCell) cell.getRealCell();
    }
}