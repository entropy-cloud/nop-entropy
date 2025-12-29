/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface XptErrors {
    String ARG_SHEET_NAME = "sheetName";
    String ARG_CELL_POS = "cellPos";
    String ARG_ROW_PARENT = "rowParent";
    String ARG_COL_PARENT = "colParent";

    String ARG_DS_NAME = "dsName";

    String ARG_PATH = "path";
    String ARG_FILE_TYPE = "fileType";
    String ARG_ALLOWED_FILE_TYPES = "allowedFileTypes";
    String ARG_RENDER_TYPE = "renderType";

    String ARG_PROP_NAME = "propName";
    String ARG_FIELD_NAME = "fieldName";

    String ARG_EXPR = "expr";
    String ARG_SIZE = "size";

    String ARG_REPORT_NAME = "reportName";

    String ARG_SOURCE = "source";
    ErrorCode ERR_XPT_INVALID_ROW_PARENT =
            define("nop.err.xpt.invalid-row-parent",
                    "表格[{sheetName}]的单元格[{cellPos}]的行父格[{rowParent}]必须配置为行展开",
                    ARG_SHEET_NAME, ARG_CELL_POS, ARG_ROW_PARENT);

    ErrorCode ERR_XPT_INVALID_COL_PARENT =
            define("nop.err.xpt.invalid-row-parent",
                    "表格[{sheetName}]的单元格[{cellPos}]的列父格[{colParent}]必须配置为列展开",
                    ARG_SHEET_NAME, ARG_CELL_POS, ARG_COL_PARENT);

    ErrorCode ERR_XPT_ROW_PARENT_CONTAINS_LOOP =
            define("nop.err.xpt.row-parent-contains-loop",
                    "表格[{sheetName}]的单元格[{cellPos}]的行父格[{rowParent}]不能包含循环指向，必须构成树状结构",
                    ARG_SHEET_NAME, ARG_CELL_POS, ARG_ROW_PARENT);

    ErrorCode ERR_XPT_COL_PARENT_CONTAINS_LOOP =
            define("nop.err.xpt.row-parent-contains-loop",
                    "表格[{sheetName}]的单元格[{cellPos}]的行父格[{colParent}]不能包含循环指向，必须构成树状结构",
                    ARG_SHEET_NAME, ARG_CELL_POS, ARG_COL_PARENT);

    ErrorCode ERR_XPT_MISSING_VAR_DS =
            define("nop.err.xpt.missing-var-ds",
                    "数据集[{dsName}]不存在", ARG_DS_NAME);

    ErrorCode ERR_XPT_MISSING_VAR_CELL =
            define("nop.err.xpt.missing-var-ds",
                    "未定义单元格变量cell");

    ErrorCode ERR_XPT_UNSUPPORTED_XPT_FILE_TYPE =
            define("nop.err.xpt.unsupported-xpt-file-type",
                    "不支持的报表文件后缀:{fileType}，允许的文件后缀名为:{allowedFileTypes}",
                    ARG_FILE_TYPE, ARG_ALLOWED_FILE_TYPES, ARG_PATH);

    ErrorCode ERR_XPT_UNSUPPORTED_RENDER_TYPE =
            define("nop.err.xpt.unsupported-render-type",
                    "不支持的报表输出类型：{renderType}", ARG_RENDER_TYPE);

    ErrorCode ERR_XPT_UNDEFINED_CELL_MODEL_PROP =
            define("nop.err.xpt.undefined-cell-model-prop",
                    "未定义的单元格模型的属性[{propName}]", ARG_PROP_NAME);

    ErrorCode ERR_XPT_UNDEFINED_IMAGE_MODEL_PROP =
            define("nop.err.xpt.undefined-image-model-prop",
                    "未定义的图片模型的属性[{propName}]", ARG_PROP_NAME);

    ErrorCode ERR_XPT_UNDEFINED_CHART_MODEL_PROP =
            define("nop.err.xpt.undefined-chart-model-prop",
                    "未定义的图表模型的属性[{propName}]", ARG_PROP_NAME);

    ErrorCode ERR_XPT_INVALID_DS_NAME =
            define("nop.err.xpt.invalid-ds-name",
                    "非法的数据源名称", ARG_DS_NAME);

    ErrorCode ERR_XPT_INVALID_FIELD_NAME =
            define("nop.err.xpt.invalid-field-name",
                    "非法的字段名", ARG_FIELD_NAME);

    ErrorCode ERR_XPT_INVALID_CELL_RANGE_EXPR =
            define("nop.err.xpt.invalid-cell-range-expr",
                    "非法的单元格区间表达式", ARG_CELL_POS);

    ErrorCode ERR_XPT_CELL_EXPR_RESULT_NOT_ONE_CELL =
            define("nop.err.xpt.cell-expr-result-not-one-cell",
                    "表达式[{expr}]返回单元格个数为[{size}]，不是单个单元格", ARG_EXPR);

    ErrorCode ERR_XPT_CELL_EXPR_NO_DS_NAME =
            define("nop.err.xpt.cell-expr-ds-name",
                    "单元格展开表达式中必须具有ds定义，格式为^dsName!fieldName: {expr}", ARG_EXPR);

    ErrorCode ERR_XPT_UNKNOWN_REPORT_MODEL =
            define("nop.err.xpt.unknown-report-model", "未知的报表模型：{reportName}", ARG_REPORT_NAME);


    ErrorCode ERR_XPT_INVALID_IMAGE_DATA =
            define("nop.err.xpt.invalid-image-data",
                    "图片数据不是字节数组或者IResource对象");

    ErrorCode ERR_XPT_NOT_SUPPORT_EXPR_IN_FORMULA =
            define("nop.err.xpt.not-support-expr-in-formula",
                    "Excel公式中不支持表达式:{expr}", ARG_EXPR);

    ErrorCode ERR_XPT_INVALID_EXCEL_FORMULA =
            define("nop.err.xpt.invalid-excel-formula",
                    "非法的Excel公式:{source}", ARG_SOURCE);
}
