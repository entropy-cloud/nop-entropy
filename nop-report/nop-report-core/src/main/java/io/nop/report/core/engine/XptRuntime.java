/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.IVariableScope;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.MapCache;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.utils.Underscore;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.report.core.XptConstants;
import io.nop.report.core.dataset.DynamicReportDataSet;
import io.nop.report.core.expr.ExcelFormulaParser;
import io.nop.report.core.expr.ReportExpressionParser;
import io.nop.report.core.expr.ReportFormulaGenerator;
import io.nop.report.core.functions.ReportFunctionProvider;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedCellSet;
import io.nop.report.core.model.ExpandedRow;
import io.nop.report.core.model.ExpandedSheet;
import io.nop.report.core.model.ExpandedTable;
import io.nop.xlang.api.EvalCodeWithAst;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.Expression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.api.core.ApiErrors.ARG_NAME;

public class XptRuntime implements IXptRuntime, IVariableScope {
    static final Logger LOG = LoggerFactory.getLogger(XptRuntime.class);

    private final IEvalScope scope;

    private final EvalRuntime evalRt;
    private ExpandedCell cell;
    private ExpandedTable table;
    private ExpandedSheet sheet;
    private ExpandedRow row;
    private ExcelWorkbook workbook;

    private ExcelImage image;

    private List<Runnable> sheetCleanups = null;
    private List<Runnable> workbookCleanups = null;

    private List<Runnable> sheetLoopCleanups = null;

    private ICache<Object, Object> cache;

    private final Map<String, IExecutableExpression> cellExprCache = new HashMap<>();
    private final Map<String, EvalCodeWithAst> formulaCompileCache = new HashMap<>();

    public XptRuntime(IEvalScope scope) {
        this.scope = scope.newChildScope();
        scope.setLocalValue(null, XptConstants.VAR_XPT_RT, this);
        scope.setExtension(this);
        this.evalRt = new EvalRuntime(scope);
    }

    public ExcelWorkbook getWorkbook() {
        return workbook;
    }

    public void setWorkbook(ExcelWorkbook workbook) {
        this.workbook = workbook;
    }

    @Override
    public synchronized ICache<Object, Object> getCache() {
        if (cache != null)
            return cache;
        IServiceContext svcCtx = IServiceContext.fromEvalContext(scope);
        if (svcCtx != null) {
            cache = svcCtx.getCache();
        } else {
            cache = new MapCache<>("xpt-runtime-cache", false);
        }
        return cache;
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    @Override
    public ExpandedCell getCell() {
        return cell;
    }

    @Override
    public void setCell(ExpandedCell cell) {
        this.cell = cell;
    }

    public ExcelImage getImage() {
        return image;
    }

    public void setImage(ExcelImage image) {
        this.image = image;
    }

    @Override
    public ExpandedTable getTable() {
        return table;
    }

    @Override
    public ExpandedSheet getSheet() {
        return sheet;
    }

    @Override
    public void setSheet(ExpandedSheet sheet) {
        this.sheet = sheet;
        this.table = sheet == null ? null : sheet.getTable();
    }

    @Override
    public boolean containsValue(String name) {
        return getValue(name) != null;
    }

    @Override
    public Object getValue(String name) {
        if (XptConstants.VAR_CELL.equals(name))
            return cell;
        if (XptConstants.VAR_TABLE.equals(name))
            return table;
        if (XptConstants.VAR_SHEET.equals(name))
            return sheet;
        if (XptConstants.VAR_ROW.equals(name))
            return row;

        if (XptConstants.VAR_IMAGE.equals(name))
            return image;

        if (XptConstants.VAR_WORKBOOK.equals(name))
            return workbook;
        // 这里只判断扩展属性名，因此对于不识别的属性直接返回null
        return null;
    }

    @Override
    public Object getValueByPropPath(String propPath) {
        return getValue(propPath);
    }

    @Override
    public ExpandedRow getRow() {
        return row;
    }

    @Override
    public void setRow(ExpandedRow row) {
        this.row = row;
    }

    @Override
    public Object evaluateCell(ExpandedCell cell) {
        return ExpandedSheetEvaluator.INSTANCE.evaluateCell(cell, this);
    }

    public Object field(String field) {
        if (cell == null)
            return scope.getValueByPropPath(field);

        List<Object> items = DynamicReportDataSet.getDsItems(cell, null);
        if (items != null) {
            Object item = CollectionHelper.first(items);
            return Underscore.getFieldValue(item, field);
        }

        if (cell.getRowParent() != null) {
            return cell.getRowParent().getExpandField(field);
        }

        if (cell.getColParent() != null) {
            return cell.getColParent().getExpandField(field);
        }

        if (sheet.getModel().getSheetVarName() != null) {
            Object value = scope.getValueByPropPath(sheet.getModel().getSheetVarName());
            if (value == null)
                return null;
            return Underscore.getFieldValue(value, field);
        }

        return scope.getValueByPropPath(field);
    }

    @Override
    public DynamicReportDataSet ds(String dsName) {
        return DynamicReportDataSet.makeDataSet(this, dsName);
    }

    @Override
    public DynamicReportDataSet makeDs(String dsName, Object value) {
        return DynamicReportDataSet.makeDataSetFromValue(dsName, value, this);
    }

    @Override
    public ExpandedCellSet getNamedCellSet(String cellName) {
        ExpandedTable table = getTable();
        if (table == null)
            return new ExpandedCellSet(null, cellName, Collections.emptyList());

        List<ExpandedCell> cells = table.getNamedCells(cellName);
        return new ExpandedCellSet(null, cellName, cells).evaluateAll(this);
    }

    @Override
    public ExpandedCell getNamedCell(String cellName) {
        ExpandedTable table = getTable();
        if (table == null)
            return null;

        ExpandedCell cell = table.getNamedCell(cellName);
        if (cell != null)
            evaluateCell(cell);
        return cell;
    }

    @Override
    public int incAndGet(String name, int delta) {
        int value = ConvertHelper.toPrimitiveInt(scope.getValue(name),
                err -> new NopException(err).param(ARG_NAME, name));
        int ret = value + delta;
        scope.setLocalValue(name, ret);
        return ret;
    }

    @Override
    public int getAndInc(String name, int delta) {
        int value = ConvertHelper.toPrimitiveInt(scope.getValue(name),
                err -> new NopException(err).param(ARG_NAME, name));
        int ret = value;
        scope.setLocalValue(name, ret + delta);
        return ret;
    }

    @Override
    public long seq(String name) {
        long value = ConvertHelper.toPrimitiveLong(scope.getValue(name), 1,
                err -> new NopException(err).param(ARG_NAME, name));
        long ret = value;
        scope.setLocalValue(name, value + 1);
        return ret;
    }

    @Override
    public ExpandedCellSet cells(String cellExpr) {
        IExecutableExpression expr = cellExprCache.computeIfAbsent(cellExpr, k -> {
            return new ReportExpressionParser().parseCellExpr(TextScanner.fromString(null, cellExpr)).getExecutable();
        });
        return (ExpandedCellSet) XLang.execute(expr, evalRt);
    }

    @Override
    public ExcelImage makeImage() {
        Guard.notNull(cell, "cell");
        return cell.makeImage();
    }

    @Override
    public void addSheetCleanup(Runnable cleanup) {
        if (cleanup == null)
            return;
        if (sheetCleanups == null)
            sheetCleanups = new ArrayList<>();
        sheetCleanups.add(cleanup);
    }

    @Override
    public void addWorkbookCleanup(Runnable cleanup) {
        if (cleanup == null)
            return;
        if (workbookCleanups == null)
            workbookCleanups = new ArrayList<>();
        workbookCleanups.add(cleanup);
    }

    @Override
    public void addSheetLoopCleanup(Runnable cleanup) {
        if (cleanup == null)
            return;
        if (sheetLoopCleanups == null)
            sheetLoopCleanups = new ArrayList<>();
        sheetLoopCleanups.add(cleanup);
    }

    @Override
    public void runSheetCleanup() {
        if (sheetCleanups != null) {
            sheetCleanups.forEach(this::runCleanup);
            sheetCleanups.clear();
        }
    }

    private void runCleanup(Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Exception e) {
            LOG.error("nop.err.xpt.run-cleanup-error", e);
        }
    }

    @Override
    public void runWorkbookCleanup() {
        if (workbookCleanups != null) {
            workbookCleanups.forEach(this::runCleanup);
            workbookCleanups.clear();
        }
    }

    @Override
    public void runSheetLoopCleanup() {
        if (sheetLoopCleanups != null) {
            sheetLoopCleanups.forEach(this::runCleanup);
            sheetLoopCleanups.clear();
        }
    }

    @Override
    public Object evalExcelFormula(String formula) {
        if (StringHelper.isBlank(formula))
            return null;
        return getCompiledExcelFormula(formula).invoke(this);
    }


    @Override
    public String expandExcelFormula(String formula) {
        if (StringHelper.isBlank(formula))
            return null;
        Expression expr = getCompiledExcelFormula(formula).getAst();
        return new ReportFormulaGenerator(this.getEvalScope()).toExprString(expr);
    }

    protected EvalCodeWithAst compileExcelFormula(String formula) {
        Expression expr = new ExcelFormulaParser().parseExpr(null, formula);
        XLangCompileTool cp = XLang.newCompileTool();
        cp.getScope().setFunctionProvider(ReportFunctionProvider.INSTANCE);
        // 语义分析过程中可能会改expr
        EvalCodeWithAst ret = new EvalCodeWithAst(cp.buildExecutable(expr.deepClone()), formula, expr);
        return ret;
    }

    protected EvalCodeWithAst getCompiledExcelFormula(String formula) {
        return formulaCompileCache.computeIfAbsent(formula, this::compileExcelFormula);
    }

}