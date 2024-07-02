/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.model;

import io.nop.commons.util.CollectionHelper;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.ITableView;
import io.nop.core.reflect.hook.SerializableExtensibleObject;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelColumnConfig;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.XptCellModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Xpt报表的展开算法基本将行与列平等对待，因此使用ExpandedCol来管理每列的数据，与ExpandedRow的作用等价。
 */
public class ExpandedTable extends SerializableExtensibleObject implements ITableView {
    private final List<ExpandedRow> rows = new ArrayList<>();
    private final List<ExpandedCol> cols = new ArrayList<>();
    private ExpandedSheet sheet;
    private String id;
    private String styleId;
    private final Map<String, List<ExpandedCell>> namedCells = new HashMap<>();

    public ExpandedTable(ExcelTable table) {
        this.init(table);
    }

    public ExpandedTable(int rowCount, int colCount) {
        this.initEmptyTable(rowCount, colCount);
    }

    public void assignRowIndexAndColIndex() {
        List<ExpandedRow> rows = getRows();
        for (int i = 0, n = rows.size(); i < n; i++) {
            rows.get(i).setAssignedRowIndex(i);
        }

        List<ExpandedCol> cols = getCols();
        for (int i = 0, n = cols.size(); i < n; i++) {
            cols.get(i).setAssignedColIndex(i);
        }
    }

    private void initEmptyTable(int rowCount, int colCount) {
        for (int i = 0; i < colCount; i++) {
            ExpandedCol col = new ExpandedCol();
            col.setTable(this);
            cols.add(col);
        }
        for (int i = 0; i < rowCount; i++) {
            this.insertEmptyRow(i);
        }
    }

    private void init(ExcelTable table) {
        this.id = table.getId();
        this.styleId = table.getStyleId();
        int rowCount = table.getRowCount();
        int colCount = table.getColCount();

        initEmptyTable(rowCount, colCount);

        for (int i = 0; i < colCount; i++) {
            ExpandedCol col = cols.get(i);
            col.setColModel((ExcelColumnConfig) table.getCol(i));
        }

        Map<ExcelCell, ExpandedCell> cellMap = new IdentityHashMap<>();

        for (int i = 0; i < rowCount; i++) {
            ExcelRow row = table.getRow(i);

            ExpandedRow er = rows.get(i);
            er.setModel(row.getModel());
            er.setHeight(row.getHeight());
            er.setHidden(row.isHidden());

            ExpandedCell cell = er.getFirstCell();
            for (int j = 0; j < colCount; j++) {
                ICell ic = row.getCell(j);

                if (ic != null && !ic.isProxyCell()) {
                    ExcelCell ec = (ExcelCell) ic;
                    XptCellModel xptModel = ec.getModel();
                    cell.setModel(xptModel);
                    cell.setComment(ec.getComment());
                    cell.setValue(ec.getValue());
                    cell.setStyleId(ec.getStyleId());
                    cell.setId(ec.getId());
                    cell.setMergeDown(ec.getMergeDown());
                    cell.setMergeAcross(ec.getMergeAcross());
                    cell.markProxy();

                    addNamedCell(cell);

                    cellMap.put(ec, cell);
                }

                cell = cell.getRight();
            }
        }

        initParentChildren(cellMap);
    }

    public void addNamedCell(ExpandedCell cell) {
        String name = cell.getName();
        namedCells.computeIfAbsent(name, k -> new ArrayList<>()).add(cell);
    }

    public List<ExpandedCell> getNamedCells(String cellName) {
        return namedCells.get(cellName);
    }

    public ExpandedCell getNamedCell(String cellName) {
        List<ExpandedCell> cells = getNamedCells(cellName);
        return cells == null ? null : cells.get(0);
    }

    private void initParentChildren(Map<ExcelCell, ExpandedCell> cellMap) {
        for (ExpandedCell cell : cellMap.values()) {
            XptCellModel xptModel = cell.getModel();
            if (xptModel.getRowParentCell() != null) {
                cell.setRowParent(cellMap.get(xptModel.getRowParentCell()));
            }
            if (xptModel.getColParentCell() != null) {
                cell.setColParent(cellMap.get(xptModel.getColParentCell()));
            }

            if (!xptModel.getRowDuplicateCells().isEmpty()) {
                Map<String, List<ExpandedCell>> map = CollectionHelper.newHashMap(xptModel.getRowDuplicateCells().size());
                for (Map.Entry<String, ExcelCell> entry : xptModel.getRowDuplicateCells().entrySet()) {
                    map.put(entry.getKey(), toList(cellMap.get(entry.getValue())));
                }
                cell.setRowDescendants(map);
            }

            if (!xptModel.getColDuplicateCells().isEmpty()) {
                Map<String, List<ExpandedCell>> map = CollectionHelper.newHashMap(xptModel.getColDuplicateCells().size());
                for (Map.Entry<String, ExcelCell> entry : xptModel.getColDuplicateCells().entrySet()) {
                    map.put(entry.getKey(), toList(cellMap.get(entry.getValue())));
                }
                cell.setColDescendants(map);
            }
        }
    }

    private List<ExpandedCell> toList(ExpandedCell cell) {
        List<ExpandedCell> list = new ArrayList<>();
        list.add(cell);
        return list;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setStyleId(String styleId) {
        this.styleId = styleId;
    }

    public ExpandedSheet getSheet() {
        return sheet;
    }

    public void setSheet(ExpandedSheet sheet) {
        this.sheet = sheet;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getStyleId() {
        return styleId;
    }

    public List<ExpandedRow> getRows() {
        return rows;
    }

    public int getRowCount() {
        return rows.size();
    }

    public ExpandedRow getRow(int rowIndex) {
        if (rowIndex >= rows.size())
            return null;
        return rows.get(rowIndex);
    }

    public List<ExpandedCol> getCols() {
        return cols;
    }

    @Override
    public ExpandedCell getCell(int rowIndex, int colIndex) {
        return (ExpandedCell) ITableView.super.getCell(rowIndex, colIndex);
    }

    public int getColCount() {
        return cols.size();
    }

    public ExpandedCol getCol(int colIndex) {
        return cols.get(colIndex);
    }

    public boolean isNewlyCreatedCol(int colIndex) {
        if (colIndex >= cols.size()) {
            return false;
        }
        return cols.get(colIndex).isNewlyCreated();
    }

    public boolean isNewlyCreatedRow(int rowIndex) {
        if (rowIndex >= rows.size())
            return false;

        return rows.get(rowIndex).isNewlyCreated();
    }

    public ExpandedRow makeRow(int index) {
        if (index < rows.size()) {
            return rows.get(index);
        } else {
            ExpandedRow row = null;
            for (int i = rows.size(); i <= index; i++) {
                row = insertEmptyRow(i);
            }
            return row;
        }
    }

    public ExpandedCol makeCol(int index) {
        if (index < cols.size()) {
            return cols.get(index);
        } else {
            ExpandedCol col = null;
            for (int i = cols.size(); i <= index; i++) {
                col = insertEmptyCol(i);
            }
            return col;
        }
    }

    /**
     * 插入一个空行
     */
    public ExpandedRow insertEmptyRow(int index) {
        ExpandedRow row = newRow();
        if (index == 0) {
            ExpandedCell cell = row.getFirstCell();
            for (int i = 0, n = cols.size(); i < n; i++) {
                ExpandedCol col = cols.get(i);
                cell.setDown(col.getFirstCell());
                cell.setCol(col);
                col.setFirstCell(cell);
                cell = cell.getRight();
            }

            rows.add(0, row);
        } else {
            ExpandedRow prevRow = rows.get(index - 1);
            rows.add(index, row);

            ExpandedCell prevCell = prevRow.getFirstCell();

            ExpandedCell cell = row.getFirstCell();
            while (prevCell != null) {
                cell.setDown(prevCell.getDown());
                prevCell.setDown(cell);
                cell.setCol(prevCell.getCol());
                cell = cell.getRight();
                prevCell = prevCell.getRight();
            }
        }
        return row;
    }

    public ExpandedCol insertEmptyCol(int index) {
        ExpandedCol col = newCol();
        if (index == 0) {
            ExpandedCell cell = col.getFirstCell();
            for (int i = 0, n = rows.size(); i < n; i++) {
                ExpandedRow row = rows.get(i);
                cell.setDown(row.getFirstCell());
                cell.setRow(row);
                row.setFirstCell(cell);
                cell = cell.getDown();
            }

            cols.add(0, col);
        } else {
            ExpandedCol prevCol = cols.get(index - 1);
            cols.add(index, col);

            ExpandedCell prevCell = prevCol.getFirstCell();

            ExpandedCell cell = col.getFirstCell();
            while (prevCell != null) {
                cell.setRight(prevCell.getRight());
                prevCell.setRight(cell);
                cell.setRow(prevCell.getRow());
                cell = cell.getDown();
                prevCell = prevCell.getDown();
            }
        }
        return col;
    }

    private ExpandedRow newRow() {
        ExpandedRow row = new ExpandedRow();
        row.setTable(this);
        ExpandedCell prevCell = null;
        for (int i = 0, n = cols.size(); i < n; i++) {
            ExpandedCell cell = new ExpandedCell();
            cell.setRow(row);

            if (prevCell == null) {
                prevCell = cell;
                row.setFirstCell(cell);
            } else {
                prevCell.setRight(cell);
                prevCell = cell;
            }
        }
        return row;
    }

    private ExpandedCol newCol() {
        ExpandedCol col = new ExpandedCol();
        col.setTable(this);
        ExpandedCell prevCell = null;
        for (int i = 0, n = rows.size(); i < n; i++) {
            ExpandedCell cell = new ExpandedCell();
            cell.setCol(col);

            if (prevCell == null) {
                prevCell = cell;
                col.setFirstCell(cell);
            } else {
                prevCell.setDown(cell);
                prevCell = cell;
            }
        }
        return col;
    }

    public void removeRow(int rowIndex) {
        ExpandedRow row = rows.remove(rowIndex);
        ExpandedRow prevRow = rowIndex == 0 ? null : rows.get(rowIndex - 1);
        ExpandedCell prevCell = prevRow == null ? null : prevRow.getFirstCell();
        ExpandedCell cell = row.getFirstCell();
        ExpandedCell prevRealCell = null;

        while (cell != null) {
            ExpandedCell realCell = cell.getRealCell();

            if (cell.isProxyCell()) {
                if (cell.getRealCell() != prevRealCell)
                    realCell.changeRowSpan(-1);
                prevRealCell = cell.getRealCell();
            } else {
                realCell.changeRowSpan(-1);
                prevRealCell = cell;

                // 删除了左上角的单元格，但是单元格占据多行
                if (cell.getDown() != null && cell.getDown().getRealCell() == cell) {
                    ExpandedCell downCell = cell.getDown();
                    downCell.setRealCell(null);
                    downCell.setValue(cell.getValue());
                    downCell.setStyleId(cell.getStyleId());
                    downCell.setFormattedValue(cell.getFormattedValue());
                    downCell.setRowParent(cell.getRowParent());
                    downCell.setColParent(cell.getColParent());
                    downCell.setMergeDown(cell.getMergeDown());
                    downCell.setMergeAcross(cell.getMergeAcross());
                    downCell.markProxy();
                }
            }

            if (prevCell != null) {
                prevCell.setDown(cell.getDown());
                prevCell = prevCell.getRight();
            } else {
                cell.getCol().setFirstCell(cell.getDown());
            }

            cell = cell.getRight();
        }
    }

    public void removeCol(int colIndex) {
        ExpandedCol col = cols.remove(colIndex);
        ExpandedCol prevCol = colIndex == 0 ? null : cols.get(colIndex - 1);
        ExpandedCell prevCell = prevCol == null ? null : prevCol.getFirstCell();
        ExpandedCell cell = col.getFirstCell();
        ExpandedCell prevRealCell = null;

        while (cell != null) {
            ExpandedCell realCell = cell.getRealCell();

            if (cell.isProxyCell()) {
                if (cell.getRealCell() != prevRealCell)
                    realCell.changeColSpan(-1);
                prevRealCell = cell.getRealCell();
            } else {
                realCell.changeColSpan(-1);
                prevRealCell = cell;

                // 删除了左上角的单元格，但是单元格占据多行
                if (cell.getRight() != null && cell.getRight().getRealCell() == cell) {
                    ExpandedCell rightCell = cell.getRight();
                    rightCell.setRealCell(null);
                    rightCell.setValue(cell.getValue());
                    rightCell.setStyleId(cell.getStyleId());
                    rightCell.setFormattedValue(cell.getFormattedValue());
                    rightCell.setRowParent(cell.getRowParent());
                    rightCell.setColParent(cell.getColParent());
                    rightCell.setMergeDown(cell.getMergeDown());
                    rightCell.setMergeAcross(cell.getMergeAcross());
                    rightCell.markProxy();
                }
            }

            if (prevCell != null) {
                prevCell.setRight(cell.getRight());
                prevCell = prevCell.getDown();
            } else {
                cell.getRow().setFirstCell(cell.getRight());
            }

            cell = cell.getDown();
        }
    }
}