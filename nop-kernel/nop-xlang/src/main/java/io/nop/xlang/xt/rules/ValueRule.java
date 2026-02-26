/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt.rules;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xt.IXTransformContext;

public class ValueRule extends AbstractSelectorRule {
    private final IEvalAction valueExpr;

    public ValueRule(IEvalAction valueExpr, boolean mandatory) {
        super(null, mandatory);
        this.valueExpr = valueExpr;
    }

    @Override
    public void apply(XNode parent, XNode node, IXTransformContext context) {
        context.getEvalScope().setLocalValue("node", node);
        context.getEvalScope().setLocalValue("context", context);
        context.getEvalScope().setLocalValue("params", context.getParameters());

        Object value = valueExpr.invoke(context.getEvalScope());
        context.getOutput().setValue(value);
    }

    public IEvalAction getValueExpr() {
        return valueExpr;
    }
}
