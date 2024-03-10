/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.dao;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopRuleErrors {
    String ARG_RULE_NAME = "ruleName";
    String ARG_RULE_GROUP = "ruleGroup";

    String ARG_PATH = "path";
    ErrorCode ERR_RULE_UNKNOWN_RULE_DEFINITION =
            define("nop.err.rule.unknown-rule-definition",
                    "未知的规则定义：ruleName={}", ARG_RULE_NAME, ARG_RULE_GROUP);

    ErrorCode ERR_RULE_INVALID_DAO_RESOURCE_PATH =
            define("nop.err.rule.invalid-dao-resource-path",
                    "资源路径的格式应该是ruleName/ruleVersion两个部分组成", ARG_PATH);
}
