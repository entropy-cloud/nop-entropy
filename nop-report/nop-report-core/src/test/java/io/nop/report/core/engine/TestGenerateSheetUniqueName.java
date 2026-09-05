/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.report.core.engine;

import io.nop.core.initialize.CoreInitialization;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.XptSheetModel;
import io.nop.report.core.model.ExpandedSheet;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * generateSheet对null的sheetNames去重表应保持健壮（ExcelRecordOutput以null调用）。
 */
public class TestGenerateSheetUniqueName {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testGenerateSheetWithNullSheetNamesMap() {
        ExcelWorkbook workbook = new ExcelWorkbook();
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("Data");
        sheet.setModel(new XptSheetModel());

        XptRuntime xptRt = ExpandedSheetGenerator.newXptRuntime(XLang.newEvalScope(), workbook);
        ExpandedSheetGenerator generator = new ExpandedSheetGenerator(workbook);

        ExpandedSheet expanded = generator.generateSheet(sheet, xptRt, null);
        assertNotNull(expanded);
        assertEquals("Data", expanded.getName());
    }
}
