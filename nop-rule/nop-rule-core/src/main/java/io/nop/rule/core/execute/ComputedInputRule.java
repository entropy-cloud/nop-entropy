package io.nop.rule.core.execute;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.model.RuleInputDefineModel;

import java.util.List;

public class ComputedInputRule implements IExecutableRule {
    private final List<RuleInputDefineModel> vars;
    private final IExecutableRule rule;

    public ComputedInputRule(List<RuleInputDefineModel> vars, IExecutableRule rule) {
        this.vars = vars;
        this.rule = rule;
    }

    @Override
    public boolean execute(IRuleRuntime ruleRt) {
        for (RuleInputDefineModel var : vars) {
            String name = var.getName();
            IEvalAction expr = var.getDefaultExpr();
            Object value = expr == null ? null : expr.invoke(ruleRt);
            ruleRt.getEvalScope().setLocalValue(var.getLocation(), name, value);
        }

        return rule.execute(ruleRt);
    }
}
