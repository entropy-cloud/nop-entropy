package io.nop.report.core.expr;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
}
