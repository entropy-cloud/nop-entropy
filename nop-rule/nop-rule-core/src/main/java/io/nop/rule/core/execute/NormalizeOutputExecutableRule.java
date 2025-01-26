/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.execute;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.utils.Underscore;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.model.RuleAggregateMethod;
import io.nop.rule.core.model.RuleOutputDefineModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.nop.rule.api.RuleApiErrors.ARG_RULE_NAME;
import static io.nop.rule.core.RuleErrors.ARG_DISPLAY_NAME;
import static io.nop.rule.core.RuleErrors.ARG_OUTPUT_NAME;
import static io.nop.rule.core.RuleErrors.ARG_VAR_NAME;
import static io.nop.rule.core.RuleErrors.ERR_RULE_AGGREGATE_WEIGHT_SIZE_NOT_MATCH;
import static io.nop.rule.core.RuleErrors.ERR_RULE_OUTPUT_VAR_NOT_ALLOW_EMPTY;

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

        for (RuleOutputDefineModel output : outputs) {
            Object value = ruleRt.getOutput(output.getName());
            if (StringHelper.isEmptyObject(value) && output.isMandatory())
                throw new NopException(ERR_RULE_OUTPUT_VAR_NOT_ALLOW_EMPTY).source(output).param(ARG_RULE_NAME, ruleRt.getRuleName())
                        .param(ARG_VAR_NAME, output.getName()).param(ARG_DISPLAY_NAME, output.getDisplayName());

            if (value != null && output.getType() != null) {
                value = BeanTool.castBeanToType(value, output.getType());
                ruleRt.setOutput(output.getName(), value);
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

        boolean useWeight = output.isUseWeight();
        List<Double> weights = null;
        if (useWeight) {
            weights = getWeights(ruleRt, output.getWeightName());
            if (weights.isEmpty()) {
                useWeight = false;
            } else {
                if (weights.size() != list.size()) {
                    throw new NopException(ERR_RULE_AGGREGATE_WEIGHT_SIZE_NOT_MATCH)
                            .param(ARG_RULE_NAME, ruleRt.getRuleName()).param(ARG_OUTPUT_NAME, name);
                }

                List<Object> withWeights = new ArrayList<>(list.size());
                for (int i = 0; i < list.size(); i++) {
                    withWeights.add(MathHelper.multiply(list.get(i), weights.get(i)));
                }
                list = withWeights;
            }
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
            case weighted_avg: {
                Object value = Underscore.sum(list);
                Number sumWeight = list.size();
                if (useWeight) {
                    sumWeight = Underscore.sum(weights);
                }
                value = MathHelper.divide(value, sumWeight);
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

    private List<Double> getWeights(IRuleRuntime ruleRt, String name) {
        List<Object> list = ruleRt.getOutputList(name);
        if (list == null || list.isEmpty())
            return Collections.emptyList();
        List<Double> weights = new ArrayList<>(list.size());
        for (Object obj : list) {
            weights.add(ConvertHelper.toPrimitiveDouble(obj, 1.0,
                    err -> new NopException(err)
                            .param(ARG_RULE_NAME, ruleRt.getRuleName()).param(ARG_OUTPUT_NAME, name)));
        }
        return weights;
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