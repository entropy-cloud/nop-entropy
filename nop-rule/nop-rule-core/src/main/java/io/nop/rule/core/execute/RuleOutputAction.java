package io.nop.rule.core.execute;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.rule.core.IRuleRuntime;

public class RuleOutputAction implements IEvalAction {
    private final String varName;
    private final IEvalAction valueExpr;

    public RuleOutputAction(String varName, IEvalAction valueExpr) {
        this.varName = varName;
        this.valueExpr = valueExpr;
    }

    @Override
    public Object invoke(IEvalContext ctx) {
        IRuleRuntime ruleRt = IRuleRuntime.fromEvalContext(ctx);
        Object ret = valueExpr.invoke(ruleRt);
        ruleRt.addOutput(varName, ret);
        return ret;
    }
}