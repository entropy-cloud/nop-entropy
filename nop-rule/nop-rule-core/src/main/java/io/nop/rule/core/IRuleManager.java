/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core;

import io.nop.commons.cache.ICache;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.rule.core.model.RuleModel;

import java.util.Map;

public interface IRuleManager {

    IRuleRuntime newRuntime(ICache<Object, Object> cache, IEvalScope scope);

    IRuleRuntime newRuntime();

    IExecutableRule getRule(String ruleName, Long ruleVersion);

    IExecutableRule loadRuleFromPath(String path);

    /**
     * 根据规则名和版本号映射到 /nop/rule/{ruleName}/v{ruleVersion}.rule.xml模型文件
     */
    RuleModel getRuleModel(String ruleName, Long ruleVersion);

    RuleModel loadRuleModelFromPath(String path);

    default Map<String, Object> executeRule(String ruleName, Long ruleVersion, IRuleRuntime ruleRt) {
        return getRule(ruleName, ruleVersion).executeForOutputs(ruleRt);
    }
}