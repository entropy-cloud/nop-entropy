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
import io.nop.xlang.xt.IXTransformRule;
import io.nop.xlang.xt.IXTransformContext;

public class IfRule implements IXTransformRule {
    private final IEvalAction condition;
    private final IXTransformRule thenRule;

    public IfRule(IEvalAction condition, IXTransformRule thenRule) {
        this.condition = condition;
        this.thenRule = thenRule;
    }

    @Override
    public void apply(XNode parent, XNode node, IXTransformContext context) {
        if (evaluateCondition(node, context)) {
            if (thenRule != null) {
                thenRule.apply(parent, node, context);
            }
        }
    }

    private boolean evaluateCondition(XNode node, IXTransformContext context) {
        context.getEvalScope().setLocalValue("node", node);
        context.getEvalScope().setLocalValue("context", context);
        context.getEvalScope().setLocalValue("params", context.getParameters());
        Object result = condition.invoke(context.getEvalScope());
        return Boolean.TRUE.equals(result);
    }

    public IEvalAction getCondition() {
        return condition;
    }

    public IXTransformRule getThenRule() {
        return thenRule;
    }
}
