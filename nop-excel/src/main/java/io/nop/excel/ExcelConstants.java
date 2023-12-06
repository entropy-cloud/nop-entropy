/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel;

public interface ExcelConstants {
    String VAR_WORKBOOK = "workbook";
    String VAR_SHEET = "sheet";
    String VAR_CELL = "cell";

    String VAR_LABEL_DATA = "labelData";
    String VAR_LABEL_CELL = "labelCell";
    String VAR_ROW = "row";
    String VAR_TABLE = "table";
    String VAR_COL = "col";
    String VAR_RECORD = "record";
    String VAR_VALUE = "value";
    String VAR_ROOT_RECORD = "rootRecord";

    String VAR_ROOT_MODEL = "rootModel";

    String VAR_RECORD_PARENTS = "recordParents";

    String VAR_SHEET_NAME = "sheetName";

    String VAR_FIELD_LABEL = "fieldLabel";

    /**
     * By default, Microsoft Office Excel 2007 uses the Calibry font in font size 11
     */
    String DEFAULT_FONT_NAME = "Calibri";
    /**
     * By default, Microsoft Office Excel 2007 uses the Calibry font in font size 11
     */
    short DEFAULT_FONT_SIZE = 11;

    String DEFAULT_FONT_COLOR = "0x0";

    String FILE_TYPE_IMP_XML = "imp.xml";

    String XDSL_SCHEMA_IMP = "/nop/schema/excel/imp.xdef";

    double DEFAULT_COL_WIDTH = 8.5;

    String DISPLAY_MODE_TABLE = "table";
}
