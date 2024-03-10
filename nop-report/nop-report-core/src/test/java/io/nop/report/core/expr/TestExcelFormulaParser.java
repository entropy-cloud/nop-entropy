/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.expr;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.XptCellModel;
import io.nop.report.core.engine.XptRuntime;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedRow;
import io.nop.report.core.model.ExpandedTable;
import io.nop.xlang.api.EvalCodeWithAst;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestExcelFormulaParser {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParse() {
        BaseTestCase.forceStackTrace();

        ExcelFormulaParser.parseFormula(null, "IF(A1<> \"2\",1,2)", XLang.newCompileTool());

        ExcelFormulaParser.parseFormula(null, "IF(A1<> \"2\",2)", XLang.newCompileTool());
    }

    @Test
    public void testOR() {

        EvalCodeWithAst code = ExcelFormulaParser.parseFormula(null, "IF(OR(D12=\"AA\",D12=\"BB\"),AVERAGE(G12),SUM(G12))", XLang.newCompileTool());
        String expr = code.getExpr().toExprString();
        System.out.println("expr=" + expr);
        assertEquals("IF(OR(D12?.value == \"AA\",D12?.value == \"BB\"),AVERAGE(G12),SUM(G12))", expr);

        XptRuntime xptRt = new XptRuntime(XLang.newEvalScope());
        ExpandedCell cell = new ExpandedCell();
        xptRt.setCell(cell);
        ExpandedTable table = new ExpandedTable(1, 2);
        table.makeRow(0).setFirstCell(cell);
        cell.setRow(table.makeRow(0));

        String expanded = new ReportFormulaGenerator(xptRt.getEvalScope()).toExprString(code.getExpr());
        System.out.println(expanded);
    }

    @Test
    public void testFilterExpr() {
        BaseTestCase.forceStackTrace();

        IEvalAction action = ExcelFormulaParser.parseFormula(null, "SUM(IF(\"e.value <= 2 \",A1:B4))", XLang.newCompileTool());

        XptRuntime xptRt = new XptRuntime(XLang.newEvalScope());
        ExpandedCell cell = new ExpandedCell();
        XptCellModel cellModel = new XptCellModel();
        cellModel.setName("A1");
        cell.setModel(cellModel);
        cell.setValue(1);
        xptRt.setCell(cell);

        ExpandedTable table = new ExpandedTable(2, 2);
        ExpandedRow row = table.makeRow(0);
        row.setFirstCell(cell);
        cell.setRow(row);
        table.addNamedCell(cell);

        Object result = action.invoke(xptRt);
        assertEquals(1, result);
    }
}
