package io.nop.rule.service;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopRuleErrors {
    String ARG_RULE_NAME = "ruleName";

    String ARG_USER_ROLES = "userRoles";

    ErrorCode ERR_RULE_NOT_ASSIGN_ROLES_FOR_RULE =
            define("nop.err.rule.not-assign-roles-for-rule", "没有为规则分配可访问的角色", ARG_RULE_NAME);

    ErrorCode ERR_RULE_CREATER_MUST_IN_ROLE_SET =
            define("nop.err.rule.creater-must-in-role-set",
                    "当前用户必须在规则的可访问角色集合中，用户不应该创建对自己不可见的规则", ARG_RULE_NAME, ARG_USER_ROLES);
}
