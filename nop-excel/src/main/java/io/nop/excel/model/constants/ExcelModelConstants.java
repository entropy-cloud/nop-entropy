/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model.constants;

public interface ExcelModelConstants {

    // int MAX_ROW_INDEX = AppConfig.var("excel.max_row_index").toInt(500000);
    // int MAX_COL_INDEX = AppConfig.var("excel.max_col_index").toInt(10000);

    float DEFAULT_COLUMN_WIDTH = 54; // 54/72*96 = 72 px

    // 2010缺省为宋体 11号字，而2003为宋体 10号字
    float DEFAULT_ROW_HEIGHT = 13.5f; // 14.25/28.35 = 0.5cm, 13.5pt = 18px, 14.25pt = 19px

    String DOCUMENT_PROPERTIES_NAME = "DocumentProperties";

    String AUTHOR_NAME = "Author";
    String LAST_AUTHOR_NAME = "LastAuthor";
    String CREATED_NAME = "Created";
    String VERSION_NAME = "Version";
    String LAST_SAVED_NAME = "LastSaved";
    String COMPANY_NAME = "Company";

    String CUSTOM_DOCUMENT_PROPERTIES_NAME = "CustomDocumentProperties";

    String DT_DT = "dt:dt";

    String DT_STRING = "string";
    String DT_DATETIME = "dateTime.tz";
    String DT_FLOAT = "float";
    String DT_BOOLEAN = "boolean";
    String DT_DOUBLE = "double"; // ??

    String NAMES_NAME = "Names";

    String SS_NAME = "ss:Name";
    String SS_REFERS_TO = "ss:RefersTo";

    String STYLES_NAME = "Styles";

    String SS_ID = "ss:ID";

    String ALIGNMENT_NAME = "Alignment";
    String SS_VERTICAL = "ss:Vertical";
    String SS_HORIZONTAL = "ss:Horizontal";
    String SS_SHRINK_TO_FIT = "ss:ShrinkToFit";
    String SS_WRAP_TEXT = "ss:WrapText";
    String SS_ROTATE = "ss:Rotate";

    String BORDERS_NAME = "Borders";
    String SS_POSITION = "ss:Position";
    String SS_LINE_STYLE = "ss:LineStyle";
    String SS_WEIGHT = "ss:Weight";
    String SS_COLOR = "ss:Color";

    String BORDER_POSITION_LEFT = "Left";
    String BORDER_POSITION_RIGHT = "Right";
    String BORDER_POSITION_TOP = "Top";
    String BORDER_POSITION_BOTTOM = "Bottom";
    String BORDER_POSITION_DIAGONAL_LEFT = "DiagonalLeft";
    String BORDER_POSITION_DIAGONAL_RIGHT = "DiagonalRight";

    String FONT_NAME = "Font";
    String SS_FONT_NAME = "ss:FontName";
    String X_FAMILY = "x:Family";
    String X_CHARSET = "x:CharSet";
    String SS_SIZE = "ss:Size";
    String SS_BOLD = "ss:Bold";
    String SS_ITALIC = "ss:Italic";
    String SS_UNDERLINE = "ss:Underline";
    String SS_VERTICAL_ALIGN = "ss:VerticalAlign";

    String NUMBER_FORMAT_NAME = "NumberFormat";
    String SS_FORMAT = "ss:Format";

    String INTERIOR_NAME = "Interior";
    String SS_PATTERN = "ss:Pattern";

    String WORK_SHEET_NAME = "Worksheet";
    String TABLE_NAME = "Table";

    String WORKSHEET_OPTIONS_NAME = "WorksheetOptions";

    String X_WORKSHEET_OPTIONS_NAME = "x:WorksheetOptions";

    String PAGE_BREAKS_NAME = "PageBreaks";
    String ROW_BREAKS_NAME = "RowBreaks";
    String ROW_NAME = "Row";
    String COL_BREAKS_NAME = "ColBreaks";
    String COLUMN_NAME = "Column";

    String UNSYNCED_NAME = "Unsynced";
    String SELECTED_NAME = "Selected";
    String FIT_TO_PAGE_NAME = "FitToPage";
    String FREEZE_PANES_NAME = "FreezePanes";
    String PAGE_SETUP_NAME = "PageSetup";
    String PRINT_NAME = "Print";
    String SPLIT_HORIZONTAL_NAME = "SplitHorizontal";
    String SPLIT_VERTICAL_NAME = "SplitVertical";
    String PANES_NAME = "Panes";
    String PROTECT_OBJECTS_NAME = "ProtectObjects";
    String PROTECT_SCENARIOS_NAME = "ProtectScenarios";

    String NUMBER_NAME = "Number";
    String ACTIVE_ROW_NAME = "ActiveRow";
    String ACTIVE_COL_NAME = "ActiveCol";

    String LAYOUT_NAME = "Layout";
    String HEADER_NAME = "Header";
    String FOOTER_NAME = "Footer";
    String PAGE_MARGINS_NAME = "PageMargins";
    String X_ORIENTATION = "x:Orientation";
    String X_CENTER_HORIZONTAL = "x:CenterHorizontal";
    String X_CENTER_VERTICAL = "x:CenterVertical";
    String X_START_PAGE_NUMBER = "x:StartPageNumber";
    String X_MARGIN = "x:Margin";
    String X_DATA = "x:Data";
    String X_BOTTOM = "x:Bottom";
    String X_LEFT = "x:Left";
    String X_TOP = "x:Top";
    String X_RIGHT = "x:Right";

    String SS_DEFAULT_COLUMN_WIDTH = "ss:DefaultColumnWidth";
    String SS_DEFAULT_ROW_HEIGHT = "ss:DefaultRowHeight";

    String SS_STYLE_ID = "ss:StyleID";
    String SS_WIDTH = "ss:Width";
    String SS_AUTO_FIT_WIDTH = "ss:AutoFitWidth";
    String SS_SPAN = "ss:Span";
    String SS_HIDDEN = "ss:Hidden";
    String SS_INDEX = "ss:Index";

    String FIT_WIDTH_NAME = "FitWidth";
    String FIT_HEIGHT_NAME = "FitHeight";
    String COMMENTS_LAYOUT_NAME = "CommentsLayout";
    String VALID_PRINTER_INFO_NAME = "ValidPrinterInfo";
    String PAPER_SIZE_INDEX_NAME = "PaperSizeIndex";
    String SCALE_NAME = "Scale";
    String HORIZONTAL_RESOLUTION_NAME = "HorizontalResolution";
    String VERTICAL_RESOLUTION_NAME = "VerticalResolution";
    String GRID_LINES_NAME = "Gridlines";
    String ROW_COL_HEADINGS_NAME = "RowColHeadings";

    String SS_AUTO_FIT_HEIGHT = "ss:AutoFitHeight";
    String SS_HEIGHT = "ss:Height";
    String SS_FORMULA = "ss:Formula";
    String SS_HREF = "ss:HRef";
    String SS_MERGE_ACROSS = "ss:MergeAcross";
    String SS_MERGE_DOWN = "ss:MergeDown";
    String SS_DATA = "ss:Data";
    String DATA_NAME = "Data";
    String SS_TYPE = "ss:Type";

    String COMMENT_NAME = "Comment";

    /**
     * not underlined
     */

    byte U_NONE = 0;

    /**
     * single (normal) underline
     */

    byte U_SINGLE = 1;

    /**
     * double underlined
     */

    byte U_DOUBLE = 2;

    /**
     * accounting style single underline
     */

    byte U_SINGLE_ACCOUNTING = 0x21;

    /**
     * accounting style double underline
     */

    byte U_DOUBLE_ACCOUNTING = 0x22;

}
