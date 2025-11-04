/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.table.impl;

import io.nop.api.core.util.ProcessResult;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.IRow;
import io.nop.core.model.table.IRowView;
import io.nop.core.model.table.ITable;
import io.nop.core.model.table.ITableView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class TableImpls {
    public static boolean isAllColumnCellSatisfy(ITableView table, int colIndex, Predicate<ICellView> predicate) {
        for (int i = 0, n = table.getRowCount(); i < n; i++) {
            ICellView cell = table.getCell(i, colIndex);
            if (!predicate.test(cell))
                return false;
        }
        return true;
    }

    public static boolean isSomeColumnCellSatisfy(ITableView table, int colIndex, Predicate<ICellView> predicate) {
        for (int i = 0, n = table.getRowCount(); i < n; i++) {
            ICellView cell = table.getCell(i, colIndex);
            if (predicate.test(cell))
                return true;
        }
        return false;
    }

    public static boolean isAllRowCellSatisfy(ITableView table, int rowIndex, Predicate<ICellView> predicate) {
        IRowView row = table.getRow(rowIndex);
        if (row == null)
            return false;
        return row.forEachCell(rowIndex, (cell, i, j) -> {
            if (predicate.test(cell))
                return ProcessResult.CONTINUE;
            return ProcessResult.STOP;
        }) == ProcessResult.CONTINUE;
    }

    public static boolean isSomeRowCellSatisfy(ITableView table, int rowIndex, Predicate<ICellView> predicate) {
        IRowView row = table.getRow(rowIndex);
        if (row == null)
            return false;
        return row.forEachCell(rowIndex, (cell, i, j) -> {
            if (predicate.test(cell))
                return ProcessResult.STOP;
            return ProcessResult.CONTINUE;
        }) == ProcessResult.STOP;
    }

    public static <T> List<List<T>> getMatrixValues(ITableView table, Function<ICellView, T> fn) {
        int nRow = table.getRowCount();
        int nCol = table.getColCount();

        List<List<T>> ret = new ArrayList<>(nRow);

        for (IRowView row : table.getRows()) {
            List<T> values = new ArrayList<>(nCol);

            if (row == null) {
                for (int j = 0; j < nCol; j++) {
                    values.add(fn.apply(null));
                }
            } else {
                for (int j = 0; j < nCol; j++) {
                    ICellView cell = row.getCell(j);
                    T value = fn.apply(cell);
                    values.add(value);
                }
            }
            ret.add(values);
        }
        return ret;
    }

    public static <T> List<Map<String, T>> getRowValues(ITableView table, List<String> headers,
                                                        Function<ICellView, T> fn) {
        int nRow = table.getRowCount();
        List<Map<String, T>> ret = new ArrayList<>(nRow);
        int rowIndex = 0;
        for (IRowView row : table.getRows()) {
            Map<String, T> map = new HashMap<>(headers.size());
            if (row == null) {
                for (int i = 0, n = headers.size(); i < n; i++) {
                    String header = headers.get(i);
                    if (header == null) {
                        continue;
                    }
                    map.put(header, fn.apply(null));
                }
            } else {
                row.forEachCell(rowIndex, (cell, i, j) -> {
                    String header = headers.get(j);
                    if (header == null) {
                        return ProcessResult.CONTINUE;
                    }
                    map.put(header, fn.apply(cell));
                    return ProcessResult.CONTINUE;
                });
            }
            rowIndex++;
            ret.add(map);
        }
        return ret;
    }

    public static <T> List<T> getRowCellValues(IRowView row, Function<ICellView, T> fn) {
        int nCol = row.getColCount();
        List<T> ret = new ArrayList<>(nCol);

        row.forEachCell(0, (cell, i, j) -> {
            ret.add(fn.apply(cell));
            return ProcessResult.CONTINUE;
        });

        return ret;
    }

    public static <T extends IRow> void setCells(ITable<T> table, int rowIndex, int colIndex, ITableView view,
                                                 Function<ICellView, ICell> transformer) {
        for (int i = 0, n = view.getRowCount(); i < n; i++) {
            IRowView row = view.getRow(i);
            for (int j = 0, m = row.getColCount(); j < m; j++) {
                ICellView icell = row.getCell(j);
                if (icell == null) {
                    table.setCell(rowIndex + i, colIndex + j, null);
                    continue;
                }
                if (icell.isProxyCell())
                    continue;

                ICellView cellView = icell.getRealCell();
                ICell cell = transformer == null ? (ICell) cellView.cloneInstance() : transformer.apply(cellView);
                table.setCell(rowIndex + i, colIndex + j, cell);
            }
        }
    }
}