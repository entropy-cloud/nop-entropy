/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.util.ProcessResult;
import io.nop.core.model.table.impl.TableImpls;
import io.nop.core.reflect.hook.IExtensibleObject;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface IRowView extends IExtensibleObject {
    String getId();

    String getStyleId();

    Double getHeight();

    boolean isHidden();

    ITableView getTable();

    default int getRowIndex() {
        ITableView table = getTable();
        return table == null ? -1 : table.getRows().indexOf(this);
    }

    /**
     * 整行所占据的列数
     *
     * @return
     */
    int getColCount();

    /**
     * 是否没有任何单元格。对应于getCells().isEmpty()
     */
    default boolean isEmpty() {
        return getCells().isEmpty();
    }

    /**
     * 返回指定位置处的单元格，可能是null或者ProxyCell。
     *
     * @param colIndex 从0开始的单元格index
     */
    ICellView getCell(int colIndex);

    default ICellView getRealCell(int colIndex) {
        ICellView cell = getCell(colIndex);
        if (cell == null || cell.isProxyCell())
            return null;
        return cell;
    }

    /**
     * 返回第n个单元格，每个合并单元格只计数一次。
     *
     * @param index 第几个合并单元格，从0开始
     * @return 未找到时返回null
     */
    default ICellView getMergedCell(int index) {
        List<? extends ICellView> cells = getCells();
        int idx = 0;
        for (int i = 0, n = cells.size(); i < n; i++) {
            ICellView icell = cells.get(i);
            if (icell == null) {
                if (idx == index)
                    return icell;
                idx++;
                continue;
            }

            if (idx == index)
                return icell;
            idx++;
            i += icell.getMergeAcross();
        }
        return null;
    }

    default Object getCellValue(int colIndex) {
        ICellView cell = getCell(colIndex);
        return cell == null ? null : cell.getValue();
    }

    default String getCellText(int colIndex) {
        ICellView cell = getCell(colIndex);
        return cell == null ? null : cell.getText();
    }

    default Iterator<? extends ICellView> iterator() {
        return getCells().iterator();
    }

    default ICellView getFirstRealCell() {
        Iterator<? extends ICellView> it = iterator();
        while (it.hasNext()) {
            ICellView cell = it.next();
            if (cell != null && !cell.isProxyCell())
                return cell;
        }
        return null;
    }

    default ICellView getLastRealCell() {
        List<? extends ICellView> cells = getCells();
        for (int i = cells.size() - 1; i >= 0; i--) {
            ICellView cell = cells.get(i);
            if (cell != null && !cell.isProxyCell())
                return cell;
        }
        return null;
    }

    default <E extends ICellView> ProcessResult forEachCell(int rowIndex, ICellProcessor<E> processor) {
        Iterator<? extends ICellView> it = this.iterator();
        int colIndex = 0;
        while (it.hasNext()) {
            if (processor.process((E) it.next(), rowIndex, colIndex++) == ProcessResult.STOP)
                return ProcessResult.STOP;
        }
        return ProcessResult.CONTINUE;
    }

    default <E extends ICellView> ProcessResult forEachRealCell(int rowIndex, ICellProcessor<E> processor) {
        Iterator<? extends ICellView> it = this.iterator();
        int colIndex = 0;
        while (it.hasNext()) {
            E cell = (E) it.next();
            if (cell == null || cell.isProxyCell()) {
                colIndex++;
                continue;
            }

            if (processor.process(cell, rowIndex, colIndex++) == ProcessResult.STOP)
                return ProcessResult.STOP;
        }
        return ProcessResult.CONTINUE;
    }

    /**
     * 这里返回的cells集合的下标就是colIndex, 当行中包含合并单元格时，合并单元格对应的colIndex位置就会放置ProxyCell。
     */
    @Nonnull
    List<? extends ICellView> getCells();

    @JsonIgnore
    default List<? extends ICellView> getRealCells() {
        List<ICellView> cells = new ArrayList<>();
        getCells().forEach(cell -> {
            if (cell != null && !cell.isProxyCell())
                cells.add(cell);
        });
        return cells;
    }

    default List<? extends ICellView> findCells(Predicate<? super ICellView> predicate) {
        return getCells().stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * 返回本行中所有非ProxyCell的单元格。
     *
     * @return 返回的集合中可能包含null，但是不包含ProxyCell
     */
    default List<? extends ICellView> getNonProxyCells() {
        List<ICellView> ret = new ArrayList<>(getColCount());
        for (ICellView cell : ret) {
            if (cell == null) {
                ret.add(cell);
                continue;
            }
            if (cell.isProxyCell())
                continue;
            ret.add(cell);
        }
        return ret;
    }

    default <T> List<T> getCellValues(Function<ICellView, T> fn) {
        return TableImpls.getRowCellValues(this, fn);
    }

    default List<Object> getCellValues() {
        return getCellValues((cell) -> cell == null ? null : cell.getValue());
    }

    /**
     * 如果本行的每一个单元格都是空白单元格，则本行为空行
     */
    default boolean isBlankRow() {
        for (ICellView icell : getCells()) {
            if (icell == null)
                continue;
            if (!icell.isBlankCell())
                return false;
        }
        return true;
    }
}