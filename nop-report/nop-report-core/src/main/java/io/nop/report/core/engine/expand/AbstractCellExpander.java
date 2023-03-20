/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.engine.expand;

import io.nop.commons.util.CollectionHelper;
import io.nop.excel.model.XptCellModel;
import io.nop.excel.model.constants.XptExpandType;
import io.nop.report.core.dataset.DynamicReportDataSet;
import io.nop.report.core.engine.IXptRuntime;
import io.nop.report.core.model.ExpandedCell;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public abstract class AbstractCellExpander implements ICellExpander {

    @Override
    public void expand(ExpandedCell cell, Deque<ExpandedCell> processing, IXptRuntime xptRt) {
        Iterator<?> expandList = runExpandExpr(cell, xptRt);
        if (!expandList.hasNext()) {
            if (cell.getModel() != null && cell.getModel().isKeepExpandEmpty()) {
                clearCell(cell);
            } else {
                removeCell(cell);
            }
        } else {
            Object value = expandList.next();
            cell.setExpandIndex(0);
            cell.setExpandValue(value);

            int expandCount = duplicate(expandList, cell, processing);
            if (expandCount > 1)
                extendCells(cell, expandCount);
        }
    }

    protected Iterator<?> runExpandExpr(ExpandedCell cell, IXptRuntime xptRt) {
        XptCellModel model = cell.getModel();
        xptRt.setCell(cell);

        if (model.getExpandExpr() == null) {
            // 如果没有指定expandExpr，则根据ds.group(field)来获取数据
            String field = model.getField();
            if (field == null)
                return Collections.singletonList(cell.getValue()).iterator();

            String dsName = model.getDs();
            if (dsName == null) {
                return valueToIterator(xptRt.field(field));
            }

            DynamicReportDataSet ds = DynamicReportDataSet.makeDataSet(xptRt.getEvalScope(), dsName);
            return ds.group(xptRt.getEvalScope(), field).iterator();
        }

        Object value = model.getExpandExpr().invoke(xptRt);
        return valueToIterator(value);
    }

    Iterator<?> valueToIterator(Object value) {
        return CollectionHelper.toIterator(value, true);
    }

    protected abstract void removeCell(ExpandedCell cell);

    protected abstract void extendCells(ExpandedCell cell, int expandCount);

    protected int duplicate(Iterator<?> it, ExpandedCell cell,
                            Deque<ExpandedCell> processing) {

        int expandIndex = 0;
        int count = 1;
        while (it.hasNext()) {
            count++;
            expandIndex++;
            Object expandValue = it.next();

            duplicateCell(cell, expandIndex, expandValue, processing);
        }

        return count;
    }

    protected abstract void duplicateCell(ExpandedCell cell, int expandIndex, Object expandValue,
                                          Collection<ExpandedCell> processing);

    protected Map<String, List<ExpandedCell>> getNewListMap(Map<String, List<ExpandedCell>> listMap, Map<ExpandedCell, ExpandedCell> cellMap) {
        Map<String, List<ExpandedCell>> ret = CollectionHelper.newHashMap(listMap.size());
        for (Map.Entry<String, List<ExpandedCell>> entry : listMap.entrySet()) {
            List<ExpandedCell> list = entry.getValue();
            List<ExpandedCell> newList = getNewList(list, cellMap);
            ret.put(entry.getKey(), newList);
        }
        return ret;
    }

    protected List<ExpandedCell> getNewList(List<ExpandedCell> list, Map<ExpandedCell, ExpandedCell> cellMap) {
        List<ExpandedCell> ret = new ArrayList<>(list.size());
        for (ExpandedCell cell : list) {
            ExpandedCell newCell = cellMap.get(cell);
            if (newCell == null)
                newCell = cell;
            ret.add(newCell);
        }
        return ret;
    }

    protected void copyCellValue(ExpandedCell nextCell, ExpandedCell newCell) {
        newCell.setModel(nextCell.getModel());
        newCell.setValue(nextCell.getValue());
        newCell.setMergeAcross(nextCell.getMergeAcross());
        newCell.setMergeDown(nextCell.getMergeDown());
        newCell.setStyleId(nextCell.getStyleId());
    }

    protected void clearCell(ExpandedCell cell) {
        cell.markEvaluated();

        XptExpandType expandType = cell.getExpandType();
        Map<String, List<ExpandedCell>> descendants;
        if (expandType == XptExpandType.c) {
            descendants = cell.getColDescendants();
        } else {
            descendants = cell.getRowDescendants();
        }
        if (descendants != null) {
            for (List<ExpandedCell> list : descendants.values()) {
                for (ExpandedCell child : list) {
                    child.markEvaluated();
                }
            }
        }
    }
}