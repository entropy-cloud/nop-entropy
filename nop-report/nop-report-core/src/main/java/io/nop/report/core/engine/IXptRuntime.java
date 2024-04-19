/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.report.core.XptConstants;
import io.nop.report.core.dataset.DynamicReportDataSet;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedCellSet;
import io.nop.report.core.model.ExpandedRow;
import io.nop.report.core.model.ExpandedSheet;
import io.nop.report.core.model.ExpandedTable;

public interface IXptRuntime extends IEvalContext {
    static IXptRuntime fromScope(IEvalScope scope) {
        return (IXptRuntime) scope.getValue(XptConstants.VAR_XPT_RT);
    }

    ExpandedCell getCell();

    void setCell(ExpandedCell cell);

    ExpandedRow getRow();

    void setRow(ExpandedRow row);

    ExcelImage getImage();

    void setImage(ExcelImage image);

    ExpandedTable getTable();

    ExpandedSheet getSheet();

    void setSheet(ExpandedSheet sheet);

    ExcelWorkbook getWorkbook();

    void setWorkbook(ExcelWorkbook workbook);

    Object evaluateCell(ExpandedCell cell);

    Object field(String field);

    ExpandedCellSet getNamedCellSet(String cellName);

    ExpandedCell getNamedCell(String cellName);

    DynamicReportDataSet ds(String dsName);

    DynamicReportDataSet makeDs(String dsName, Object value);

    /**
     * 解析层次坐标返回对应的单元格集合。这个函数仅作为示例使用，用于演示不使用支持层次坐标的表达式引擎时如何处理
     */
    ExpandedCellSet cells(String cellExpr);

    /**
     * 递增计数器并返回递增前的值
     *
     * @param name 变量名
     * @return 递增前的值
     */
    int incAndGet(String name);

    ExcelImage makeImage();

    void addSheetCleanup(Runnable cleanup);

    void addWorkbookCleanup(Runnable cleanup);

    void addSheetLoopCleanup(Runnable cleanup);

    void runSheetCleanup();

    void runWorkbookCleanup();

    void runSheetLoopCleanup();
}