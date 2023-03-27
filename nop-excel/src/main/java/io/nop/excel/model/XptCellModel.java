/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.core.model.table.CellPosition;
import io.nop.excel.model._gen._XptCellModel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class XptCellModel extends _XptCellModel {
    public XptCellModel() {

    }

    private String name;
    private int rowIndex;
    private int colIndex;

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

    @JsonIgnore
    public CellPosition getCellPosition() {
        return CellPosition.of(rowIndex, colIndex);
    }

    @JsonIgnore
    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    @JsonIgnore
    public int getColIndex() {
        return colIndex;
    }

    public void setColIndex(int colIndex) {
        this.colIndex = colIndex;
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