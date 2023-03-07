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

public class CurrentNodeSelector<E> implements IXPathElementSelector<E> {
    public static final CurrentNodeSelector INSTANCE = new CurrentNodeSelector();
    private static final long serialVersionUID = 3005707924129688349L;

    public String toString() {
        return ".";
    }

    private Object readResolve() {
        return INSTANCE;
    }

    @Override
    public SelectResult select(E source, IXPathContext<E> context, ISelectionCollector<E> collector) {
        if (source == null)
            return SelectResult.NOT_FOUND;
        return collector.collect(source);
    }
}