/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpath.selector;

import io.nop.commons.functional.select.ISelectionCollector;
import io.nop.commons.functional.select.SelectResult;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathElementSelector;
import io.nop.xlang.xpath.IXPathEvaluator;

import java.io.Serializable;

public abstract class AbstractChildSelector<E> implements IXPathElementSelector<E>, IXPathEvaluator<E>, Serializable {
    private static final long serialVersionUID = 5871835027685918912L;

    @Override
    public SelectResult select(E source, IXPathContext<E> context, ISelectionCollector<E> collector) {
        Iterable<E> children = context.adapter().getChildren(source);
        if (children == null)
            return SelectResult.NOT_FOUND;

        SelectResult result = SelectResult.NOT_FOUND;
        for (E child : children) {
            if (child == null)
                continue;

            if (matches(child, context)) {
                SelectResult childResult = collector.collect(child);
                if (isUniqueMatch())
                    return childResult;
                if (childResult == SelectResult.STOP)
                    return SelectResult.STOP;
                if (childResult == SelectResult.FOUND) {
                    result = childResult;
                }
            }
        }
        return result;
    }

    protected boolean isUniqueMatch() {
        return false;
    }
}