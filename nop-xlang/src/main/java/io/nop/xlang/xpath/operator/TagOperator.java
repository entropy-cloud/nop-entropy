/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpath.operator;

import io.nop.xlang.XLangConstants;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathOperator;

import java.io.ObjectStreamException;
import java.io.Serializable;

public class TagOperator<E> implements IXPathOperator<E>, Serializable {
    public static final TagOperator INSTANCE = new TagOperator();
    private static final long serialVersionUID = -9174641225813073538L;

    public String toString() {
        return XLangConstants.XPATH_OPERATOR_TAG;
    }

    private Object readResolve() throws ObjectStreamException {
        return INSTANCE;
    }

    @Override
    public Object apply(E node, IXPathContext<E> context) {
        return context.adapter().tagName(node);
    }
}