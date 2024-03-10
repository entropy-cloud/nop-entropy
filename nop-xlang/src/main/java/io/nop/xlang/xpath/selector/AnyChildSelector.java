/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpath.selector;

import io.nop.xlang.xpath.IXPathContext;

public class AnyChildSelector<E> extends AbstractChildSelector<E> {
    public static final AnyChildSelector INSTANCE = new AnyChildSelector();
    private static final long serialVersionUID = -3311311069327266553L;

    private Object readResolve() {
        return INSTANCE;
    }

    public String toString() {
        return "*";
    }

    @Override
    public boolean matches(E element, IXPathContext<E> ctx) {
        return true;
    }
}