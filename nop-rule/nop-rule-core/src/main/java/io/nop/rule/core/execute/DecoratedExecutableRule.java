package io.nop.rule.core.execute;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.RuleConstants;

public class DecoratedExecutableRule implements IExecutableRule {
    private final IEvalAction beforeExecute;
    private final IExecutableRule rule;
    private final IEvalAction afterExecute;

    public DecoratedExecutableRule(IEvalAction beforeExecute,
                                   IExecutableRule rule,
                                   IEvalAction afterExecute) {
        this.beforeExecute = beforeExecute;
        this.rule = rule;
        this.afterExecute = afterExecute;
    }

    @Override
    public boolean execute(IRuleRuntime ruleRt) {
        if (beforeExecute != null)
            beforeExecute.invoke(ruleRt);
        boolean b = rule.execute(ruleRt);
        if (afterExecute != null) {
            ruleRt.getEvalScope().setLocalValue(RuleConstants.VAR_RULE_MATCH, b);
            afterExecute.invoke(ruleRt);
        }
        return b;
    }
}
