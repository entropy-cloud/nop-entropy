package io.nop.rule.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface RuleErrors {
    ErrorCode ERR_RULE_WORKBOOK_NO_RULE_SHEET =
            define("nop.err.rule.workbook-no-rule-sheet",
                    "工作簿中没有名称为Rule的Sheet");

    ErrorCode ERR_RULE_WORKBOOK_NO_CONFIG_SHEET =
            define("nop.err.rule.workbook-no-config-sheet",
                    "工作簿中没有名称为Config的Sheet");
}
