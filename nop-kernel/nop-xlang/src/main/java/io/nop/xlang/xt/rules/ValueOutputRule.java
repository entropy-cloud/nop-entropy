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
import io.nop.xlang.xt.IXTransformRule;

/**
 * 用于处理 XtRuleGroupModel 的 value 字段， * 当节点本身作为转换规则时（如 &lt;div&gt;test&lt;/div&gt;），
 * value 字段包含要输出的文本内容（可能包含表达式）
 */
public class ValueOutputRule implements IXTransformRule {
    private final IEvalAction valueExpr;

    public ValueOutputRule(IEvalAction valueExpr) {
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
