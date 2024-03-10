/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.api;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface RuleApiErrors {
    String ARG_RULE_NAME = "ruleName";
    String ARG_LOC2 = "loc2";

    ErrorCode ERR_RULE_NAME_NOT_UNIQUE = define("nop.err.rule.rule-name-not-unique", "规则名称不唯一:[{ruleName}]",
            ARG_RULE_NAME);

}
