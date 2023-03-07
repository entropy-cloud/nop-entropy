/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpath.operator;

import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathOperator;

import java.io.Serializable;

public class AttrOperator<E> implements IXPathOperator<E>, Serializable {
    private static final long serialVersionUID = -7594426411039265382L;

    private final String attrName;

    public AttrOperator(String attrName) {
        this.attrName = attrName;
    }

    public String toString() {
        return "@" + attrName;
    }

    @Override
    public Object apply(E node, IXPathContext<E> context) {
        return context.adapter().attr(node, attrName);
    }
}
