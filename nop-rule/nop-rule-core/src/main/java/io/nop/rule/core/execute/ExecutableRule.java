/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.execute;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.RuleConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ExecutableRule implements IExecutableRule, ISourceLocationGetter {
    static final Logger LOG = LoggerFactory.getLogger(ExecutableRule.class);

    private final SourceLocation loc;
    private final String id;
    private final String label;
    private final IEvalPredicate predicate;
    private final IEvalAction messageExpr;
    private final IEvalAction action;
    private final List<IExecutableRule> children;
    private final boolean multiMatch;

    public ExecutableRule(SourceLocation loc, String id, String label,
                          IEvalAction messageExpr,
                          IEvalPredicate predicate,
                          IEvalAction action,
                          List<IExecutableRule> children,
                          boolean multiMatch) {
        this.loc = loc;
        this.id = id;
        this.label = label;
        this.messageExpr = messageExpr;
        this.predicate = predicate;
        this.action = action;
        this.children = children == null || children.isEmpty() ? null : children;
        this.multiMatch = multiMatch;
    }

    protected String buildMessage(IRuleRuntime ruleRt, boolean passed) {
        if (ruleRt.isCollectLogMessage() && messageExpr != null) {
            try {
                Object result = messageExpr.invoke(ruleRt);
                if (result != null) return result.toString();
            } catch (Exception e) {
                LOG.warn("rule:message-expr-error,id={}", id, e);
            }
        }
        return passed ? RuleConstants.MESSAGE_MATCH : RuleConstants.MESSAGE_MISMATCH;
    }

    @Override
    public SourceLocation getLocation() {
        return loc;
    }

    @Override
    public boolean execute(IRuleRuntime ruleRt) {
        if (predicate != null && !predicate.passConditions(ruleRt)) {
            if (id != null || label != null)
                ruleRt.logMessage(buildMessage(ruleRt, false), id, label);
            return false;
        }

        if (id != null || label != null)
            ruleRt.logMessage(buildMessage(ruleRt, true), id, label);

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