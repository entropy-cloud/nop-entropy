/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core;

import java.util.Arrays;
import java.util.List;

public interface XptConstants {
    String VAR_CELL = "cell";
    String VAR_ROW = "row";
    String VAR_SHEET = "sheet";
    String VAR_TABLE = "table";

    String VAR_XPT_RT = "xptRt";

    String VAR_IMAGE = "image";

    String VAR_ENTITY = "entity";
    String VAR_SHEET_TPL = "sheetTpl";
    String VAR_WORKBOOK_TPL = "workbookTpl";
    String VAR_SHEET_NAME = "sheetName";

    String EXCEL_MODEL_FIELD_PREFIX = "*=";

    String WORKBOOK_LOOP_VAR = "workbookLoopVar";
    String SHEET_LOOP_VAR = "sheetLoopVar";
    String WORKBOOK_LOOP_INDEX = "workbookLoopIndex";
    String SHEET_LOOP_INDEX = "sheetLoopIndex";

    String SHEET_NAME_XPT_WORKBOOK_MODEL = "XptWorkbookModel";
    String SHEET_NAME_XPT_SHEET_MODEL = "XptSheetModel";
    String POSTFIX_XPT_SHEET_MODEL = "-XptSheetModel";

    String PROP_NAMED_STYLES = "ext:namedStyles";
    String PROP_STYLE = "style";
    String PROP_ID = "id";
    String PROP_MODEL = "model";

    String XPT_IMP_MODEL_PATH = "/nop/report/imp/xpt.imp.xml";
    String XDSL_SCHEMA_WORKBOOK = "/nop/schema/excel/workbook.xdef";

    String XDEF_NODE_EXCEL_CELL = "ExcelCell";

    String XDEF_NODE_EXCEL_IMAGE = "ExcelImage";

    String STD_DOMAIN_REPORT_EXPR = "report-expr";

    String MODEL_TYPE_XPT = "xpt";
    String FILE_TYPE_XPT_XML = "xpt.xml";
    String FILE_TYPE_XPT_XLSX = "xpt.xlsx";

    List<String> ALLOWED_XPT_FILE_TYPES = Arrays.asList(FILE_TYPE_XPT_XML, FILE_TYPE_XPT_XLSX);

    String RENDER_TYPE_HTML = "html";
    String RENDER_TYPE_WORD = "word";
    String RENDER_TYPE_XLSX = "xlsx";
    String RENDER_TYPE_PDF = "pdf";

    String CSS_PREFIX_XPT = "xpt-";
    String CSS_PREFIX_SCOPED = "xpt-s-";

    String VAR_SCOPED_CSS_PREFIX = "scopedCssPrefix";

    double DEFAULT_WIDTH = 8.5;


    String KEY_SUM = "SUM";
    String KEY_ALL_SUM = "ALL_SUM";

    String KEY_RANK = "RANK";

    String KEY_ACCSUM = "ACCSUM";

    String EXT_PROP_XPT_FORMAT_EXPR = "xpt:formatExpr";
    String EXT_PROP_XPT_BEFORE_EXPAND = "xpt:beforeExpand";

    String EXT_PROP_XPT_VALUE_EXPR = "xpt:valueExpr";

    String EXT_PROP_XPT_LABEL_EXPAND_EXPR = "xpt:labelExpandExpr";
    String EXT_PROP_XPT_LABEL_VALUE_EXPR = "xpt:labelValueExpr";

    String EXT_PROP_XPT_LABEL_STYLE_ID_EXPR = "xpt:labelStyleIdExpr";

    String EXT_PROP_XPT_STYLE_ID_EXPR = "xpt:styleIdExpr";
}