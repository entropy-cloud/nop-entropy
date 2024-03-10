/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpath.selector;

import io.nop.commons.functional.select.IMatchEvaluator;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathElementSelector;

public class ChildEvaluatorSelector<E> extends AbstractChildSelector<E> {
    private static final long serialVersionUID = 466907365371998598L;
    private final boolean uniqueMatch;
    private final String tagName;
    private final IMatchEvaluator<E, IXPathContext<E>> evaluator;

    public ChildEvaluatorSelector(boolean uniqueMatch, String tagName, IMatchEvaluator<E, IXPathContext<E>> evaluator) {
        this.uniqueMatch = uniqueMatch;
        this.tagName = tagName;
        this.evaluator = evaluator;
    }

    public static <E> IXPathElementSelector<E> of(boolean uniqueMatch, String tagName,
                                                  IMatchEvaluator<E, IXPathContext<E>> evaluator) {
        if (evaluator == null)
            return ChildTagSelector.of(uniqueMatch, tagName);
        return new ChildEvaluatorSelector<>(uniqueMatch, tagName, evaluator);
    }

    public String toString() {
        return (uniqueMatch ? "#" : "") + tagName + "[" + evaluator + "]";
    }

    public boolean isUniqueMatch() {
        return uniqueMatch;
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
}