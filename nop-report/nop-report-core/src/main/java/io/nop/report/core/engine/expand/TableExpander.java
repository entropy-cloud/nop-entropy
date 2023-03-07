/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.engine.expand;

import io.nop.excel.model.constants.XptExpandType;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedRow;
import io.nop.report.core.model.ExpandedTable;
import io.nop.report.core.engine.IXptRuntime;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 执行报表展开算法。基本逻辑为从前到后，从上至下逐个执行单元格展开。展开单元格时会先检查它的父格已经被展开。
 * 展开后产生的新的单元格会被放入处理队列并按照顺序被处理
 */
public class TableExpander {
    private final Deque<ExpandedCell> processing = new ArrayDeque<>();

    public TableExpander(ExpandedTable table) {
        for (ExpandedRow row : table.getRows()) {
            row.forEachRealCell(cell -> {
                if (!cell.isExpanded()) {
                    processing.add(cell);
                }
            });
        }
    }

    public void expand(IXptRuntime xptRt) {
        do {
            ExpandedCell cell = processing.poll();
            if (cell == null)
                return;

            if (cell.isRemoved() || cell.isExpanded())
                continue;

            if (cell.getColParent() != null && !cell.getColParent().isExpanded()) {
                processing.push(cell);
                processing.push(cell.getColParent());
                continue;
            }

            if (cell.getRowParent() != null && !cell.getRowParent().isExpanded()) {
                processing.push(cell);
                processing.push(cell.getRowParent());
                continue;
            }

            getCellExpander(cell).expand(cell, processing, xptRt);
        } while (true);
    }

    ICellExpander getCellExpander(ExpandedCell cell) {
        XptExpandType expandType = cell.getExpandType();
        if (expandType == XptExpandType.r)
            return CellRowExpander.INSTANCE;
        return CellColExpander.INSTANCE;
    }
}