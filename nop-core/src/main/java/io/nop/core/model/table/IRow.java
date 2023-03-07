/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table;

import io.nop.api.core.util.IFreezable;

import java.util.Collection;
import java.util.List;

public interface IRow extends IRowView, IFreezable {
    ICell getCell(int colIndex);

    ITable<? extends IRow> getTable();

    void setTable(ITable<? extends IRow> table);

    default ICell getRealCell(int colIndex) {
        ICell cell = getCell(colIndex);
        if (cell == null || cell.isProxyCell())
            return null;
        return cell;
    }

    ICell makeCell(int colIndex);

    List<? extends ICell> getCells();

    /**
     * 内部函数，不应该由应用代码调用。它没有处理合并单元格情况
     */
    void internalSetCell(int colIndex, ICell cell);

    /**
     * 直接删除指定位置处的单元格。没有处理合并单元格情况
     */
    void internalRemoveCell(int colIndex);

    /**
     * 插入一列，将后续列向后移动。
     */
    void internalInsertCell(int colIndex, ICell cell);

    void internalAddCell(ICell cell);

    void internalAddCells(Collection<? extends ICell> cells);
}