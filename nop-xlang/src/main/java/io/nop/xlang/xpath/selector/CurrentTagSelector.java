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

public class CurrentTagSelector<E> implements IXPathElementSelector<E> {
    private final String tagName;

    public CurrentTagSelector(String tagName) {
        this.tagName = tagName;
    }

    public String toString() {
        return tagName;
    }

    @Override
    public SelectResult select(E source, IXPathContext<E> context, ISelectionCollector<E> collector) {
        E root = context.root();
        if (tagName.equals("*") || tagName.equals(context.adapter().tagName(root)))
            return collector.collect(source);

        return SelectResult.NOT_FOUND;
    }
}