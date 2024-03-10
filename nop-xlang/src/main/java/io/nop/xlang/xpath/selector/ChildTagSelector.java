/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpath.selector;

import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathElementSelector;

public class ChildTagSelector<E> extends AbstractChildSelector<E> {
    private static final long serialVersionUID = -7833730526121648081L;
    private final boolean uniqueMatch;
    private final String tagName;

    public ChildTagSelector(boolean uniqueMatch, String tagName) {
        this.uniqueMatch = uniqueMatch;
        this.tagName = tagName;
    }

    public static <E> IXPathElementSelector<E> of(boolean uniqueMatch, String tagName) {
        if (tagName.equals("*"))
            return AnyChildSelector.INSTANCE;
        return new ChildTagSelector<>(uniqueMatch, tagName);
    }

    public String toString() {
        if (uniqueMatch)
            return "#" + tagName;
        return tagName;
    }

    @Override
    public boolean matches(E element, IXPathContext<E> ctx) {
        return tagName.equals(ctx.adapter().tagName(element));
    }
}