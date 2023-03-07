/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpath.selector;

import io.nop.commons.functional.select.IMatchEvaluator;
import io.nop.commons.functional.select.ISelectionCollector;
import io.nop.commons.functional.select.SelectResult;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathElementSelector;
import io.nop.xlang.xpath.IXPathEvaluator;

public class CurrentEvaluatorSelector<E> implements IXPathElementSelector<E>, IXPathEvaluator<E> {
    private static final long serialVersionUID = 466907365371998598L;
    private final String tagName;
    private final IMatchEvaluator<E, IXPathContext<E>> evaluator;

    public CurrentEvaluatorSelector(String tagName, IMatchEvaluator<E, IXPathContext<E>> evaluator) {
        this.tagName = tagName;
        this.evaluator = evaluator;
    }

    public String toString() {
        return tagName + "[" + evaluator + "]";
    }

    @Override
    public boolean matches(E source, IXPathContext<E> context) {
        String childName = context.adapter().tagName(source);
        if (tagName.equals("*") || childName.equals(tagName)) {
            if (evaluator.matches(source, context)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public SelectResult select(E source, IXPathContext<E> context, ISelectionCollector<E> collector) {
        if (matches(source, context)) {
            return collector.collect(source);
        }
        return SelectResult.NOT_FOUND;
    }
}