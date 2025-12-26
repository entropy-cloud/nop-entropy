/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface XlsxErrors {
    String ARG_TYPE = "type";
    String ARG_REL_ID = "relId";
    String ARG_SHEET_NAME = "sheetName";
    String ARG_CHART_ID = "chartId";
    String ARG_PART_NAME = "partName";
    String ARG_COLOR_NAME = "colorName";
    String ARG_ELEMENT_NAME = "elementName";
    String ARG_PARSER_NAME = "parserName";

    ErrorCode ERR_XLSX_NULL_REL_PART = define("nop.err.xlsx.null-rel-part", "没有关联文件:type={type},relId={relId}", ARG_TYPE,
            ARG_REL_ID);

    ErrorCode ERR_XLSX_UNKNOWN_SHEET_NAME = define("nop.err.xlsx.unknown-sheet-name",
            "找不到sheet名称为:{sheetName}", ARG_SHEET_NAME);

    ErrorCode ERR_XLSX_CHART_PARSE_FAIL = define("nop.err.xlsx.chart-parse-fail",
            "解析图表失败:chartId={chartId},partName={partName}", ARG_CHART_ID, ARG_PART_NAME);

    ErrorCode ERR_XLSX_NULL_PACKAGE = define("nop.err.xlsx.null-package",
            "ExcelOfficePackage不能为null");

    // 图表样式相关错误码
    ErrorCode ERR_XLSX_CHART_STYLE_PARSE_FAIL = define("nop.err.xlsx.chart-style-parse-fail",
            "解析图表样式失败:parserName={parserName},elementName={elementName}", ARG_PARSER_NAME, ARG_ELEMENT_NAME);

    ErrorCode ERR_XLSX_CHART_COLOR_RESOLVE_FAIL = define("nop.err.xlsx.chart-color-resolve-fail",
            "解析图表颜色失败:colorName={colorName}", ARG_COLOR_NAME);

    ErrorCode ERR_XLSX_CHART_THEME_LOAD_FAIL = define("nop.err.xlsx.chart-theme-load-fail",
            "加载图表主题失败:partName={partName}", ARG_PART_NAME);
}
