/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.execute;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.RuleConstants;

import java.util.List;
import java.util.function.Consumer;

public class RuleDecider implements ISourceLocationGetter {
    private final SourceLocation location;
    private final String id;
    private final String label;
    private final IEvalPredicate predicate;
    private final boolean multiMatch;
    private final int leafIndex;
    private final List<RuleDecider> children;

    public RuleDecider(SourceLocation location, String id, String label,
                       IEvalPredicate predicate,
                       boolean multiMatch, int leafIndex,
                       List<RuleDecider> children) {
        this.location = location;
        this.id = id;
        this.label = label;
        this.predicate = Guard.notNull(predicate, "predicate");
        this.multiMatch = multiMatch;
        this.leafIndex = leafIndex;
        this.children = children == null || children.isEmpty() ? null : children;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public IEvalPredicate getPredicate() {
        return predicate;
    }

    public boolean isMultiMatch() {
        return multiMatch;
    }

    public int getLeafIndex() {
        return leafIndex;
    }

    public boolean test(IRuleRuntime ruleRt) {
        if (!predicate.passConditions(ruleRt)) {
            if (id != null || label != null)
                ruleRt.logMessage(RuleConstants.MESSAGE_MISMATCH, id, label);
            return false;
        }

        if (id != null || label != null)
            ruleRt.logMessage(RuleConstants.MESSAGE_MATCH, id, label);

        return true;
    }

    public boolean execute(IRuleRuntime ruleRt, Consumer<RuleDecider> task) {
        if (!test(ruleRt))
            return false;

        if (children == null) {
            task.accept(this);
            return true;
        } else {
            boolean ret = false;
            for (RuleDecider child : children) {
                if (child.execute(ruleRt, task)) {
                    if (!multiMatch)
                        return true;
                    ret = true;
                }
            }
            return ret;
        }
    }

    public boolean isAnyChildMultiMatch() {
        if (multiMatch)
            return true;
        if (children != null) {
            return children.stream().anyMatch(RuleDecider::isAnyChildMultiMatch);
        }
        return false;
    }

    public int getMaxLeafIndex() {
        if (children == null)
            return leafIndex;

        int max = 0;
        for (RuleDecider child : children) {
            max = Math.max(max, child.getMaxLeafIndex());
        }
        return max;
    }
}