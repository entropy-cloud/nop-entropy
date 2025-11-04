/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.table.impl;

import io.nop.api.core.util.ProcessResult;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.IRowView;
import io.nop.core.model.table.ITableView;

import java.util.ArrayList;
import java.util.List;

public class BaseTable extends AbstractTable<BaseRow> {

    private static final long serialVersionUID = -4662561617378749671L;

    private List<BaseRow> rows;
    private List<BaseColumnConfig> cols = new ArrayList<>();
    private Object model;

    public BaseTable(int rowCount) {
        this.rows = new ArrayList<>(rowCount);
    }

    public BaseTable() {
        this.rows = new ArrayList<>();
    }

    public Object getModel() {
        return model;
    }

    public void setModel(Object model) {
        this.model = model;
    }

    @Override
    public List<BaseColumnConfig> getCols() {
        return cols;
    }

    public void setCols(List<BaseColumnConfig> cols) {
        this.cols = cols;
    }

    @Override
    public List<BaseRow> getRows() {
        return rows;
    }

    @Override
    public BaseRow newRow() {
        return new BaseRow();
    }

    @Override
    protected void outputJson(IJsonHandler handler) {
        super.outputJson(handler);

        handler.put("cols", this.getCols());
        handler.put("rows", this.getRows());
    }

    public static BaseTable extract(ITableView table, int firstRow, int firstCol, int lastRow, int lastCol) {
        if (table == null) {
            throw new IllegalArgumentException("Table cannot be null");
        }
        if (firstRow > lastRow || firstCol > lastCol) {
            throw new IllegalArgumentException("Invalid range: firstRow > lastRow or firstCol > lastCol");
        }

        BaseTable ret = new BaseTable();
        List<BaseColumnConfig> cols = new ArrayList<>();
        for (int i = firstCol; i <= lastCol; i++) {
            cols.add(BaseColumnConfig.from(table.getCol(i)));
        }
        ret.setCols(cols);

        for (int i = firstRow; i <= lastRow; i++) {
            IRowView row = table.getRow(i);
            if (row == null) {
                continue;
            }
            ret.makeRow(i).setHeight(row.getHeight());

            row.forEachCell(i, (cell, rowIndex, colIndex) -> {
                if (colIndex < firstCol || colIndex > lastCol) {
                    return ProcessResult.CONTINUE;
                }

                if (cell.isProxyCell()) {
                    // 上边沿
                    if (rowIndex == firstRow) {
                        // 这里特殊考虑了左上角的单元格
                        if (cell.getColOffset() == 0 || (colIndex == firstCol)) {
                            // 从上侧延展过来的单元格
                            ICellView realCell = cell.getRealCell();
                            BaseCell copy = newBaseCell(realCell);
                            copy.setMergeAcross(Math.min(realCell.getMergeAcross() - cell.getColOffset(), lastCol - colIndex));
                            copy.setMergeDown(Math.min(realCell.getMergeDown() - cell.getRowOffset(), lastRow - rowIndex));
                            ret.setCell(0, colIndex - firstCol, copy);
                        }
                    } else if (colIndex == firstCol) {
                        // 左边沿。左上角单元格已经在上一个分支中处理，这里不用考虑
                        if (cell.getRowOffset() == 0) {
                            // 从左侧延展过来的单元格
                            ICellView realCell = cell.getRealCell();
                            BaseCell copy = newBaseCell(realCell);
                            copy.setMergeAcross(Math.min(realCell.getMergeAcross() - cell.getColOffset(), lastCol - colIndex));
                            copy.setMergeDown(Math.min(realCell.getMergeDown(), lastRow - rowIndex));
                            ret.setCell(rowIndex - firstRow, 0, copy);
                        }
                    }
                } else {
                    BaseCell copy = newBaseCell(cell);
                    // 避免单元格范围超出限制
                    copy.setMergeAcross(Math.min(cell.getMergeAcross(), lastCol - colIndex));
                    copy.setMergeDown(Math.min(cell.getMergeDown(), lastRow - rowIndex));
                    ret.setCell(rowIndex - firstRow, colIndex - firstCol, copy);
                }
                return ProcessResult.CONTINUE;
            });
        }
        return ret;
    }

    static BaseCell newBaseCell(ICellView cell) {
        BaseCell copy = new BaseCell();
        copy.setId(cell.getId());
        copy.setStyleId(cell.getStyleId());
        copy.setComment(cell.getComment());
        copy.setValue(cell.getText());
        return copy;
    }
}