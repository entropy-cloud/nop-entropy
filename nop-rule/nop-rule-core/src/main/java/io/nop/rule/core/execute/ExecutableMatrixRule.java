/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.core.execute;

import io.nop.api.core.util.Guard;
import io.nop.commons.collections.MutableIntArray;
import io.nop.commons.mutable.MutableInt;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;

public class ExecutableMatrixRule implements IExecutableRule {
    private final boolean multiMatch;
    private final RuleDecider rowDecider;
    private final RuleDecider colDecider;
    private final IEvalAction[][] outputs;

    public ExecutableMatrixRule(
            boolean multiMatch,
            RuleDecider rowDecider,
            RuleDecider colDecider,
            IEvalAction[][] outputs) {
        this.multiMatch = multiMatch;
        this.rowDecider = Guard.notNull(rowDecider, "rowDecider");
        this.colDecider = Guard.notNull(colDecider, "colDecider");
        this.outputs = Guard.notEmpty(outputs, "outputs");
    }

    @Override
    public boolean execute(IRuleRuntime ruleRt) {
        if (multiMatch) {
            MutableIntArray rowIndexes = new MutableIntArray();
            if (!rowDecider.execute(ruleRt, p -> rowIndexes.add(p.getLeafIndex())))
                return false;

            return colDecider.execute(ruleRt, p -> {
                int colIndex = p.getLeafIndex();
                for (int rowIndex : rowIndexes) {
                    outputs[rowIndex][colIndex].invoke(ruleRt);
                }
            });
        } else {
            MutableInt matched = new MutableInt();
            if (!rowDecider.execute(ruleRt, p -> matched.set(p.getLeafIndex())))
                return false;

            return colDecider.execute(ruleRt, p -> {
                outputs[matched.get()][p.getLeafIndex()].invoke(ruleRt);
            });
        }
    }
}
