/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.engine;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.IExcelSheet;
import io.nop.excel.model.ILoopModel;
import io.nop.excel.model.XptRowModel;
import io.nop.excel.model.XptSheetModel;
import io.nop.excel.model.XptWorkbookModel;
import io.nop.ooxml.xlsx.output.IExcelSheetGenerator;
import io.nop.report.core.XptConstants;
import io.nop.report.core.engine.expand.TableExpander;
import io.nop.report.core.model.ExpandedCol;
import io.nop.report.core.model.ExpandedRow;
import io.nop.report.core.model.ExpandedSheet;
import io.nop.report.core.model.ExpandedTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

public class ReportSheetGenerator implements IExcelSheetGenerator {
    public static final Logger LOG = LoggerFactory.getLogger(ReportSheetGenerator.class);

    private final ExcelWorkbook workbook;

    public ReportSheetGenerator(ExcelWorkbook workbook) {
        this.workbook = workbook;
    }

    @Override
    public void generate(IEvalContext ctx, Consumer<IExcelSheet> consumer) {
        XptRuntime xptRt = new XptRuntime(ctx.getEvalScope());
        xptRt.setWorkbook(workbook);

        xptRt.getEvalScope().setLocalValue(null, XptConstants.VAR_WORKBOOK_TPL, workbook);

        XptWorkbookModel workbookModel = workbook.getModel();
        runLoop(workbook.getModel(), XptConstants.WORKBOOK_LOOP_VAR, XptConstants.WORKBOOK_LOOP_INDEX,
                xptRt, () -> {
                    if (workbookModel != null)
                        runXpl(workbookModel.getBeforeExpand(), xptRt);

                    for (ExcelSheet sheet : workbook.getSheets()) {
                        generateSheetLoop(sheet, workbook, xptRt, consumer);
                    }

                    if (workbookModel != null)
                        runXpl(workbookModel.getAfterExpand(), xptRt);
                });
    }

    void generateSheetLoop(ExcelSheet sheet, ExcelWorkbook workbook, IXptRuntime xptRt, Consumer<IExcelSheet> consumer) {
        XptSheetModel sheetModel = sheet.getModel();
        xptRt.getEvalScope().setLocalValue(null, XptConstants.VAR_SHEET_TPL, sheet);

        if (sheetModel != null) {
            if (!passConditions(sheetModel.getTestExpr(), xptRt)) {
                LOG.info("nop.report.ignore-sheet-when-test-return-false:sheetName={},path={}", sheet.getName(), sheet.resourcePath());
                return;
            }
        }

        // 用于避免生成重复的sheet名称
        Map<String, Integer> sheetNames = new HashMap<>();

        runLoop(sheetModel, XptConstants.SHEET_LOOP_VAR, XptConstants.SHEET_LOOP_INDEX,
                xptRt, () -> {
                    if (sheetModel != null) {
                        runXpl(sheetModel.getBeforeExpand(), xptRt);
                    }

                    generateSheet(sheet, xptRt, consumer, sheetNames);

                    if (sheetModel != null)
                        runXpl(sheetModel.getAfterExpand(), xptRt);

                });
    }

    private void generateSheet(ExcelSheet sheet, IXptRuntime xptRt, Consumer<IExcelSheet> consumer,
                               Map<String, Integer> sheetNames) {
        XptSheetModel sheetModel = sheet.getModel();
        String sheetName = sheet.getName();
        xptRt.getEvalScope().setLocalValue(null, XptConstants.VAR_SHEET_NAME, sheetName);

        if (sheetModel != null && sheetModel.getSheetNameExpr() != null) {
            sheetName = ConvertHelper.toString(sheetModel.getSheetNameExpr().invoke(xptRt));
            sheetName = StringHelper.strip(sheetName);
            if (sheetName == null)
                sheetName = sheet.getName();
        }
        sheetName = uniqueName(sheetName, sheetNames);

        xptRt.getEvalScope().setLocalValue(null, XptConstants.VAR_SHEET_NAME, sheetName);

        ExpandedSheet expandedSheet = new ExpandedSheet(sheet);
        expandedSheet.setName(sheetName);
        xptRt.setSheet(expandedSheet);

        new TableExpander(expandedSheet.getTable()).expand(xptRt);

        evaluateSheet(expandedSheet, xptRt);

        dropRemoved(expandedSheet);

        consumer.accept(expandedSheet);
    }

    private void evaluateSheet(ExpandedSheet expandedSheet, IXptRuntime xptRt) {
        for (ExpandedRow row : expandedSheet.getTable().getRows()) {
            xptRt.setRow(row);
            XptRowModel rowModel = row.getModel();

            row.forEachRealCell(cell -> {
                xptRt.evaluateCell(cell);
            });

            if (rowModel != null) {
                xptRt.setRow(row);
                Boolean visible = ConvertHelper.toBoolean(runXpl(rowModel.getVisibleExpr(), xptRt));
                if (visible != null) {
                    row.setHidden(!visible);
                }

                String styleId = ConvertHelper.toString(runXpl(rowModel.getStyleIdExpr(), xptRt));
                row.setStyleId(styleId);
            }
        }
    }

    private void dropRemoved(ExpandedSheet sheet) {
        ExpandedTable table = sheet.getTable();
        for (int i = 0, n = table.getRowCount(); i < n; i++) {
            ExpandedRow row = table.getRow(i);
            if (row.isRemoved()) {
                table.removeRow(i);
                i--;
                n--;
            }
        }

        for (int i = 0, n = table.getColCount(); i < n; i++) {
            ExpandedCol col = table.getCol(i);
            if (col.isRemoved()) {
                table.removeCol(i);
                i--;
                n--;
            }
        }
    }

    private String uniqueName(String sheetName, Map<String, Integer> sheetNames) {
        int nextIndex = 1;
        do {
            Integer index = sheetNames.putIfAbsent(sheetName, nextIndex);
            if (index == null)
                return sheetName;

            sheetName = getBaseSheetName(sheetName) + "(" + index + ")";
            nextIndex = index + 1;
        } while (true);
    }

    private String getBaseSheetName(String sheetName) {
        if (sheetName.endsWith(")")) {
            int pos = sheetName.lastIndexOf('(');
            if (pos < 0)
                return sheetName;
            String str = sheetName.substring(pos + 1, sheetName.length() - 1);
            if (StringHelper.isAllDigit(str))
                return sheetName.substring(0, pos);
            return sheetName;
        } else {
            return sheetName;
        }
    }

    private void runLoop(ILoopModel model, String defaultLoopVarName, String defaultLoopIndexName,
                         IXptRuntime xptRt, Runnable task) {

        Iterator<Object> loopIt = beginLoop(model, xptRt);
        if (loopIt == null) {
            task.run();
        } else {
            IEvalScope scope = xptRt.getEvalScope();

            int loopIndex = 0;
            while (loopIt.hasNext()) {
                Object loopVar = loopIt.next();
                String loopVarName = model.getLoopVarName();
                if (loopVarName == null)
                    loopVarName = defaultLoopVarName;

                String loopIndexName = model.getLoopIndexName();
                if (loopIndexName == null)
                    loopIndexName = defaultLoopIndexName;

                scope.setLocalValue(null, loopVarName, loopVar);
                scope.setLocalValue(null, loopIndexName, loopIndex);

                task.run();
                loopIndex++;
            }

            runXpl(model.getEndLoop(), xptRt);
        }
    }

    private Iterator<Object> beginLoop(ILoopModel model, IXptRuntime xptRt) {
        if (model == null || model.getBeginLoop() == null && model.getLoopItemsName() == null)
            return null;

        Object c = runXpl(model.getBeginLoop(), xptRt);
        if (model.getLoopItemsName() != null) {
            c = xptRt.getEvalScope().getValueByPropPath(model.getLoopItemsName());
        }
        return CollectionHelper.toIterator(c, false);
    }

    private Object runXpl(IEvalAction action, IXptRuntime xptRt) {
        if (action == null)
            return null;
        return action.invoke(xptRt);
    }

    private boolean passConditions(IEvalPredicate predicate, IXptRuntime xptRt) {
        if (predicate == null)
            return true;
        return predicate.passConditions(xptRt);
    }
}