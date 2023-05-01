/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.core.execute;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.RuleConstants;

import java.util.List;

public class ExecutableRule implements IExecutableRule, ISourceLocationGetter {
    private final SourceLocation loc;
    private final String id;
    private final String label;
    private final IEvalPredicate predicate;
    private final IEvalAction action;
    private final List<IExecutableRule> children;
    private final boolean multiMatch;

    public ExecutableRule(SourceLocation loc, String id, String label,
                          IEvalPredicate predicate,
                          IEvalAction action,
                          List<IExecutableRule> children,
                          boolean multiMatch) {
        this.loc = loc;
        this.id = id;
        this.label = label;
        this.predicate = predicate;
        this.action = action;
        this.children = children == null || children.isEmpty() ? null : children;
        this.multiMatch = multiMatch;
    }

    @Override
    public SourceLocation getLocation() {
        return loc;
    }

    @Override
    public boolean execute(IRuleRuntime ruleRt) {
        if (predicate != null && !predicate.passConditions(ruleRt)) {
            ruleRt.logMessage(RuleConstants.MESSAGE_MISMATCH, id, label);
            return false;
        }

        ruleRt.logMessage(RuleConstants.MESSAGE_MATCH, id, label);

        if (action != null) {
            action.invoke(ruleRt);
        }

        if (children == null)
            return true;

        boolean match = false;
        for (IExecutableRule child : children) {
            if (child.execute(ruleRt)) {
                if (!multiMatch)
                    return true;
                match = true;
            }
        }
        return match;
    }
}