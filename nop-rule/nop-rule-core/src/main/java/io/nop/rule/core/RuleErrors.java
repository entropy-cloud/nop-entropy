package io.nop.rule.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface RuleErrors {
    String ARG_CELL_POS = "cellPos";
    String ARG_TEXT = "text";
    String ARG_VAR_NAME = "varName";

    ErrorCode ERR_RULE_WORKBOOK_NO_RULE_SHEET =
            define("nop.err.rule.workbook-no-rule-sheet",
                    "工作簿中没有名称为Rule的Sheet");

    ErrorCode ERR_RULE_WORKBOOK_NO_CONFIG_SHEET =
            define("nop.err.rule.workbook-no-config-sheet",
                    "工作簿中没有名称为Config的Sheet");

    ErrorCode ERR_RULE_INVALID_DECISION_TREE_TABLE =
            define("nop.err.rule.invalid-decision-tree-table",
                    "决策树配置表格式不正确");

    ErrorCode ERR_RULE_VAR_CELL_TEXT_IS_EMPTY =
            define("nop.err.rule.var-cell-text-is-empty",
                    "变量单元格[{cellPos}]的内容不能为空", ARG_CELL_POS);

    ErrorCode ERR_RULE_UNKNOWN_INPUT_VAR =
            define("nop.err.rule.unknown-input-var",
                    "未定义的输入变量：{varName}", ARG_VAR_NAME);

    ErrorCode ERR_RULE_UNKNOWN_OUTPUT_VAR =
            define("nop.err.rule.unknown-output-var",
                    "未定义的输出变量: {varName}", ARG_VAR_NAME);

    ErrorCode ERR_RULE_INVALID_INPUT_VAR =
            define("nop.err.rule.invalid-input-var", "输入变量格式必须是a或者a.b.c这种格式", ARG_TEXT);

    ErrorCode ERR_RULE_VAR_CELL_SPAN_MUST_BE_ONE =
            define("nop.err.rule.var-cell-span-must-be-one", "变量单元格的宽度只能是1，不允许合并单元格");

    ErrorCode ERR_RULE_INPUT_NO_LABEL =
            define("nop.err.rule.input-no-label", "输入变量显示标题不能为空");
}
