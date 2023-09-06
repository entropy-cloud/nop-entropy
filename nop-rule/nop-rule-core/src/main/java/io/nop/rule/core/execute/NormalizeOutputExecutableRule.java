package io.nop.rule.core.execute;

import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.utils.Underscore;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.model.RuleAggregateMethod;
import io.nop.rule.core.model.RuleOutputDefineModel;

import java.util.List;

public class NormalizeOutputExecutableRule implements IExecutableRule {
    private final IExecutableRule rule;
    private final List<RuleOutputDefineModel> outputs;

    public NormalizeOutputExecutableRule(List<RuleOutputDefineModel> outputs, IExecutableRule rule) {
        this.rule = rule;
        this.outputs = outputs;
    }

    @Override
    public boolean execute(IRuleRuntime ruleRt) {
        boolean b = rule.execute(ruleRt);
        if (b) {
            for (RuleOutputDefineModel output : outputs) {
                aggOutput(output, ruleRt);
            }
        }
        return b;
    }

    private void aggOutput(RuleOutputDefineModel output, IRuleRuntime ruleRt) {
        String name = output.getName();
        RuleAggregateMethod aggMethod = output.getAggregate();
        if (aggMethod == null) {
            aggMethod = RuleAggregateMethod.last;
        }
        List<Object> list = ruleRt.getOutputList(name);
        if (list == null) {
            Object defaultValue = getDefaultValue(output, ruleRt);
            if (defaultValue != null) {
                ruleRt.setOutput(name, defaultValue);
            }
            return;
        }

        switch (aggMethod) {
            case min: {
                Object value = Underscore.min(list);
                ruleRt.setOutput(name, value);
                break;
            }
            case max: {
                Object value = Underscore.max(list);
                ruleRt.setOutput(name, value);
                break;
            }
            case sum: {
                Object value = Underscore.sum(list);
                ruleRt.setOutput(name, value);
                break;
            }
            case avg: {
                Object value = Underscore.avg(list);
                ruleRt.setOutput(name, value);
                break;
            }
            case list: {
                ruleRt.setOutput(name, list);
                break;
            }
            case first: {
                ruleRt.setOutput(name, CollectionHelper.first(list));
                break;
            }
            default: {
                ruleRt.setOutput(name, CollectionHelper.last(list));
            }
        }
    }

    private Object getDefaultValue(RuleOutputDefineModel output, IRuleRuntime ruleRt) {
        IEvalAction defaultExpr = output.getDefaultExpr();
        if (defaultExpr == null) {
            return null;
        } else {
            return defaultExpr.invoke(ruleRt);
        }
    }
}