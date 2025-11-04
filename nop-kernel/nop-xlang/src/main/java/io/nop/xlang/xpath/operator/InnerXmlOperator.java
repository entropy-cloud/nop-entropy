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

public class InnerXmlOperator<E> implements IXPathOperator<E>, Serializable {
    public static final InnerXmlOperator INSTANCE = new InnerXmlOperator();
    private static final long serialVersionUID = -547990668488288431L;

    public String toString() {
        return XLangConstants.XPATH_OPERATOR_INNER_XML;
    }

    private Object readResolve() throws ObjectStreamException {
        return INSTANCE;
    }

    @Override
    public Object apply(E node, IXPathContext<E> context) {
        return context.adapter().innerXml(node);
    }
}
