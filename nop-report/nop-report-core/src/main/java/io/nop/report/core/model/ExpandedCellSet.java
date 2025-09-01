/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.lang.IValueWrapper;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.utils.Underscore;
import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.CellRangeMerger;
import io.nop.report.core.engine.IXptRuntime;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.nop.report.core.XptErrors.ARG_EXPR;
import static io.nop.report.core.XptErrors.ARG_SIZE;
import static io.nop.report.core.XptErrors.ERR_XPT_CELL_EXPR_RESULT_NOT_ONE_CELL;

public class ExpandedCellSet implements Iterable<Object>, IValueWrapper, ISourceLocationGetter {
    private final SourceLocation loc;
    private final String expr;
    private final List<ExpandedCell> cells;

    public ExpandedCellSet(SourceLocation loc, String expr, List<ExpandedCell> cells) {
        this.loc = loc;
        this.expr = expr;
        this.cells = cells == null ? Collections.emptyList() : cells;
    }

    public String getCellName() {
        if (cells.isEmpty())
            return null;
        ExpandedCell cell = cells.get(0);
        return cell.getName();
    }

    @Override
    public SourceLocation getLocation() {
        return loc;
    }

    public String toString() {
        return expr == null ? "ExpandedCellSet" : expr;
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    public int size() {
        return cells.size();
    }

    public List<ExpandedCell> getCells() {
        return cells;
    }

    public List<CellRange> getCellRanges() {
        CellRangeMerger merger = new CellRangeMerger();
        for (ExpandedCell cell : cells) {
            merger.addCell(cell.getRowIndex(), cell.getColIndex());
        }
        return merger.getMergedRanges();
    }

    public int getRowCount() {
        ExpandedCell first = getFirstCell();
        if (first == null)
            return 0;
        ExpandedCell last = getLastCell();
        return last.getRowIndex() - first.getRowIndex() + 1;
    }

    public int getColCount() {
        ExpandedCell first = getFirstCell();
        if (first == null)
            return 0;
        ExpandedCell last = getLastCell();
        return last.getColIndex() - first.getColIndex() + 1;
    }

    public Object getFirstValue() {
        ExpandedCell cell = getFirstCell();
        return cell == null ? null : cell.getValue();
    }

    public Object getLastValue() {
        ExpandedCell cell = getLastCell();
        return cell == null ? null : cell.getValue();
    }

    public Object sum() {
        return Underscore.sum(cells, ExpandedCell::getValue);
    }

    public ExpandedCell getFirstCell() {
        if (cells.isEmpty())
            return null;
        return cells.get(0);
    }

    public ExpandedCell getLastCell() {
        if (cells.isEmpty())
            return null;
        return cells.get(cells.size() - 1);
    }

    public ExpandedCell get(int index) {
        if (index < 0) {
            index = cells.size() + index;
        }

        if (index < 0 || index >= cells.size())
            return null;
        return cells.get(index);
    }

    public Object value(int index) {
        ExpandedCell cell = get(index);
        return cell == null ? null : cell.getValue();
    }

//    @EvalMethod
//    public ExpandedCell rowRelative(IEvalScope scope, int index) {
//        IXptRuntime xptRt = (IXptRuntime) scope.getLocalValue(XptConstants.VAR_XPT_RT);
//        ExpandedCell cell = xptRt.getCell();
//        ExpandedRow row = cell.getRow();
//        int idx = CellCoordinateHelper.findRowIndex(cells, row.getRowIndex() + index, row);
//        return idx >= 0 ? cells.get(idx) : null;
//    }
//
//    @EvalMethod
//    public ExpandedCell colRelative(IEvalScope scope, int index) {
//        IXptRuntime xptRt = (IXptRuntime) scope.getLocalValue(XptConstants.VAR_XPT_RT);
//        ExpandedCell cell = xptRt.getCell();
//        ExpandedCol col = cell.getCol();
//        int idx = CellCoordinateHelper.findColIndex(cells, col.getColIndex() + index, col);
//        return idx >= 0 ? cells.get(idx) : null;
//    }

    public ExpandedCell getCell() {
        if (cells.size() != 1)
            throw new NopException(ERR_XPT_CELL_EXPR_RESULT_NOT_ONE_CELL)
                    .loc(loc).param(ARG_EXPR, expr).param(ARG_SIZE, cells.size());

        return cells.get(0);
    }

    public int getExpandIndex() {
        ExpandedCell cell = getCell();
        return cell == null ? -1 : cell.getExpandIndex();
    }

    @Override
    public Object getValue() {
        if (cells.isEmpty())
            return null;
        ExpandedCell cell = getCell();
        return cell.getValue();
    }

    public ExpandedCellSet realCells() {
        return filter(cell -> !cell.isProxyCell());
    }

    @Nonnull
    @Override
    public Iterator<Object> iterator() {
        return cells.stream().map(ExpandedCell::getValue).iterator();
    }

    public ExpandedCellSet filter(Predicate<ExpandedCell> filter) {
        List<ExpandedCell> list = cells.stream().filter(filter).collect(Collectors.toList());
        return new ExpandedCellSet(loc, expr + "{ filter }", list);
    }

    public List<Object> map(Function<ExpandedCell, Object> fn) {
        return cells.stream().map(fn).collect(Collectors.toList());
    }

    public List<Object> flatMap(Function<ExpandedCell, Object> fn) {
        return cells.stream().flatMap(v -> CollectionHelper.toStream(fn.apply(v), true, false))
                .collect(Collectors.toList());
    }

    public ExpandedCellSet rowChildSet(String cellName) {
        List<ExpandedCell> list = cells.stream().flatMap(cell -> {
            List<ExpandedCell> children = cell.getRowDescendants().get(cellName);
            if (children == null)
                return Collections.<ExpandedCell>emptyList().stream();
            return children.stream();
        }).collect(Collectors.toList());
        return new ExpandedCellSet(loc, expr + "[" + cellName + "]", list);
    }

    public ExpandedCellSet colChildSet(String cellName) {
        List<ExpandedCell> list = cells.stream().flatMap(cell -> {
            List<ExpandedCell> children = cell.getColDescendants().get(cellName);
            if (children == null)
                return Collections.<ExpandedCell>emptyList().stream();
            return children.stream();
        }).collect(Collectors.toList());
        return new ExpandedCellSet(loc, expr + "[" + cellName + "]", list);
    }

    public ExpandedCellSet evaluateAll(IXptRuntime xptRt) {
        for (ExpandedCell cell : cells) {
            xptRt.evaluateCell(cell);
        }
        return this;
    }
}