/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.api.support;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IVariableScope;
import io.nop.rule.api.IRuleRuntime;
import io.nop.rule.api.beans.RuleOutputBean;
import io.nop.rule.api.beans.RuleResultBean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.rule.api.RuleApiErrors.ARG_LOC2;
import static io.nop.rule.api.RuleApiErrors.ARG_RULE_NAME;
import static io.nop.rule.api.RuleApiErrors.ERR_RULE_NAME_NOT_UNIQUE;

public class DefaultRuleRuntime implements IRuleRuntime {
    private IVariableScope evalScope;
    private Map<String, Object> outputs = new HashMap<>();
    private Map<String, RuleResultBean> resultMap = new HashMap<>();
    private List<RuleResultBean> results = new ArrayList<>();

    public DefaultRuleRuntime() {
    }

    public DefaultRuleRuntime(IVariableScope scope) {
        this.setEvalScope(scope);
    }

    @Override
    public IVariableScope getEvalScope() {
        return evalScope;
    }

    public void setEvalScope(IVariableScope scope) {
        this.evalScope = scope;
    }

    @Override
    public Map<String, Object> getOutputs() {
        return outputs;
    }

    @Override
    public void clearOutputs() {
        outputs.clear();
    }

    @Override
    public List<RuleResultBean> getResults() {
        return results;
    }

    @Override
    public RuleResultBean getRuleResult(String ruleName) {
        return resultMap.get(ruleName);
    }

    @Override
    public void addOutput(RuleOutputBean output) {
        if (RuleOutputBean.OP_APPEND.equals(output.getOp())) {
            appendOutput(output);
        } else {
            outputs.put(output.getName(), output.getValue());
        }
    }

    protected void appendOutput(RuleOutputBean output) {
        String name = output.getName();
        // 忽略空值
        Object value = output.getValue();
        if (value == null)
            return;

        Object oldValue = outputs.get(name);

        // 将oldValue规范化为List类型
        List<Object> list;
        if (oldValue == null) {
            list = new ArrayList<>();
            outputs.put(name, list);
        } else if (oldValue instanceof List) {
            list = (List<Object>) oldValue;
        } else {
            list = new ArrayList<>();
            list.add(oldValue);
            outputs.put(name, list);
        }

        if (value instanceof Collection) {
            list.addAll((Collection<?>) value);
        } else {
            list.add(value);
        }
    }

    @Override
    public void addRuleResult(RuleResultBean ruleResult) {
        results.add(ruleResult);
        if (ruleResult.getRuleName() != null) {
            RuleResultBean oldResult = resultMap.put(ruleResult.getRuleName(), ruleResult);
            if (oldResult != null) {
                throw new NopException(ERR_RULE_NAME_NOT_UNIQUE).loc(ruleResult.getLocation())
                        .param(ARG_RULE_NAME, ruleResult.getRuleName()).param(ARG_LOC2, oldResult.getLocation());
            }
        }
        addOutputs(ruleResult.getOutputs());
    }

    @Override
    public void clearRuleResults() {
        results.clear();
    }
}
