/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table;

import io.nop.core.model.table.impl.ProxyCell;
import io.nop.core.model.table.impl.TableImpls;

import java.util.function.Function;

public interface ITable<T extends IRow> extends ITableView {
    void setHeaderCount(int headerCount);

    void setSideCount(int sideCount);

    void setFooterCount(int footerCount);

    /**
     * 根据行下标返回行，如果对应的行不存在则返回null
     *
     * @param rowIndex 从0开始
     */
    T getRow(int rowIndex);

    /**
     * 根据行下标返回行，如果对应的行不存在则创建行。例如当前只有行0, 调用makeRow(2), 则会自动创建行1,行2,最后返回行2。
     * 考虑到插入合并单元格有可能隐蔽的创建行，因此ITable没有提供addRow方法，而是通过makeRow来自动发现已经被隐蔽创建的行。
     *
     * @param rowIndex 从0开始。
     */
    T makeRow(int rowIndex);

    default ICell newProxyCell(ICell cell, int rowOffset, int colOffset) {
        return new ProxyCell(cell, rowOffset, colOffset);
    }

    /**
     * 在rowIndex位置插入一个新行。如果在合并单元格中间插入，则会自动延展合并单元格。 另外可以通过extendCols来特殊指定前方行中的哪些列也要被自动延展。
     *
     * @param rowIndex   行下标，从0开始
     * @param count      插入多少行
     * @param extendCols 指定rowIndex-1行中哪些列需要被自动扩展。允许为null
     */
    void insertRows(int rowIndex, int count, int[] extendCols);

    void insertRow(int rowIndex);

    /**
     * 在colIndex位置插入一个新列。如果在合并单元格中间插入，则会自动延展合并单元格
     *
     * @param colIndex 列下标，从0开始
     */
    void insertCols(int colIndex, int count, int[] extendRows);

    void insertCol(int colIndex);

    /**
     * 根据行下标删除行。删除行时会自动调整合并单元格的rowSpan和rowOffset。
     *
     * @param rowIndex 从0开始
     */
    void removeRows(int rowIndex, int count);

    void removeRow(int rowIndex);

    /**
     * 根据列下标来删除列。删除列时会自动调整合并单元格的colSpan和colOffset。
     *
     * @param colIndex 删除列的位置，从0开始
     */
    void removeCols(int colIndex, int count);

    void removeCol(int colIndex);

    /**
     * 根据行坐标和列坐标取到单元格。如果该位置被合并单元格占用，则返回ProxyCell
     *
     * @param rowIndex 行坐标
     * @param colIndex 列坐标
     * @return 有可能返回null, 对应空单元格
     */
    ICell getCell(int rowIndex, int colIndex);

    /**
     * 在指定位置处设置单元格，如果是合并单元格，则会自动创建ProxyCell。如果指定位置处单元格已存在，则会自动清除此前的单元格。
     */
    <V> void setCell(int rowIndex, int colIndex, ICell cell);

    /**
     * 设置位置在(rowIndex,colIndex)处的单元格的mergeDown和mergeAcross为指定值
     */
    void mergeCell(int rowIndex, int colIndex, int mergeDown, int mergeAcross);

    ICell makeCell(int rowIndex, int colIndex);

    default void mergeCell(CellRange range) {
        mergeCell(range.getFirstRowIndex(), range.getFirstColIndex(), range.getRowCount() - 1, range.getColCount() - 1);
    }

    /**
     * 检查(rowIndex,colIndex)位置开始具有足够的未占用的节点空间
     *
     * @param rowIndex    起始行下标，从0开始
     * @param colIndex    起始列下标，从0开始
     * @param mergeDown   向下延展行数，不包含起始行
     * @param mergeAcross 向右延展列数，不包含起始列
     */
    boolean isSpaceAvailable(int rowIndex, int colIndex, int mergeDown, int mergeAcross);

    /**
     * 在指定行追加单元格，此函数会自动跳过上方合并单元格占据的空间，寻找到第一个空位置。如果第一个空位置不够大，则抛出异常。
     */
    <V> void addToRow(int rowIndex, ICell cell);

    /**
     * 将table对应的行追加到当前表中
     */
    void rbind(ITable<T> table);

    /**
     * 将table对应的所有数据按照列拼接到当前表中
     */
    void cbind(ITable<T> table);

    default void setCells(int rowIndex, int colIndex, ITableView table, Function<ICellView, ICell> transformer) {
        TableImpls.setCells(this, rowIndex, colIndex, table, transformer);
    }

    default void setCells(int rowIndex, int colIndex, ITableView table) {
        setCells(rowIndex, colIndex, table, null);
    }

    /**
     * 将超出的行和列全部删除
     *
     * @param maxRowCount 最多保留多少行
     * @param maxColCount 最多保留多少列
     */
    void trimTable(int maxRowCount, int maxColCount);

    /**
     * 删除表格下方的空白行，以及表格右方的所有空白列
     */
    default void trimBlankRowsAndCols() {
        trimBlankRows();
        trimBlankCols();
    }

    /**
     * 删除表格下方的所有空白行
     */
    void trimBlankCols();

    /**
     * 删除表格下方的所有空白行
     */
    void trimBlankRows();
}