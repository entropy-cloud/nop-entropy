/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpath.operator;

import io.nop.xlang.XLangConstants;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathOperator;

import java.io.ObjectStreamException;
import java.io.Serializable;

public class IdentityOperator<E> implements IXPathOperator<E>, Serializable {
    public static final IdentityOperator INSTANCE = new IdentityOperator();
    private static final long serialVersionUID = -547990668488288431L;

    public String toString() {
        return XLangConstants.XPATH_OPERATOR_IDENTITY;
    }

    private Object readResolve() throws ObjectStreamException {
        return INSTANCE;
    }

    @Override
    public Object apply(E node, IXPathContext<E> context) {
        return node;
    }
}