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
import io.nop.commons.util.CollectionHelper;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathElementSelector;

public class ChildIndexSelector<E> implements IXPathElementSelector<E> {
    private static final long serialVersionUID = -7833730526121648081L;
    private final int index;

    public ChildIndexSelector(int index) {
        this.index = index;
    }

    public static <E> IXPathElementSelector<E> of(int index) {
        return new ChildIndexSelector<>(index);
    }

    public String toString() {
        return "[" + index + ']';
    }

    @Override
    public SelectResult select(E source, IXPathContext<E> context, ISelectionCollector<E> collector) {
        Iterable<E> children = context.adapter().getChildren(source);
        E result = CollectionHelper.getByIndex(children, index);
        if (result == null)
            return SelectResult.NOT_FOUND;
        return collector.collect(result);
    }
}
