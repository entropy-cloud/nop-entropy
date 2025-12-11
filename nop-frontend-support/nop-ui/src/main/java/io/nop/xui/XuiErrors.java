/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xui;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface XuiErrors {
    String ARG_FORM_ID = "formId";
    String ARG_CELL_ID = "cellId";
    String ARG_GRID_ID = "gridId";
    String ARG_COL_ID = "colId";
    String ARG_PROP_NAME = "propName";

    String ARG_RELATION_NAME = "relationName";

    String ARG_VIEW_PATH = "viewPath";
    String ARG_PAGE_ID = "pageId";

    ErrorCode ERR_XUI_FORM_UNKNOWN_PROP = define("nop.err.xui.form.unknown-prop", "表单[{formId}]引用了未定义的对象属性[{propName}]",
            ARG_FORM_ID, ARG_PROP_NAME);

    ErrorCode ERR_XUI_FORM_CELL_UNKNOWN_DEPEND = define("nop.err.xui.form.cell.unknown-depend",
            "表单[{formId}]的字段[{cellId}]依赖了未定义的对象属性[{propName}]", ARG_FORM_ID, ARG_CELL_ID, ARG_PROP_NAME);

    ErrorCode ERR_XUI_GRID_COL_UNKNOWN_DEPEND = define("nop.err.xui.grid.col-unknown-depend",
            "表格[{gridId}]的字段[{colId}]依赖了未定义的对象属性[{propName}]", ARG_GRID_ID, ARG_COL_ID, ARG_PROP_NAME);

    ErrorCode ERR_XUI_UNKNOWN_FORM = define("nop.err.xui.unknown-form", "未定义的表单:{formId}", ARG_FORM_ID);

    ErrorCode ERR_XUI_UNKNOWN_GRID = define("nop.xui.unknown-grid", "未定义的表格:{gridId}", ARG_GRID_ID);

    ErrorCode ERR_XUI_UNKNOWN_PAGE = define("nop.xui.unknown-page", "未定义的页面:{pageId}", ARG_PAGE_ID);

    ErrorCode ERR_XUI_INVALID_EXT_RELATION = define("nop.err.xui.invalid-relation", "属性[{propName}]的ext:relation关联设置错误",
            ARG_PROP_NAME);

    ErrorCode ERR_FORM_CELL_NOT_PROP = define("nop.err.xui.form.cell-not-prop", "表单[{formId}]的字段[{cellId}]不是已定义的实体属性",
            ARG_FORM_ID, ARG_CELL_ID);

    ErrorCode ERR_GRID_COL_NOT_PROP = define("nop.err.xui.grid.col-not-prop", "表格[{gridId}]的列[{colId}]不是已定义的实体属性",
            ARG_GRID_ID, ARG_COL_ID);

    ErrorCode ERR_XUI_REF_VIEW_MUST_HAS_PAGE_OR_GRID_OR_FORM_ATTR =
            define("nop.err.xui.ref-view-must-has-page-or-grid-or-form-attr",
                    "view配置必须指定page、form或者grid属性之一", ARG_VIEW_PATH);

    ErrorCode ERR_XUI_REF_VIEW_PAGE_GRID_FORM_ONLY_ALLOW_ONE_NON_EMPTY =
            define("nop.err.xui.ref-view-page-grid-form-only-allow-one-non-empty",
                    "view配置的page、form、grid属性只允许有一个非空，不能设置多个");

    ErrorCode ERR_XUI_REF_VIEW_NOT_EXISTS =
            define("nop.err.xui.ref-view-not-exists", "view配置不存在：{viewPath}",
                    ARG_VIEW_PATH);

    ErrorCode ERR_XUI_UNKNOWN_JSON_COMPONENT_PROP =
            define("nop.err.xui.unknown-json-component-prop", "没有找到对应的json组件属性:{propName}", ARG_PROP_NAME);
}
