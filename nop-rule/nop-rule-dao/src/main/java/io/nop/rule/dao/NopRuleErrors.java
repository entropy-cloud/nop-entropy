package io.nop.rule.dao;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopRuleErrors {
    String ARG_RULE_NAME = "ruleName";
    String ARG_RULE_GROUP = "ruleGroup";
    ErrorCode ERR_RULE_UNKNOWN_RULE_DEFINITION =
            define("nop.err.rule.dao.unknown-rule-definition",
                    "未知的规则定义：ruleName={},ruleGroup={}", ARG_RULE_NAME, ARG_RULE_GROUP);
}
