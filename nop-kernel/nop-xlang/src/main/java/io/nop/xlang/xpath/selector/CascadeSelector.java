/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpath.selector;

import io.nop.commons.functional.select.ISelectionCollector;
import io.nop.commons.functional.select.SelectResult;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathElementSelector;

public class CascadeSelector<E> implements IXPathElementSelector<E> {
    private static final long serialVersionUID = -6792228719066332763L;
    public static CascadeSelector INSTANCE = new CascadeSelector();

    public String toString() {
        return "//";
    }

    private Object readResolve() {
        return INSTANCE;
    }

    @Override
    public SelectResult select(E source, IXPathContext<E> context, ISelectionCollector<E> collector) {
        SelectResult result = collector.collect(source);
        if (result == SelectResult.FOUND)
            return result;
        if (result == SelectResult.STOP)
            return SelectResult.STOP;

        Iterable<E> children = context.adapter().getChildren(source);
        for (E child : children) {
            SelectResult childResult = select(child, context, collector);
            if (childResult == SelectResult.STOP)
                return SelectResult.STOP;
            if (childResult == SelectResult.FOUND) {
                result = SelectResult.FOUND;
            }
        }

        return result;
    }
}