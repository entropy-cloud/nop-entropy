/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface ExcelErrors {
    String ARG_SHEET_NAME = "sheetName";
    String ARG_ALLOWED_NAMES = "allowedNames";
    String ARG_CELL_POS = "cellPos";

    String ARG_BIZ_OBJ_NAME = "bizObjName";
    String ARG_FIELD_NAME = "fieldName";
    String ARG_DISPLAY_NAME = "displayName";
    String ARG_NAME_PATTERN = "namePattern";
    String ARG_PROP_PATH = "propPath";

    String ARG_FIELD_LABEL = "fieldLabel";

    String ARG_ALLOWED_VALUES = "allowedValues";

    String ARG_ROW_NUMBER = "rowNumber";
    String ARG_COL_NUMBER = "colNumber";

    String ARG_DICT_NAME = "dictName";

    String ARG_NAME = "name";
    String ARG_KEY = "key";

    String ARG_KEY_PROP = "keyProp";

    String ARG_PAPER_SIZE = "paperSize";

    String ARG_ERROR_DESC = "errorDesc";

    String ARG_ALIAS = "alias";

    String ARG_OLD_NAME = "oldName";
    String ARG_NEW_NAME = "newName";
    String ARG_PRECISION = "precision";
    String ARG_SCALE = "scale";

    String ARG_CELL_REF = "cellRef";

    ErrorCode ERR_IMPORT_UNKNOWN_SHEET = define("nop.err.excel.import.unknown-sheet",
            "未定义的Excel表格:[{sheetName}],允许的名称为{allowedNames}", ARG_SHEET_NAME, ARG_ALLOWED_NAMES);

    ErrorCode ERR_IMPORT_UNKNOWN_FIELD = define("nop.err.excel.import.unknown-field",
            "表格[{sheetName}]的单元格[{cellPos}]中指定的字段名[{fieldName}]没有定义，允许的字段名为{allowedNames}", ARG_SHEET_NAME,
            ARG_CELL_POS, ARG_FIELD_NAME, ARG_ALLOWED_NAMES);

    ErrorCode ERR_IMPORT_INVALID_DATA_ROW = define("nop.err.excel.import.invalid-data-row",
            "表格[{sheetName}]的第[{rowNumber}]行无法被解析", ARG_SHEET_NAME, ARG_ROW_NUMBER);

    ErrorCode ERR_IMPORT_LIST_SHEET_MODEL_MUST_HAS_FIELD_ATTR = define(
            "nop.err.excel.import.list-sheet-model-must-has-field-attr", "表格[{sheetName}]设置了list=true，则它的field属性必须被设置",
            ARG_SHEET_NAME);

    ErrorCode ERR_IMPORT_MISSING_MANDATORY_FIELD = define("nop.err.excel.import.missing-mandatory-field",
            "表格[{sheetName}]中缺少必填字段[{fieldName}({fieldLabel})]", ARG_SHEET_NAME, ARG_FIELD_NAME, ARG_FIELD_LABEL);

    ErrorCode ERR_IMPORT_MISSING_MANDATORY_SHEET = define("nop.err.excel.import.missing-mandatory-sheet",
            "缺少表格[{sheetName}]，名称模式为[{namePattern}]", ARG_SHEET_NAME, ARG_NAME_PATTERN);

    ErrorCode ERR_IMPORT_MANDATORY_FIELD_IS_EMPTY = define("nop.err.excel.import.mandatory-field-is-empty",
            "表格[{sheetName}]的必填字段[{fieldName}:{displayName}]的值为空，单元格位置:{cellPos}", ARG_SHEET_NAME, ARG_FIELD_NAME,
            ARG_DISPLAY_NAME, ARG_CELL_POS);

    ErrorCode ERR_IMPORT_FIELD_VALUE_NOT_IN_DICT = define("nop.err.excel.import.field-value-not-in-dict",
            "表格[{sheetName}]中的单元格[{cellPos}]的值不在字典表[{dictName}]中", ARG_SHEET_NAME, ARG_CELL_POS, ARG_DICT_NAME);

    ErrorCode ERR_IMPORT_FIELD_VALUE_VALIDATE_FAIL =
            define("nop.err.excel.import.field-value-validate-fail",
                    "表格[{sheetName}]中的单元格[{cellPos}]的值验证失败: {errorDesc}",
                    ARG_SHEET_NAME, ARG_CELL_POS, ARG_ERROR_DESC);

    ErrorCode ERR_IMPORT_MULTIPLE_ITEM_WITH_SAME_KEY = define("nop.err.excel.import.multiple-item-with-same-key",
            "列表属性[{fieldName}]中多个条目的属性[{keyProp}]的值相同：{key}", ARG_FIELD_NAME, ARG_KEY, ARG_KEY_PROP);

    ErrorCode ERR_EXCEL_INVALID_PAPER_SIZE =
            define("nop.err.excel.invalid-paper-size", "页面大小[{paperSize}]不是允许的值", ARG_PAPER_SIZE);


    ErrorCode ERR_IMPORT_UNKNOWN_GROUP_FIELD =
            define("nop.err.excel.import.unknown-group-field",
                    "未知的分组字段:{fieldName}", ARG_FIELD_NAME);

    ErrorCode ERR_IMPORT_SHEET_WITH_DUPLICATE_KEY_PROP =
            define("nop.err.excel.import-sheet-with-duplicate-key-prop",
                    "导入表格[{sheetName}]时[{keyProp}]的属性重复", ARG_SHEET_NAME, ARG_KEY_PROP);

    ErrorCode ERR_EXCEL_UNKNOWN_SHEET =
            define("nop.err.excel.unknown-sheet", "Excel文件中没有找到表格:[{sheetName}]", ARG_SHEET_NAME);

    ErrorCode ERR_IMP_DUPLICATE_SHEET_ALIAS =
            define("nop.err.imp.duplicate-sheet-alias",
                    "发现重复的表单别名：别名'{alias}'同时被表单'{oldName}'和'{newName}'使用",
                    ARG_ALIAS, ARG_OLD_NAME, ARG_NEW_NAME);

    // Data validation error codes
    ErrorCode ERR_EXCEL_VALIDATION_INVALID_VALUE =
            define("nop.err.excel.validation.invalid-value",
                    "[{displayName}]的值不符合要求", ARG_DISPLAY_NAME);

    ErrorCode ERR_EXCEL_VALIDATION_FIELD_VALUE_INVALID =
            define("nop.err.excel.validation.field-value-invalid",
                    "字段[{fieldName}]的值不符合要求", ARG_FIELD_NAME);

    ErrorCode ERR_EXCEL_VALIDATION_PATTERN_MISMATCH =
            define("nop.err.excel.validation.pattern-mismatch",
                    "输入的值必须符合格式要求：[{pattern}]", ARG_NAME_PATTERN);

    ErrorCode ERR_EXCEL_VALIDATION_INTEGER_REQUIRED =
            define("nop.err.excel.validation.integer-required",
                    "请输入有效的整数");

    ErrorCode ERR_EXCEL_VALIDATION_TITLE =
            define("nop.err.excel.validation.title",
                    "数据验证错误");

    ErrorCode ERR_EXCEL_VALIDATION_DECIMAL_REQUIRED =
            define("nop.err.excel.validation.decimal-required",
                    "请输入有效的小数");

    ErrorCode ERR_EXCEL_VALIDATION_DECIMAL_PRECISION_INVALID =
            define("nop.err.excel.validation.decimal-precision-invalid",
                    "请输入有效的小数，最多[{precision}]位数字，其中小数位最多[{scale}]位",
                    ARG_PRECISION, ARG_SCALE);

    ErrorCode ERR_EXCEL_VALIDATION_DATE_REQUIRED =
            define("nop.err.excel.validation.date-required",
                    "请输入有效的日期");

    ErrorCode ERR_EXCEL_VALIDATION_DATETIME_REQUIRED =
            define("nop.err.excel.validation.datetime-required",
                    "请输入有效的日期时间");

    ErrorCode ERR_EXCEL_VALIDATION_TIME_REQUIRED =
            define("nop.err.excel.validation.time-required",
                    "请输入有效的时间");

    ErrorCode ERR_EXCEL_VALIDATION_TEXT_LENGTH_INVALID =
            define("nop.err.excel.validation.text-length-invalid",
                    "文本长度不符合要求");

    ErrorCode ERR_EXCEL_VALIDATION_BOOLEAN_REQUIRED =
            define("nop.err.excel.validation.boolean-required",
                    "请输入TRUE或FALSE");

    ErrorCode ERR_EXCEL_INVALID_CELL_REF =
            define("nop.err.excel.invalid-cell-ref",
                    "非法的单元格引用：{cellRef}", ARG_CELL_REF);
}
