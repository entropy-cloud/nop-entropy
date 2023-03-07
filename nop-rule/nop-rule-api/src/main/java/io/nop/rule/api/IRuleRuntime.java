/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.api;

import io.nop.api.core.util.IVariableScope;
import io.nop.rule.api.beans.RuleOutputBean;
import io.nop.rule.api.beans.RuleResultBean;

import java.util.List;
import java.util.Map;

public interface IRuleRuntime {

    /**
     * 检查规则的匹配条件时所使用的上下文对象
     */
    IVariableScope getEvalScope();

    Map<String, Object> getOutputs();

    default Object getOutput(String name) {
        return getOutputs().get(name);
    }

    void clearOutputs();

    List<RuleResultBean> getResults();

    /**
     * 获取
     *
     * @param ruleName 规则的name
     * @return null表示此前没有执行过对应名称的规则
     */
    RuleResultBean getRuleResult(String ruleName);

    /**
     * 记录单个规则节点的匹配结果。
     */
    void addRuleResult(RuleResultBean ruleResult);

    void clearRuleResults();

    default void addOutputs(List<RuleOutputBean> outputs) {
        if (outputs == null || outputs.isEmpty())
            return;

        for (RuleOutputBean output : outputs) {
            addOutput(output);
        }
    }

    void addOutput(RuleOutputBean output);
}