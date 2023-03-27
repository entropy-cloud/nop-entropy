/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.engine;

import io.nop.core.context.IEvalContext;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.report.core.dataset.DynamicReportDataSet;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedRow;
import io.nop.report.core.model.ExpandedSheet;
import io.nop.report.core.model.ExpandedTable;

public interface IXptRuntime extends IEvalContext {
    ExpandedCell getCell();

    void setCell(ExpandedCell cell);

    ExpandedRow getRow();

    void setRow(ExpandedRow row);

    ExpandedTable getTable();

    ExpandedSheet getSheet();

    void setSheet(ExpandedSheet sheet);

    ExcelWorkbook getWorkbook();

    void setWorkbook(ExcelWorkbook workbook);

    Object evaluateCell(ExpandedCell cell);

    Object field(String field);

    DynamicReportDataSet ds(String dsName);

    DynamicReportDataSet makeDs(String dsName, Object value);
}