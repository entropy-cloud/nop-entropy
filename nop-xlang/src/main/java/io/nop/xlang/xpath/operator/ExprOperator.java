/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpath.operator;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathOperator;

public class ExprOperator<E> implements IXPathOperator<E> {
    private final IEvalAction action;

    public ExprOperator(IEvalAction action) {
        this.action = action;
    }

    @Override
    public Object apply(E node, IXPathContext<E> context) {
        E thisNode = context.getThisNode();
        try {
            context.setThisNode(node);
            return action.invoke(context);
        } finally {
            context.setThisNode(thisNode);
        }
    }
}