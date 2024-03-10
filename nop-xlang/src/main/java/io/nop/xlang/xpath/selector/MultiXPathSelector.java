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
import io.nop.commons.functional.select.collector.SelectAllCollector;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathValueSelector;

import java.util.List;

public class MultiXPathSelector<E, V> implements IXPathValueSelector<E, V> {
    private final List<IXPathValueSelector<E, V>> selectors;

    public MultiXPathSelector(List<IXPathValueSelector<E, V>> selectors) {
        this.selectors = selectors;
    }

    public List<V> selectAll(E source, IXPathContext<E> context) {
        SelectAllCollector<V> collector = new SelectAllCollector<>();
        for (IXPathValueSelector<E, V> selector : selectors) {
            selector.select(source, context, collector);
        }
        return collector.getElements();
    }

    @Override
    public SelectResult select(E source, IXPathContext<E> context, ISelectionCollector<V> collector) {
        for (IXPathValueSelector<E, V> selector : selectors) {
            SelectResult result = selector.select(source, context, collector);
            if (result != SelectResult.NOT_FOUND)
                return result;
        }
        return SelectResult.NOT_FOUND;
    }
}