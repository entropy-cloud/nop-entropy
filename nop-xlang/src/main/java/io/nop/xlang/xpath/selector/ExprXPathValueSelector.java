/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpath.selector;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.functional.select.ISelectionCollector;
import io.nop.commons.functional.select.SelectResult;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathValueSelector;

import java.util.Collections;
import java.util.List;

public class ExprXPathValueSelector<E> implements IXPathValueSelector<E, Object> {
    static final SourceLocation s_loc = SourceLocation.fromClass(ExprXPathValueSelector.class);
    private static final long serialVersionUID = 1333520915181347664L;

    private final IEvalAction expr;
    private final boolean returnElement;

    public ExprXPathValueSelector(IEvalAction expr, boolean returnElement) {
        this.expr = expr;
        this.returnElement = returnElement;
    }

    public boolean isReturnElement() {
        return returnElement;
    }

    Object getValue(E source, IXPathContext<E> context) {
        E thisNode = context.getThisNode();
        try {
            context.setThisNode(source);
            return expr.invoke(context);
        } finally {
            context.setThisNode(thisNode);
        }
    }

    @Override
    public SelectResult select(E source, IXPathContext<E> context, ISelectionCollector<Object> collector) {
        Object value = getValue(source, context);
        if (value == null)
            return SelectResult.NOT_FOUND;
        return collector.collect(value);
    }

    @Override
    public Object select(E source, IXPathContext<E> context) {
        return getValue(source, context);
    }

    @Override
    public List<Object> selectAll(E source, IXPathContext<E> context) {
        return Collections.singletonList(getValue(source, context));
    }
}