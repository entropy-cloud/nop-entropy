/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.core.model.table.CellPosition;
import io.nop.excel.model._gen._XptCellModel;
import io.nop.excel.model.constants.XptExpandType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class XptCellModel extends _XptCellModel {
    public XptCellModel() {

    }

    private ExcelCell cell;

    private String name;
    private CellPosition cellPosition;

    // 单元格及所有子单元格所构成的区块的起始位置（从当前index算起）
    private int rowExpandOffset;

    // 单元格及所有子单元格所构成的区块的大小
    private int rowExpandSpan;

    private int colExpandSpan;

    private int colExpandOffset;

    private int rowExpandLevel;

    private int colExpandLevel;

    private Map<String, ExcelCell> rowExtendCells = Collections.emptyMap();

    // 递归包含所有子节点
    private Map<String, ExcelCell> rowDuplicateCells = Collections.emptyMap();

    private Map<String, ExcelCell> rowChildCells = Collections.emptyMap();

    private Map<String, ExcelCell> colExtendCells = Collections.emptyMap();

    private Map<String, ExcelCell> colDuplicateCells = Collections.emptyMap();

    private Map<String, ExcelCell> colChildCells = Collections.emptyMap();

    private ExcelCell rowParentCell;
    private ExcelCell colParentCell;

    public ExcelCell getCell() {
        return cell;
    }

    public void setCell(ExcelCell cell) {
        this.cell = cell;
    }

    public boolean isTopRowCell() {
        return getRowParent() == null || getRowParent() == CellPosition.NONE;
    }

    public boolean isTopColCell() {
        return getColParent() == null || getColParent() == CellPosition.NONE;
    }

    @JsonIgnore
    public CellPosition getCellPosition() {
        return cellPosition;
    }

    public void setCellPosition(CellPosition cellPosition) {
        this.cellPosition = cellPosition;
    }

    public boolean isColExpandOrColParentExpand() {
        if (getExpandType() == XptExpandType.c)
            return true;
        if (colParentCell != null)
            return colParentCell.getModel().isColExpandOrColParentExpand();
        return false;
    }

    public boolean isRowExpandOrRowParentExpand() {
        if (getExpandType() == XptExpandType.r)
            return true;
        if (rowParentCell != null)
            return rowParentCell.getModel().isRowExpandOrRowParentExpand();
        return false;
    }

    public ExcelCell getExpandableColParent() {
        if (colParentCell == null)
            return null;
        if (colParentCell.getModel().getExpandType() != XptExpandType.c)
            return colParentCell.getModel().getExpandableColParent();
        return colParentCell;
    }

    public ExcelCell getExpandableRowParent() {
        if (rowParentCell == null)
            return null;
        if (rowParentCell.getModel().getExpandType() != XptExpandType.r)
            return rowParentCell.getModel().getExpandableRowParent();
        return rowParentCell;
    }

    @JsonIgnore
    public int getRowIndex() {
        if (cellPosition == null)
            return -1;
        return cellPosition.getRowIndex();
    }

    @JsonIgnore
    public int getColIndex() {
        if (cellPosition == null)
            return -1;
        return cellPosition.getColIndex();
    }

    public boolean shouldRemoveEmpty() {
        return Boolean.FALSE.equals(getKeepExpandEmpty());
    }

    public boolean isRowDuplicate(String name) {
        return rowDuplicateCells.containsKey(name);
    }

    public ExcelCell getRowParent(String name) {
        if (rowParentCell == null)
            return null;
        XptCellModel cellModel = rowParentCell.getModel();
        if (cellModel == null)
            return null;

        if (cellModel.getName().equals(name))
            return rowParentCell;
        return cellModel.getRowParent(name);
    }

    public ExcelCell getColParent(String name) {
        if (colParentCell == null)
            return null;
        XptCellModel cellModel = colParentCell.getModel();
        if (cellModel == null)
            return null;

        if (cellModel.getName().equals(name))
            return colParentCell;
        return cellModel.getColParent(name);
    }

    public boolean isColDuplicate(String name) {
        return colDuplicateCells.containsKey(name);
    }

    public void addRowChildCell(ExcelCell cell) {
        if (rowChildCells.isEmpty()) {
            rowChildCells = new LinkedHashMap<>();
        }
        XptCellModel model = cell.getModel();
        rowChildCells.put(model.getName(), cell);
    }

    public void addColChildCell(ExcelCell cell) {
        if (colChildCells.isEmpty()) {
            colChildCells = new LinkedHashMap<>();
        }
        XptCellModel model = cell.getModel();
        colChildCells.put(model.getName(), cell);
    }

    public void addRowExtendCell(ExcelCell cell) {
        if (rowExtendCells.isEmpty()) {
            rowExtendCells = new LinkedHashMap<>();
        }
        XptCellModel model = cell.getModel();
        rowExtendCells.put(model.getName(), cell);
    }

    public void addColExtendCell(ExcelCell cell) {
        if (colExtendCells.isEmpty()) {
            colExtendCells = new LinkedHashMap<>();
        }
        XptCellModel model = cell.getModel();
        colExtendCells.put(model.getName(), cell);
    }

    public void removeColExtendCell(String cellName) {
        if (colExtendCells != null)
            colExtendCells.remove(cellName);
    }

    public void removeRowExtendCell(String cellName) {
        if (rowExtendCells != null)
            rowExtendCells.remove(cellName);
    }

    @JsonIgnore
    public int getRowExpandLevel() {
        return rowExpandLevel;
    }

    public void setRowExpandLevel(int expandLevel) {
        this.rowExpandLevel = expandLevel;
    }

    public int getColExpandLevel() {
        return colExpandLevel;
    }

    public void setColExpandLevel(int colExpandLevel) {
        this.colExpandLevel = colExpandLevel;
    }

    @JsonIgnore
    public Map<String, ExcelCell> getRowChildCells() {
        return rowChildCells;
    }

    @JsonIgnore
    public Map<String, ExcelCell> getColExtendCells() {
        return colExtendCells;
    }

    @JsonIgnore
    public Map<String, ExcelCell> getColDuplicateCells() {
        return colDuplicateCells;
    }

    @JsonIgnore
    public Map<String, ExcelCell> getColChildCells() {
        return colChildCells;
    }

    @JsonIgnore
    public ExcelCell getRowParentCell() {
        return rowParentCell;
    }

    public void setRowParentCell(ExcelCell rowParentCell) {
        this.rowParentCell = rowParentCell;
    }

    @JsonIgnore
    public ExcelCell getColParentCell() {
        return colParentCell;
    }

    public void setColParentCell(ExcelCell colParentCell) {
        this.colParentCell = colParentCell;
    }

    @JsonIgnore
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonIgnore
    public int getRowExpandOffset() {
        return rowExpandOffset;
    }

    public void setRowExpandOffset(int expandOffset) {
        this.rowExpandOffset = expandOffset;
    }

    @JsonIgnore
    public int getRowExpandSpan() {
        return rowExpandSpan;
    }

    public void setRowExpandSpan(int expandSpan) {
        this.rowExpandSpan = expandSpan;
    }

    public int getColExpandSpan() {
        return colExpandSpan;
    }

    public void setColExpandSpan(int colExpandSpan) {
        this.colExpandSpan = colExpandSpan;
    }

    public int getColExpandOffset() {
        return colExpandOffset;
    }

    public void setColExpandOffset(int colExpandOffset) {
        this.colExpandOffset = colExpandOffset;
    }

    @JsonIgnore
    public Map<String, ExcelCell> getRowExtendCells() {
        return rowExtendCells;
    }

    @JsonIgnore
    public Map<String, ExcelCell> getRowDuplicateCells() {
        return rowDuplicateCells;
    }

    public void addRowDuplicateCell(ExcelCell cell) {
        if (rowDuplicateCells.isEmpty()) {
            rowDuplicateCells = new LinkedHashMap<>();
        }
        rowDuplicateCells.put(cell.getModel().getName(), cell);
    }

    public void addColDuplicateCell(ExcelCell cell) {
        if (colDuplicateCells.isEmpty()) {
            colDuplicateCells = new LinkedHashMap<>();
        }
        colDuplicateCells.put(cell.getModel().getName(), cell);
    }

    public void addRowDuplicateCells(Map<String, ExcelCell> cells) {
        if (cells.isEmpty())
            return;

        if (rowDuplicateCells.isEmpty()) {
            rowDuplicateCells = new LinkedHashMap<>();
        }
        rowDuplicateCells.putAll(cells);
    }

    public void addColDuplicateCells(Map<String, ExcelCell> cells) {
        if (cells.isEmpty())
            return;

        if (colDuplicateCells.isEmpty()) {
            colDuplicateCells = new LinkedHashMap<>();
        }
        colDuplicateCells.putAll(cells);
    }
}