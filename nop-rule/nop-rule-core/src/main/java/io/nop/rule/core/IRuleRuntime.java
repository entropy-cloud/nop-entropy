/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.core;

import io.nop.api.core.util.Guard;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;

import java.util.List;
import java.util.Map;

public interface IRuleRuntime extends IEvalContext {

    /**
     * 检查规则的匹配条件时所使用的上下文对象
     */
    IEvalScope getEvalScope();

    Map<String, List<Object>> getOutputLists();

    default List<Object> getOutputList(String name) {
        return getOutputLists().get(name);
    }

    void addOutput(String name, Object value);

    Map<String, Object> getOutputs();

    void setOutput(String name, Object value);

    void clearOutputs();

    void logMessage(String message, String ruleId, String ruleLabel);

    List<RuleLogMessage> getLogMessages();

    static IRuleRuntime fromEvalContext(IEvalContext context) {
        if (context instanceof IRuleRuntime)
            return (IRuleRuntime) context;

        IEvalScope scope = context.getEvalScope();
        IRuleRuntime ruleRt = (IRuleRuntime) scope.getValue(RuleConstants.VAR_RULE_RT);
        Guard.notNull(ruleRt, "ruleRt");
        return ruleRt;
    }
}