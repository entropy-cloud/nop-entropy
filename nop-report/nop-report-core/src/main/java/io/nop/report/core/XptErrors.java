/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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

    ErrorCode ERR_XPT_INVALID_DS_NAME =
            define("nop.err.xpt.invalid-ds-name",
                    "非法的数据源名称", ARG_DS_NAME);

    ErrorCode ERR_XPT_INVALID_FIELD_NAME =
            define("nop.err.xpt.invalid-field-name",
                    "非法的字段名", ARG_FIELD_NAME);
}
