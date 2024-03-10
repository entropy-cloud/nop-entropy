/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpath.evaluator;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.functional.select.IMatchEvaluator;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.xlang.xpath.IXPathContext;

public class ExprEvaluator<E> implements IMatchEvaluator<E, IXPathContext<E>> {
    private final IEvalAction action;

    public ExprEvaluator(IEvalAction action) {
        this.action = action;
    }

    @Override
    public boolean matches(E element, IXPathContext<E> context) {
        E node = context.getThisNode();
        try {
            context.setThisNode(element);
            return ConvertHelper.toPrimitiveBoolean(action.invoke(context));
        } finally {
            context.setThisNode(node);
        }
    }
}
