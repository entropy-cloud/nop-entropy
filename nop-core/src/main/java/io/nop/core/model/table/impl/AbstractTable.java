/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.ArrayHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.IColumnConfig;
import io.nop.core.model.table.IRow;
import io.nop.core.model.table.ITable;
import io.nop.core.model.table.html.HtmlTableOutput;
import io.nop.core.resource.component.AbstractComponentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.nop.core.CoreErrors.*;

public abstract class AbstractTable<T extends IRow> extends AbstractComponentModel implements ITable<T> {

    private static final long serialVersionUID = -6856729605246558386L;

    static final Logger LOG = LoggerFactory.getLogger(AbstractTable.class);

    private String id;
    private String styleId;
    private int colCount = -1;
    private int headerCount;
    private int sideCount;
    private int footerCount;

    public String toString() {
        return "Table[id=" + id + ",rowCount=" + getRows().size() + ",colCount=" + this.getColCount() + "]";
    }

    @Override
    public abstract List<? extends IColumnConfig> getCols();

    public void freeze(boolean cascade) {
        super.freeze(cascade);
        if (cascade) {
            for (IRow row : getRows()) {
                row.freeze(true);
            }
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String tableId) {
        checkAllowChange();
        this.id = tableId;
    }

    public String getStyleId() {
        return styleId;
    }

    public void setStyleId(String styleId) {
        checkAllowChange();
        this.styleId = styleId;
    }

    @JsonIgnore
    @Override
    public int getRowCount() {
        return getRows().size();
    }

    @JsonIgnore
    @Override
    public int getColCount() {
        if (colCount < 0) {
            int max = 0;
            for (T row : getRows()) {
                max = Math.max(max, row.getColCount());
            }
            colCount = max;
        }
        return colCount;
    }

    @Override
    public int getHeaderCount() {
        return headerCount;
    }

    @Override
    public void setHeaderCount(int headerCount) {
        checkAllowChange();
        this.headerCount = headerCount;
    }

    @Override
    public int getSideCount() {
        return sideCount;
    }

    @Override
    public void setSideCount(int sideCount) {
        checkAllowChange();
        this.sideCount = sideCount;
    }

    @Override
    public int getFooterCount() {
        return footerCount;
    }

    @Override
    public void setFooterCount(int footerCount) {
        checkAllowChange();
        this.footerCount = footerCount;
    }

    public abstract List<T> getRows();

    @Override
    public T getRow(int rowIndex) {
        List<T> rows = getRows();
        if (rowIndex < 0 || rowIndex >= rows.size())
            return null;
        return rows.get(rowIndex);
    }

    @Override
    public T makeRow(int rowIndex) {
        List<T> rows = getRows();
        for (int i = rows.size(); i <= rowIndex; i++) {
            checkAllowChange();
            T row = newRow();
            row.setTable(this);
            rows.add(row);
        }
        return rows.get(rowIndex);
    }

    @Override
    public void insertRow(int rowIndex) {
        insertRows(rowIndex, 1, null);
    }

    @Override
    public void insertRows(int rowIndex, int count, int[] extendCols) {
        checkAllowChange();
        List<T> rows = getRows();
        if (rowIndex >= rows.size()) {
            for (int i = rows.size(); i < rowIndex + count; i++) {
                T row = newRow();
                row.setTable(this);
                rows.add(row);
            }
            if (extendCols == null)
                return;
        } else {
            for (int i = 0; i < count; i++) {
                T row = newRow();
                row.setTable(this);
                rows.add(rowIndex, row);
            }
        }

        if (rowIndex > 0) {
            _expandProxy(rowIndex, count, extendCols);
        }
    }

    // 新增行导致合并单元格被扩展
    void _expandProxy(int rowIndex, int count, int[] extendCols) {
        // 在修改realCell属性之前先延展下方的proxy
        IRow afterRow = getRow(rowIndex + count);
        if (afterRow != null) {
            List<? extends ICell> cells = afterRow.getCells();
            for (int j = 0, m = cells.size(); j < m; j++) {
                ICell icell = cells.get(j);
                if (icell == null)
                    continue;

                ICell cell = icell.getRealCell();
                // 上方延展下来的
                if (icell.getRowOffset() > 0) {
                    // 下方的proxy单元格的rowOffset+count
                    int startRow = rowIndex + count;
                    int lastRow = startRow + cell.getMergeDown() - icell.getRowOffset();

                    for (int p = startRow; p <= lastRow; p++) {
                        for (int k = 0; k <= cell.getMergeAcross(); k++) {
                            ICell proxy = getProxy(p, j + k);
                            proxy.setRowOffset(proxy.getRowOffset() + count);
                        }
                    }
                }
                j += cell.getMergeAcross();
            }
        }

        // 如果前面的行要向下延展，则在插入的空行中补充proxy
        IRow beforeRow = getRow(rowIndex - 1);
        if (beforeRow != null) {
            List<? extends ICell> cells = beforeRow.getCells();
            for (int j = 0, m = cells.size(); j < m; j++) {
                ICell icell = cells.get(j);
                if (icell == null)
                    continue;

                ICell cell = icell.getRealCell();

                // 如果指定延展或者跨越了插入行
                if (ArrayHelper.indexOf(extendCols, j) >= 0 || cell.getMergeDown() > icell.getRowOffset()) {
                    cell.setMergeDown(cell.getMergeDown() + count);

                    for (int i = 0; i < count; i++) {
                        for (int k = 0; k <= cell.getMergeAcross(); k++) {
                            ICell proxy = newProxyCell(cell, icell.getRowOffset() + i + 1, k);
                            getRow(rowIndex + i).internalSetCell(j + k, proxy);
                        }
                    }
                }
                j += cell.getMergeAcross();
            }
        }
    }

    @Override
    public void removeRow(int rowIndex) {
        removeRows(rowIndex, 1);
    }

    @Override
    public void removeRows(int rowIndex, int count) {
        checkAllowChange();
        List<T> rows = getRows();
        if (rowIndex >= rows.size())
            return;
        if (rowIndex + count > rows.size()) {
            count = rows.size() - rowIndex;
        }
        _removeProxy(rowIndex, count);
        for (int i = 0; i < count; i++) {
            rows.remove(rowIndex);
        }
        this.invalidateColCount();
    }

    void _removeProxy(int rowIndex, int count) {
        IRow row = getRow(rowIndex);
        List<? extends ICell> cells = row.getCells();
        for (int j = 0, m = cells.size(); j < m; j++) {
            ICell icell = cells.get(j);
            if (icell == null)
                continue;
            ICell cell = icell.getRealCell();
            if (icell.isProxyCell()) {
                // 贯穿
                if (cell.getMergeDown() - icell.getRowOffset() >= count) { //NOPMD - suppressed EmptyControlStatement
                    // 后面再处理
                    // ignore
                } else {
                    cell.setMergeDown(icell.getRowOffset() - 1);
                }
            }
            j += cell.getMergeAcross();
        }

        row = getRow(rowIndex + count);
        if (row != null) {
            cells = row.getCells();
            for (int j = 0, m = cells.size(); j < m; j++) {
                ICell icell = cells.get(j);
                if (icell == null)
                    continue;

                ICell cell = icell.getRealCell();
                // 上方延展下来的。 rowOffset>0必然是proxy
                if (icell.getRowOffset() > 0) {
                    int rowOffset = icell.getRowOffset();
                    int start = rowIndex + count - rowOffset;

                    if (rowOffset > count) {
                        // 贯穿下来的单元格
                        for (int p = rowOffset, n = cell.getMergeDown(); p <= n; p++) {
                            for (int k = 0; k <= cell.getMergeAcross(); k++) {
                                ICell proxy = getProxy(start + p, j + k);
                                proxy.setRowOffset(p - count);
                            }
                        }
                        cell.setMergeDown(cell.getMergeDown() - count);
                    } else {
                        // RealCell在被删除的区间内
                        this.doRemoveCell(icell, rowIndex + count, j);
                    }
                }
                j += cell.getMergeAcross();
            }
        }
    }

    void invalidateColCount() {
        colCount = -1;
    }

    public void validate() {
        List<T> rows = getRows();
        for (int i = 0, n = rows.size(); i < n; i++) {
            IRow row = rows.get(i);
            int cols = row.getColCount();
            for (int j = 0; j < cols; j++) {
                ICell cell = row.getCell(j);
                if (cell == null)
                    continue;
                if (cell.isProxyCell()) {
                    validateProxy(cell, i, j);
                } else {
                    validateRealCell(cell, i, j);
                }
                j += cell.getRealCell().getMergeAcross();
            }
        }
    }

    void validateProxy(ICell cell, int rowIndex, int colIndex) {
        ICell realCell = getCell(rowIndex - cell.getRowOffset(), colIndex - cell.getColOffset());
        if (realCell != cell.getRealCell())
            throw new NopException(ERR_TABLE_INVALID_PROXY_CELL).param(ARG_CELL, cell).param(ARG_ROW_INDEX, rowIndex)
                    .param(ARG_COL_INDEX, colIndex);
    }

    void validateRealCell(ICell cell, int rowIndex, int colIndex) {
        for (int i = 0; i <= cell.getMergeDown(); i++) {
            for (int j = 0; j <= cell.getMergeAcross(); j++) {
                if (i == 0 && j == 0)
                    continue;
                ICell proxy = getCell(rowIndex + i, colIndex + j);
                if (proxy == null || !proxy.isProxyCell())
                    throw new NopException(ERR_TABLE_NOT_PROXY_CELL).param(ARG_ROW_INDEX, rowIndex + i)
                            .param(ARG_COL_INDEX, colIndex + j);

                if (proxy.getRowOffset() != i || proxy.getColOffset() != j)
                    throw new NopException(ERR_TABLE_INVALID_PROXY_CELL).param(ARG_CELL, proxy)
                            .param(ARG_ROW_INDEX, rowIndex + i).param(ARG_COL_INDEX, rowIndex + j);
            }
        }
    }

    @Override
    public void insertCol(int colIndex) {
        insertCols(colIndex, 1, null);
    }

    @Override
    public void insertCols(int colIndex, int count, int[] extendRows) {
        checkAllowChange();
        invalidateColCount();
        _insertCols(colIndex, count);

        if (colIndex == 0)
            return;

        List<T> rows = getRows();
        for (int i = 0, n = rows.size(); i < n; i++) {
            IRow row = rows.get(i);

            ICell icell = row.getCell(colIndex + count);
            if (icell == null)
                continue;

            ICell cell = icell.getRealCell();
            if (icell.getColOffset() > 0) {
                int colOffset = icell.getColOffset();
                int start = colIndex + count - colOffset;
                for (int p = 0; p <= cell.getMergeDown(); p++) {
                    for (int k = colOffset; k <= cell.getMergeAcross(); k++) {
                        ICell proxy = getProxy(i + p, start + k);
                        proxy.setColOffset(k + count);
                    }
                }
            }
            i += cell.getMergeDown();
        }

        for (int i = 0, n = rows.size(); i < n; i++) {
            IRow row = rows.get(i);

            ICell icell = row.getCell(colIndex - 1);
            if (icell == null)
                continue;

            ICell cell = icell.getRealCell();
            if (ArrayHelper.indexOf(extendRows, i) >= 0 || cell.getMergeAcross() > icell.getColOffset()) {
                cell.setMergeAcross(cell.getMergeAcross() + count);
                int colOffset = icell.getColOffset();
                for (int p = 0; p <= cell.getMergeDown(); p++) {
                    IRow row2 = getRow(i + p);
                    for (int k = 0; k < count; k++) {
                        ICell proxy = newProxyCell(cell, p, colOffset + k + 1);
                        row2.internalSetCell(colIndex + k, proxy);
                    }
                }
            }
            i += cell.getMergeDown();
        }
    }

    void _insertCols(int colIndex, int count) {
        List<T> rows = getRows();
        for (int i = 0, n = rows.size(); i < n; i++) {
            IRow row = rows.get(i);
            int cols = row.getColCount();
            // 如果行的列数较少，则直接跳过，没必要添加，setCell时会自动增加列
            if (colIndex >= cols)
                continue;

            for (int k = 0; k < count; k++) {
                row.internalInsertCell(colIndex, null);
            }
        }
    }

    @Override
    public void removeCol(int colIndex) {
        removeCols(colIndex, 1);
    }

    @Override
    public void removeCols(int colIndex, int count) {
        checkAllowChange();
        int colCount = getColCount();
        if (colIndex >= colCount)
            return;

        if (colIndex + count > colCount) {
            count = colCount - colIndex;
        }

        invalidateColCount();

        List<T> rows = getRows();
        for (int i = 0, n = rows.size(); i < n; i++) {
            IRow row = rows.get(i);

            ICell icell = row.getCell(colIndex);
            if (icell == null)
                continue;

            ICell cell = icell.getRealCell();
            if (icell.isProxyCell()) {
                // 贯穿
                if (cell.getMergeAcross() - icell.getColOffset() >= count) { //NOPMD - suppressed EmptyControlStatement
                    // 后面再处理
                    // ignore
                } else {
                    cell.setMergeAcross(icell.getColOffset() - 1);
                }
            }
            i += cell.getMergeDown();
        }

        for (int i = 0, n = rows.size(); i < n; i++) {
            IRow row = rows.get(i);

            ICell icell = row.getCell(colIndex + count);
            if (icell == null)
                continue;

            ICell cell = icell.getRealCell();
            // 左方延展过来的
            if (icell.getColOffset() > 0) {
                int colOffset = icell.getColOffset();
                if (colOffset > count) {
                    int start = colIndex + count - colOffset;
                    for (int p = colOffset, m = cell.getMergeAcross(); p <= m; p++) {
                        for (int k = 0; k <= cell.getMergeDown(); k++) {
                            ICell proxy = getProxy(i + k, start + p);
                            proxy.setColOffset(p - count);
                        }
                    }
                    cell.setMergeAcross(cell.getMergeAcross() - count);
                } else {
                    // RealCell在被删除的区间中
                    doRemoveCell(icell, i, colIndex + count);
                }
            }
            i += cell.getMergeDown();
        }

        _deleteCols(colIndex, count);
    }

    void _deleteCols(int colIndex, int count) {
        List<T> rows = getRows();
        for (int i = 0, n = this.getRowCount(); i < n; i++) {
            IRow row = rows.get(i);
            int cols = row.getColCount();
            if (cols <= colIndex)
                continue;

            int m = Math.min(cols, colIndex + count);
            for (int j = m - 1; j >= colIndex; j--) {
                row.internalRemoveCell(j);
            }
        }
    }

    ICell getProxy(int rowIndex, int colIndex) {
        ICell cell = getCell(rowIndex, colIndex);
        if (cell == null || !cell.isProxyCell())
            throw new NopException(ERR_TABLE_NOT_PROXY_CELL).param(ARG_ROW_INDEX, rowIndex).param(ARG_COL_INDEX,
                    colIndex);
        return cell;
    }

    @Override
    public ICell getCell(int rowIndex, int colIndex) {
        IRow row = getRow(rowIndex);
        if (row == null)
            return null;
        return row.getCell(colIndex);
    }

    @Override
    public ICell makeCell(int rowIndex, int colIndex) {
        IRow row = makeRow(rowIndex);
        return row.makeCell(colIndex);
    }

    @Override
    public void setCell(int rowIndex, int colIndex, ICell cell) {
        checkAllowChange();
        if (colCount >= 0 && colIndex + cell.getMergeAcross() >= colCount)
            invalidateColCount();

        if (cell == null) {
            internalSetCell(makeRow(rowIndex), rowIndex, colIndex, null);
            return;
        }

        Guard.checkArgument(!cell.isProxyCell(), "only RealCell is allowed");
        doSetCell(makeRow(rowIndex), rowIndex, colIndex, cell);
    }

    void doSetCell(IRow row, int rowIndex, int colIndex, ICell cell) {
        internalSetCell(row, rowIndex, colIndex, cell);

        int mergeAcross = cell.getMergeAcross();
        int mergeDown = cell.getMergeDown();

        if (mergeAcross > 0) {
            for (int i = 1; i <= mergeAcross; i++) {
                ICell proxyCell = newProxyCell(cell, 0, i);
                cell.setRow(row);
                internalSetCell(row, rowIndex, colIndex + i, proxyCell);
            }
        }
        if (mergeDown > 0) {
            for (int i = 1; i <= mergeDown; i++) {
                IRow nextRow = makeRow(rowIndex + i);
                for (int j = 0; j <= mergeAcross; j++) {
                    ICell proxyCell = newProxyCell(cell, i, j);
                    internalSetCell(nextRow, rowIndex + i, colIndex + j, proxyCell);
                }
            }
        }
    }

    void internalSetCell(IRow row, int rowIndex, int colIndex, ICell cell) {
        ICell oldCell = row.getCell(colIndex);
        if (oldCell == cell)
            return;

        if (oldCell != null) {
            this.doRemoveCell(oldCell, rowIndex, colIndex);
        }
        row.internalSetCell(colIndex, cell);
    }

    void doRemoveCell(ICell cell, int rowIndex, int colIndex) {
        if (cell.isProxyCell()) {
            rowIndex -= cell.getRowOffset();
            colIndex -= cell.getColOffset();
            cell = cell.getRealCell();
        }

        for (int i = 0, n = cell.getMergeDown(); i <= n; i++) {
            for (int j = 0, m = cell.getMergeAcross(); j <= m; j++) {
                IRow row = getRow(i + rowIndex);
                if (row != null) {
                    row.internalSetCell(j + colIndex, null);
                }
            }
        }
    }

    @Override
    public void mergeCell(int rowIndex, int colIndex, int mergeDown, int mergeAcross) {
        checkAllowChange();
        ICell cell = this.makeCell(rowIndex, colIndex);
        if (cell.isProxyCell())
            throw new NopException(ERR_TABLE_MERGE_CELL_EMPTY_OR_PROXY_CELL).param(ARG_CELL, cell)
                    .param(ARG_ROW_INDEX, rowIndex).param(ARG_COL_INDEX, colIndex);

        this.invalidateColCount();

        int curMergeDown = cell.getMergeDown();
        int curMergeAcross = cell.getMergeAcross();

        int minDown = Math.min(curMergeDown, mergeDown);
        int minAcross = Math.min(curMergeAcross, mergeAcross);

        // 原先的合并范围较大，需要缩减
        if (curMergeAcross > mergeAcross || curMergeDown > mergeDown) {
            for (int i = minDown; i <= curMergeDown; i++) {
                for (int j = minAcross; j <= curMergeAcross; j++) {
                    if (i == 0 && j == 0)
                        continue;
                    if (i > mergeDown || j > mergeAcross)
                        makeRow(rowIndex + i).internalSetCell(colIndex + j, null);
                }
            }
        }

        cell.setMergeAcross(mergeAcross);
        cell.setMergeDown(mergeDown);

        if (curMergeAcross < mergeAcross || curMergeDown < mergeDown) {
            for (int i = minDown; i <= mergeDown; i++) {
                for (int j = minAcross; j <= mergeAcross; j++) {
                    // 后续判断自动跳过此情况
                    // if (i == 0 && j == 0)
                    // continue;
                    if (i <= curMergeDown && j <= curMergeAcross)
                        continue;

                    IRow row = makeRow(rowIndex + i);
                    ICell proxy = newProxyCell(cell, i, j);
                    internalSetCell(row, rowIndex + i, colIndex + j, proxy);
                }
            }
        }
    }

    @Override
    public void addToRow(int rowIndex, ICell cell) {
        checkAllowChange();
        if (cell == null || cell.isProxyCell()) {
            throw new IllegalArgumentException("only RealCell is allowed");
        }

        invalidateColCount();

        IRow row = makeRow(rowIndex);
        int i = 0;
        int cols = row.getColCount();
        for (; i < cols; i++) {
            ICell c = row.getCell(i);
            if (c == null) {
                break;
            }
        }

        if (!_isSpaceAvailable(row, rowIndex, i, cell.getMergeDown(), cell.getMergeAcross()))
            throw new NopException(ERR_TABLE_NO_ENOUGH_FREE_SPACE).param(ARG_ROW_INDEX, rowIndex)
                    .param(ARG_COL_INDEX, i).param(ARG_MERGE_DOWN, cell.getMergeDown())
                    .param(ARG_MERGE_ACROSS, cell.getMergeAcross());
        doSetCell(row, rowIndex, i, cell);
    }

    @Override
    public boolean isSpaceAvailable(int rowIndex, int colIndex, int mergeDown, int mergeAcross) {
        IRow row = getRow(rowIndex);
        if (row == null)
            return true;
        return _isSpaceAvailable(row, rowIndex, colIndex, mergeDown, mergeAcross);
    }

    boolean _isSpaceAvailable(IRow row, int rowIndex, int colIndex, int mergeDown, int mergeAcross) {
        if (row.getCell(colIndex) != null)
            return false;

        if (mergeAcross > 0) {
            for (int i = 1; i <= mergeAcross; i++) {
                if (row.getCell(colIndex + i) != null)
                    return false;
            }
        }
        if (mergeDown > 0) {
            for (int i = 1; i <= mergeDown; i++) {
                IRow nextRow = getRow(rowIndex + 1 + i);
                if (nextRow == null)
                    break;
                for (int j = 0; j <= mergeAcross; j++) {
                    if (nextRow.getCell(colIndex + j) != null)
                        return false;
                }
            }
        }
        return true;
    }

    @Override
    public void rbind(ITable<T> table) {
        checkAllowChange();
        int colCount = this.getColCount();
        int rowCount = getRowCount();
        int newColCount = table.getColCount();
        int newRowCount = table.getRowCount();
        for (int i = 0, n = newRowCount; i < n; i++) {
            IRow row = this.makeRow(rowCount + i);
            IRow brow = table.getRow(i);
            for (int j = 0, m = brow.getColCount(); j < m; j++) {
                ICell cell = brow.getCell(j);
                if (cell != null && !cell.isProxyCell()) {
                    doSetCell(row, rowCount + i, j, cell.cloneInstance());
                }
            }
        }
        this.colCount = Math.max(colCount, newColCount);
    }

    @Override
    public void cbind(ITable<T> table) {
        checkAllowChange();
        int colCount = this.getColCount();
        int rowCount = table.getRowCount();
        for (int i = 0, n = rowCount; i < n; i++) {
            IRow row = this.makeRow(i);
            IRow brow = table.getRow(i);
            for (int j = 0, m = brow.getColCount(); j < m; j++) {
                ICell cell = brow.getCell(j);
                if (cell != null && !cell.isProxyCell()) {
                    doSetCell(row, i, colCount + j, cell.cloneInstance());
                }
            }
        }
        this.colCount = colCount + table.getColCount();
    }

    @Override
    public void trimTable(int maxRowCount, int maxColCount) {
        checkAllowChange();
        if (colCount >= 0) {
            if (getColCount() <= maxColCount && getRowCount() <= maxRowCount)
                return;
        }

        List<T> rows = getRows();
        if (maxRowCount <= 0) {
            this.invalidateColCount();
            rows.clear();
            return;
        }

        if (maxColCount <= 0) {
            this.invalidateColCount();
            rows.clear();
            return;
        }

        if (rows.size() > maxRowCount) {
            removeRows(maxRowCount, rows.size() - maxRowCount);
        }
        if (getColCount() > maxColCount) {
            removeCols(maxColCount, getColCount() - maxColCount);
        }
    }

    @Override
    public void trimBlankCols() {
        int rowCount = getRowCount();
        int colCount = getColCount();

        for (; colCount > 0; colCount--) {
            if (!isAllColumnCellSatisfy(colCount - 1, cell -> cell == null || cell.isBlankCell()))
                break;
        }
        trimTable(rowCount, colCount);
    }

    @Override
    public void trimBlankRows() {
        checkAllowChange();
        int rowCount = getRowCount();
        // int colCount = getColCount();

        List<T> rows = getRows();
        for (int rowIndex = rowCount - 1; rowIndex >= 0; rowIndex--) {
            if (!isAllRowCellSatisfy(rowIndex, cell -> cell == null || cell.isBlankCell()))
                break;
            rows.remove(rowIndex);
        }
    }

    public void dump(String title) {
        LOG.info("{}:\n{}", title, toDebugString());
    }

    public void dump() {
        dump("table.dump");
    }

    public String toHtmlString() {
        return HtmlTableOutput.toHtml(this);
    }

    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        int rowCount = this.getRowCount();
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            IRow row = getRow(rowIndex);

            sb.append(StringHelper.leftPad(String.valueOf(rowIndex + 1), 2, ' '));
            sb.append(':');

            for (int colIndex = 0, colCount = row.getColCount(); colIndex < colCount; colIndex++) {
                ICell cell = row.getCell(colIndex);
                if (colIndex != 0)
                    sb.append('|');

                int pos = sb.length();
                if (cell == null) {
                    sb.append("   null");
                } else if (cell.isProxyCell()) {
                    sb.append("   {+").append(cell.getRowOffset()).append(",+").append(cell.getColOffset()).append('}');
                } else {
                    sb.append('[').append(colIndex).append("]");
                    if (cell.getMergeDown() > 0 || cell.getMergeAcross() > 0) {
                        sb.append("{").append(cell.getRowSpan()).append(",").append(cell.getColSpan()).append("}");
                    }
                    String text = StringHelper.limitLen(normalizeText(cell.getText()), 10);
                    sb.append(text);
                }
                int pos2 = sb.length();
                // 增加padding
                for (; pos2 < pos + 15; pos2++) {
                    sb.append(' ');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    String normalizeText(String text) {
        return StringHelper.replaceChars(text, "\t\r\n", "   ");
    }

    protected abstract T newRow();

    public void addRow(AbstractRow row) {
        checkAllowChange();
        row.setTable(this);
        ((List) getRows()).add(row);
    }

    @Override
    protected void outputJson(IJsonHandler handler) {
        if (id != null)
            handler.put("id", id);
        if (styleId != null)
            handler.put("styleId", styleId);
//
//        List<? extends IColumnConfig> cols = getCols();
//        if (cols != null && cols.size() > 0) {
//            handler.put("cols", cols);
//        }
//        handler.put("rows", getRows());
        super.outputJson(handler);
    }

    public void normalizeMergeRanges() {
        int rowCount = getRowCount();
        int colCount = getColCount();

        for (int i = 0; i < rowCount; i++) {
            IRow row = getRow(i);
            for (int j = 0; j < colCount; j++) {
                ICell cell = row.getCell(j);
                if (cell != null && !cell.isProxyCell()) {
                    if (cell.getMergeAcross() > 0 || cell.getMergeDown() > 0) {
                        this.setCell(i, j, cell);
                    }
                }
            }
        }
    }
}