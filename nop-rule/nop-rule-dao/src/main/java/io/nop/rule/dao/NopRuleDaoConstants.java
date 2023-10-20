/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.dao;

public interface NopRuleDaoConstants extends _NopRuleDaoConstants {
    int RULE_STATUS_ACTIVE = 1;

    String RULE_TAG_NAME = "rule";

    String XDEF_PATH_RULE = "/nop/schema/rule.xdef";

    String INPUTS_NAME = "inputs";

    String OUTPUTS_NAME = "outputs";

    String BEFORE_EXECUTE_NAME = "beforeExecute";

    String DECISION_MATRIX_NAME = "decisionMatrix";
}
