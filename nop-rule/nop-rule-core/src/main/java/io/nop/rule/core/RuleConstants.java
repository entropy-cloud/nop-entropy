/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core;

public interface RuleConstants {
    String SHEET_NAME_RULE = "Rule";
    String SHEET_NAME_RULE_LIST = "RuleList";
    String SHEET_NAME_CONFIG = "Config";

    String XDSL_SCHEMA_RULE = "/nop/schema/rule.xdef";

    String IMP_PATH_RULE = "/nop/rule/imp/rule.imp.xml";

    String FIELD_DECISION_TREE = "decisionTree";

    String FIELD_DECISION_MATRIX = "decisionMatrix";

    String RULE_FLAG_MATRIX = "M";

    String NAME_VAR = "var";

    String NAME_VALUE_EXPR = "valueExpr";

    String NAME_MULTI_MATCH = "multiMatch";

    String NAME_ID = "id";

    String MESSAGE_MISMATCH = "MISMATCH";
    String MESSAGE_MATCH = "MATCH";

    String VAR_RULE_RT = "ruleRt";

    String VAR_RESULT = "result";

    String VAR_RULE_MATCH = "ruleMatch";

    String FILE_TYPE_RULE_XLSX = "rule.xlsx";

    String FILE_TYPE_RULE_XML = "rule.xml";

    String RESOLVE_RULE_NS_PREFIX = "resolve-rule:";

    String ENUM_RULE_TYPE_TREE = "TREE";

    String ENUM_RULE_TYPE_MATX = "MATX";

    String FIELD_LOG_MESSAGES = "logMessages";

    String WEIGHT_NAME_POSTFIX = "__weight";
}
