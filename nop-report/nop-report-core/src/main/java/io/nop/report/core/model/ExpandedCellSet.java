/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.model;

import io.nop.commons.lang.IValueWrapper;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ExpandedCellSet implements Iterable<Object>, IValueWrapper {
    private final List<ExpandedCell> cells;

    public ExpandedCellSet(List<ExpandedCell> cells) {
        this.cells = cells;
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

    public ExpandedCell getCell() {
        if (cells.isEmpty())
            return null;
        return cells.get(0);
    }

    @Override
    public Object getValue() {
        if (cells.isEmpty())
            return null;
        ExpandedCell cell = cells.get(0);
        return cell.getValue();
    }

    @Override
    public Iterator<Object> iterator() {
        return cells.stream().map(ExpandedCell::getValue).iterator();
    }

    public ExpandedCellSet filter(Predicate<ExpandedCell> filter) {
        List<ExpandedCell> list = cells.stream().filter(filter).collect(Collectors.toList());
        return new ExpandedCellSet(list);
    }

    public List<Object> map(Function<ExpandedCell, Object> fn) {
        return cells.stream().map(fn).collect(Collectors.toList());
    }
}