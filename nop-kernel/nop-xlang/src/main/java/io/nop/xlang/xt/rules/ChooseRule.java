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

import java.util.Collections;
import java.util.List;

public class ChooseRule implements IXTransformRule {
    private final List<WhenClause> whenClauses;
    private final IXTransformRule otherwiseRule;

    public ChooseRule(List<WhenClause> whenClauses, IXTransformRule otherwiseRule) {
        this.whenClauses = whenClauses != null ? whenClauses : Collections.emptyList();
        this.otherwiseRule = otherwiseRule;
    }

    @Override
    public void apply(XNode parent, XNode node, IXTransformContext context) {
        for (WhenClause clause : whenClauses) {
            if (evaluateCondition(clause.getCondition(), node, context)) {
                if (clause.getRule() != null) {
                    clause.getRule().apply(parent, node, context);
                }
                return;
            }
        }

        if (otherwiseRule != null) {
            otherwiseRule.apply(parent, node, context);
        }
    }

    private boolean evaluateCondition(IEvalAction condition, XNode node, IXTransformContext context) {
        if (condition == null) {
            return false;
        }
        context.getEvalScope().setLocalValue("node", node);
        context.getEvalScope().setLocalValue("context", context);
        context.getEvalScope().setLocalValue("params", context.getParameters());
        Object result = condition.invoke(context.getEvalScope());
        return Boolean.TRUE.equals(result);
    }

    public List<WhenClause> getWhenClauses() {
        return whenClauses;
    }

    public IXTransformRule getOtherwiseRule() {
        return otherwiseRule;
    }

    public static class WhenClause {
        private final IEvalAction condition;
        private final IXTransformRule rule;

        public WhenClause(IEvalAction condition, IXTransformRule rule) {
            this.condition = condition;
            this.rule = rule;
        }

        public IEvalAction getCondition() {
            return condition;
        }

        public IXTransformRule getRule() {
            return rule;
        }
    }
}
