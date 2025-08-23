/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ProcessResult;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.table.CellRange;
import io.nop.excel.model.ExcelClientAnchor;
import io.nop.excel.model.ExcelDataValidation;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.IExcelSheet;
import io.nop.excel.model.ILoopModel;
import io.nop.excel.model.XptCellModel;
import io.nop.excel.model.XptSheetModel;
import io.nop.excel.model.XptWorkbookModel;
import io.nop.ooxml.xlsx.output.IExcelSheetGenerator;
import io.nop.report.core.XptConstants;
import io.nop.report.core.engine.expand.TableExpander;
import io.nop.report.core.expr.ReportFormulaGenerator;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedCol;
import io.nop.report.core.model.ExpandedRow;
import io.nop.report.core.model.ExpandedSheet;
import io.nop.report.core.model.ExpandedTable;
import io.nop.xlang.api.EvalCodeWithAst;
import io.nop.xlang.ast.Expression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class ExpandedSheetGenerator implements IExcelSheetGenerator {
    public static final Logger LOG = LoggerFactory.getLogger(ExpandedSheetGenerator.class);

    private final ExcelWorkbook workbook;

    public ExpandedSheetGenerator(ExcelWorkbook workbook) {
        this.workbook = workbook;
    }

    public static XptRuntime newXptRuntime(IEvalContext ctx, ExcelWorkbook workbook) {
        XptRuntime xptRt = new XptRuntime(ctx.getEvalScope());
        xptRt.setWorkbook(workbook);

        xptRt.getEvalScope().setLocalValue(null, XptConstants.VAR_WORKBOOK_TPL, workbook);
        return xptRt;
    }

    @Override
    public void generate(IEvalContext ctx, BiConsumer<IExcelSheet, IEvalContext> consumer) {
        XptRuntime xptRt = newXptRuntime(ctx, workbook);

        XptWorkbookModel workbookModel = workbook.getModel();
        runLoop(workbook.getModel(), XptConstants.WORKBOOK_LOOP_VAR, XptConstants.WORKBOOK_LOOP_INDEX,
                xptRt, () -> {
                    try {
                        if (workbookModel != null)
                            runXpl(workbookModel.getBeforeExpand(), xptRt);

                        for (ExcelSheet sheet : workbook.getSheets()) {
                            generateSheetLoop(sheet, workbook, xptRt, consumer);
                        }

                        if (workbookModel != null)
                            runXpl(workbookModel.getAfterExpand(), xptRt);
                    } finally {
                        xptRt.runWorkbookCleanup();
                    }
                });
    }

    void generateSheetLoop(ExcelSheet sheet, ExcelWorkbook workbook, IXptRuntime xptRt, BiConsumer<IExcelSheet, IEvalContext> consumer) {
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

        try {
            runLoop(sheetModel, XptConstants.SHEET_LOOP_VAR, XptConstants.SHEET_LOOP_INDEX,
                    xptRt, () -> {
                        try {
                            if (sheetModel != null) {
                                runXpl(sheetModel.getBeforeExpand(), xptRt);
                            }

                            ExpandedSheet expandedSheet = generateSheet(sheet, xptRt, sheetNames);

                            if (sheetModel != null)
                                runXpl(sheetModel.getAfterExpand(), xptRt);

                            consumer.accept(expandedSheet, xptRt);
                        } finally {
                            xptRt.runSheetCleanup();
                        }
                    });
        } finally {
            xptRt.runSheetLoopCleanup();
        }
    }

    public ExpandedSheet generateSheet(ExcelSheet sheet, IXptRuntime xptRt,
                                       Map<String, Integer> sheetNames) {
        XptSheetModel sheetModel = sheet.getModel();
        Guard.notNull(sheetModel, "sheetModel");

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
        expandedSheet.setModel(sheetModel);
        expandedSheet.setName(sheetName);
        xptRt.setSheet(expandedSheet);

        new TableExpander(expandedSheet.getTable()).expand(xptRt);
        //ExpandedTableToNode.dump(expandedSheet);

        ExpandedSheetEvaluator.INSTANCE.evaluateSheetCells(expandedSheet, xptRt);

        removeHidden(expandedSheet);

        dropRemoved(expandedSheet);

        expandedSheet.getTable().assignRowIndexAndColIndex();
        ExpandedSheetEvaluator.INSTANCE.evaluateImages(expandedSheet, sheet.getImages(), xptRt);
        collectImages(expandedSheet);

        initDataValidations(sheet, expandedSheet, xptRt);
        initExportFormula(expandedSheet, xptRt);

        return expandedSheet;
    }

    private void removeHidden(ExpandedSheet sheet) {
        if (workbook.shouldRemoveHiddenCell()) {
            ExpandedTable table = sheet.getTable();
            for (int i = 0, n = table.getColCount(); i < n; i++) {
                ExpandedCol col = table.getCol(i);
                if (col.isHidden()) {
                    col.setRemoved(true);
                }
            }

            for (int i = 0, n = table.getRowCount(); i < n; i++) {
                ExpandedRow row = table.getRow(i);
                if (row.isHidden()) {
                    row.setRemoved(true);
                }
            }
        }
    }

    private void collectImages(ExpandedSheet sheet) {
        sheet.getTable().forEachRealCell((cell, rowIndex, colIndex) -> {
            ExpandedCell ec = (ExpandedCell) cell;
            if (ec.getImage() != null) {
                ExcelImage image = ec.getImage();
                ExcelClientAnchor anchor = image.makeAnchor();
                anchor.setRow1(rowIndex);
                anchor.setCol1(colIndex);
                image.calcSize(sheet);
                sheet.makeImages().add(image);
            }
            return ProcessResult.CONTINUE;
        });
    }

    private boolean dropRemoved(ExpandedSheet sheet) {
        boolean removed = false;
        ExpandedTable table = sheet.getTable();
        for (int i = 0, n = table.getRowCount(); i < n; i++) {
            ExpandedRow row = table.getRow(i);
            if (row.isRemoved()) {
                table.removeRow(i);
                i--;
                n--;
                removed = true;
            }
        }

        for (int i = 0, n = table.getColCount(); i < n; i++) {
            ExpandedCol col = table.getCol(i);
            if (col.isRemoved()) {
                table.removeCol(i);
                i--;
                n--;
                removed = true;
            }
        }
        return removed;
    }

    private void initDataValidations(ExcelSheet sheetTpl, ExpandedSheet sheet, IXptRuntime xptRt) {
        if (sheetTpl.getDataValidations() != null && !sheetTpl.getDataValidations().isEmpty()) {
            List<ExcelDataValidation> validations = new ArrayList<>(sheetTpl.getDataValidations().size());
            for (ExcelDataValidation validation : sheetTpl.getDataValidations()) {
                ExcelDataValidation copy = validation.cloneInstance();
                List<CellRange> ranges = copy.getRanges();
                if (ranges != null) {
                    ranges = xptRt.getExpandedCellRanges(ranges);
                    if (ranges != null && !ranges.isEmpty()) {
                        copy.setRanges(ranges);
                        validations.add(copy);
                    }
                }
            }
            if (!validations.isEmpty())
                sheet.setDataValidations(validations);
        }
    }

    private void initExportFormula(ExpandedSheet sheet, IXptRuntime xptRt) {
        if (!sheet.getModel().isUseExportFormula())
            return;

        IEvalScope scope = xptRt.getEvalScope();
        for (ExpandedRow row : sheet.getTable().getRows()) {
            row.forEachRealCell(cell -> {
                exportFormula(cell, scope, xptRt);
            });
        }
    }

    private void exportFormula(ExpandedCell cell, IEvalScope scope, IXptRuntime xptRt) {
        // 已经设置了表达式，则直接返回
        if (!StringHelper.isEmpty(cell.getFormula()))
            return;

        XptCellModel cellModel = cell.getModel();
        if (cellModel != null && Boolean.TRUE.equals(cellModel.getExportFormula())) {
            if (cellModel.getValueExpr() instanceof EvalCodeWithAst) {
                Expression expr = ((EvalCodeWithAst) cellModel.getValueExpr()).getAst();
                // 层次坐标需要根据当前单元格进行定位
                xptRt.setCell(cell);
                try {
                    String formula = new ReportFormulaGenerator(scope).toExprString(expr);
                    LOG.debug("nop.report.gen-excel-formula:cellName={},{}", cellModel.getName(), formula);
                    cell.setFormula(formula);
                } finally {
                    xptRt.setCell(null);
                }
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