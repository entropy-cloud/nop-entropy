/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.report.core.expr;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.report.core.engine.XptRuntime;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Excel公式 AND/OR 关键字语义回归测试。
 */
public class TestExcelFormulaLogicKeyword {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private boolean eval(String formula) {
        IEvalAction action = ExcelFormulaParser.parseFormula(null, formula, XLang.newCompileTool());
        Object result = action.invoke(new XptRuntime(XLang.newEvalScope()));
        return ConvertHelper.toTruthy(result);
    }

    @Test
    public void testAndKeyword() {
        // AND语义：true AND false = false。曾被解析为OR导致恒真
        assertFalse(eval("(1 > 0) AND (1 > 2)"));
    }

    @Test
    public void testOrKeyword() {
        // OR语义：false OR true = true。曾被解析为AND导致恒假
        assertTrue(eval("(1 > 2) OR (1 > 0)"));
    }

    @Test
    public void testAndKeywordBothTrue() {
        assertTrue(eval("(1 > 0) AND (2 > 0)"));
    }
}
