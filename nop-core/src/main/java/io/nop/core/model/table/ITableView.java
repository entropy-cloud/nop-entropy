/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table;

import io.nop.commons.util.CollectionHelper;
import io.nop.core.model.table.impl.SubTableView;
import io.nop.core.model.table.impl.TableImpls;
import io.nop.core.reflect.hook.IExtensibleObject;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public interface ITableView extends IExtensibleObject {
    int getRowCount();

    int getColCount();

    default boolean isSingleCell() {
        return getRowCount() == 1 && getColCount() == 1;
    }

    /**
     * 表头所占据的行数
     */
    default int getHeaderCount() {
        return 0;
    }

    /**
     * 表旁所占据的行数
     */
    default int getSideCount() {
        return 0;
    }

    /**
     * 表尾所占据的行数
     */
    default int getFooterCount() {
        return 0;
    }

    String getId();

    String getStyleId();

    default CellRange getCellRange() {
        return new CellRange(0, 0, Math.max(0, getRowCount() - 1), Math.max(0, getColCount() - 1));
    }

    List<? extends IColumnConfig> getCols();

    default IColumnConfig getCol(int colIndex) {
        return CollectionHelper.get(getCols(), colIndex);
    }

    default Double getColWidth(int colIndex) {
        IColumnConfig colConfig = getCol(colIndex);
        return colConfig == null ? null : colConfig.getWidth();
    }

    List<? extends IRowView> getRows();

    /**
     * 根据行下标返回行，如果对应的行不存在则返回null
     *
     * @param rowIndex 从0开始
     */
    IRowView getRow(int rowIndex);

    /**
     * 根据行坐标和列坐标取到单元格。如果该位置被合并单元格占用，则返回ProxyCell
     *
     * @param rowIndex 行坐标
     * @param colIndex 列坐标
     * @return 有可能返回null, 对应空单元格
     */
    default ICellView getCell(int rowIndex, int colIndex) {
        IRowView row = getRow(rowIndex);
        return row == null ? null : row.getCell(colIndex);
    }

    default Object getCellValue(int rowIndex, int colIndex) {
        ICellView cell = getCell(rowIndex, colIndex);
        return cell == null ? null : cell.getValue();
    }

    default String getCellText(int rowIndex, int colIndex) {
        ICellView cell = getCell(rowIndex, colIndex);
        return cell == null ? null : cell.getText();
    }

    default boolean isBlankRow(int rowIndex) {
        IRowView row = getRow(rowIndex);
        return row == null || row.isBlankRow();
    }

    default ICellView getFirstRealCellInCol(int colIndex) {
        for (IRowView row : this.getRows()) {
            if (row == null)
                continue;
            ICellView cell = row.getCell(colIndex);
            if (cell != null && !cell.isProxyCell())
                return cell;
        }
        return null;
    }

    default <E extends ICellView> void forEachRealCell(ICellProcessor<E> processor) {
        for (int i = 0, n = getRowCount(); i < n; i++) {
            IRowView row = getRow(i);
            if (row == null)
                continue;
            row.forEachRealCell(i, processor);
        }
    }

    default <E extends ICellView> void forEachCell(ICellProcessor<E> processor) {
        for (int i = 0, n = getRowCount(); i < n; i++) {
            IRowView row = getRow(i);
            if (row == null)
                continue;
            row.forEachCell(i, processor);
        }
    }

    /**
     * 将表格中的一个区域包装为ITable形式
     *
     * @param range 表格区间对象
     */
    default ITableView getSubTable(CellRange range) {
        if (getRowCount() <= 0 || getColCount() <= 0)
            return null;

        CellRange tableRange = new CellRange(0, 0, getRowCount() - 1, getColCount() - 1);
        CellRange intersect = tableRange.intersect(range);
        if (intersect == null)
            return null;
        return new SubTableView(this, intersect);
    }

    default ITableView getSubTable(int firstRowIndex, int firstColIndex, int lastRowIndex, int lastColIndex) {
        return getSubTable(new CellRange(firstRowIndex, firstColIndex, lastRowIndex, lastColIndex));
    }

    default boolean isAllColumnCellSatisfy(int colIndex, Predicate<ICellView> predicate) {
        return TableImpls.isAllColumnCellSatisfy(this, colIndex, predicate);
    }

    default boolean isSomeColumnCellSatisfy(int colIndex, Predicate<ICellView> predicate) {
        return TableImpls.isSomeColumnCellSatisfy(this, colIndex, predicate);
    }

    default boolean isAllRowCellSatisfy(int rowIndex, Predicate<ICellView> predicate) {
        return TableImpls.isAllRowCellSatisfy(this, rowIndex, predicate);
    }

    default boolean isSomeRowCellSatisfy(int rowIndex, Predicate<ICellView> predicate) {
        return TableImpls.isSomeRowCellSatisfy(this, rowIndex, predicate);
    }

    default <T> List<List<T>> getMatrixValues(@Nonnull Function<ICellView, T> fn) {
        return TableImpls.getMatrixValues(this, fn);
    }

    default List<List<Object>> getMatrixValues() {
        return getMatrixValues((cell) -> cell == null ? null : cell.getValue());
    }

    /**
     * 根据headers中指定的列，返回单元格的值
     *
     * @param headers 对应返回的Map的key
     */
    default <T> List<Map<String, T>> getRowValues(List<String> headers, Function<ICellView, T> fn) {
        return TableImpls.getRowValues(this, headers, fn);
    }

    default List<Map<String, Object>> getRowValues(List<String> headers) {
        return getRowValues(headers, (cell) -> cell == null ? null : cell.getValue());
    }
}