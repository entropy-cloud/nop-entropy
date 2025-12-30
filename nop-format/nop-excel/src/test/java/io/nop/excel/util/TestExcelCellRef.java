/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.util;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.CoreErrors;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.nop.excel.ExcelErrors.ERR_EXCEL_INVALID_CELL_REF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestExcelCellRef {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParseNull() {
        assertNull(ExcelCellRef.parse(null));
        assertNull(ExcelCellRef.parse(""));
    }

    @Test
    public void testParseSingleCell() {
        ExcelCellRef ref = ExcelCellRef.parse("A1");
        assertNotNull(ref);
        assertNull(ref.getSheetName());
        assertEquals("A1", ref.getCellRange().toABString());
        assertTrue(ref.getCellRange().isSingleCell());
    }

    @Test
    public void testParseSingleCellWithAbsolute() {
        ExcelCellRef ref = ExcelCellRef.parse("$A$1");
        assertNotNull(ref);
        assertNull(ref.getSheetName());
        assertEquals("$A$1", ref.getCellRange().toABString(true, true));
        assertTrue(ref.getCellRange().isSingleCell());
    }

    @Test
    public void testParseCellRange() {
        ExcelCellRef ref = ExcelCellRef.parse("A1:C3");
        assertNotNull(ref);
        assertNull(ref.getSheetName());
        assertEquals("A1:C3", ref.getCellRange().toABString());
        assertFalse(ref.getCellRange().isSingleCell());
        assertEquals(0, ref.getCellRange().getFirstRowIndex());
        assertEquals(0, ref.getCellRange().getFirstColIndex());
        assertEquals(2, ref.getCellRange().getLastRowIndex());
        assertEquals(2, ref.getCellRange().getLastColIndex());
    }

    @Test
    public void testParseCellRangeWithAbsolute() {
        ExcelCellRef ref = ExcelCellRef.parse("$A$1:$C$3");
        assertNotNull(ref);
        assertNull(ref.getSheetName());
        assertEquals("$A$1:$C$3", ref.getCellRange().toABString(true, true));
        assertFalse(ref.getCellRange().isSingleCell());
    }

    @Test
    public void testParseWithSheetName() {
        ExcelCellRef ref = ExcelCellRef.parse("Sheet1!A1");
        assertNotNull(ref);
        assertEquals("Sheet1", ref.getSheetName());
        assertEquals("A1", ref.getCellRange().toABString());
        assertTrue(ref.getCellRange().isSingleCell());
    }

    @Test
    public void testParseWithSheetNameAndRange() {
        ExcelCellRef ref = ExcelCellRef.parse("Sheet1!A1:C3");
        assertNotNull(ref);
        assertEquals("Sheet1", ref.getSheetName());
        assertEquals("A1:C3", ref.getCellRange().toABString());
        assertFalse(ref.getCellRange().isSingleCell());
    }

    @Test
    public void testParseWithQuotedSheetName() {
        ExcelCellRef ref = ExcelCellRef.parse("'Sheet Name'!A1");
        assertNotNull(ref);
        assertEquals("Sheet Name", ref.getSheetName());
        assertEquals("A1", ref.getCellRange().toABString());
        assertTrue(ref.getCellRange().isSingleCell());
    }

    @Test
    public void testParseWithQuotedSheetNameAndRange() {
        ExcelCellRef ref = ExcelCellRef.parse("'My Sheet'!A1:C3");
        assertNotNull(ref);
        assertEquals("My Sheet", ref.getSheetName());
        assertEquals("A1:C3", ref.getCellRange().toABString());
        assertFalse(ref.getCellRange().isSingleCell());
    }

    @Test
    public void testParseWithQuotedSheetNameAndAbsolute() {
        ExcelCellRef ref = ExcelCellRef.parse("'Data Sheet'!$A$1:$C$3");
        assertNotNull(ref);
        assertEquals("Data Sheet", ref.getSheetName());
        assertEquals("$A$1:$C$3", ref.getCellRange().toABString(true, true));
        assertFalse(ref.getCellRange().isSingleCell());
    }

    @Test
    public void testParseComplexCellReferences() {
        // 测试更复杂的单元格引用
        ExcelCellRef ref1 = ExcelCellRef.parse("AA100");
        assertNotNull(ref1);
        assertNull(ref1.getSheetName());
        assertEquals("AA100", ref1.getCellRange().toABString());

        ExcelCellRef ref2 = ExcelCellRef.parse("Sheet1!AA100:ZZ200");
        assertNotNull(ref2);
        assertEquals("Sheet1", ref2.getSheetName());
        assertEquals("AA100:ZZ200", ref2.getCellRange().toABString());
    }

    @Test
    public void testParseEdgeCases() {
        // 测试边界情况
        ExcelCellRef ref1 = ExcelCellRef.parse("A1");
        assertNotNull(ref1);
        assertEquals("A1", ref1.getCellRange().toABString());

        ExcelCellRef ref2 = ExcelCellRef.parse("Sheet1!A1");
        assertNotNull(ref2);
        assertEquals("Sheet1", ref2.getSheetName());
        assertEquals("A1", ref2.getCellRange().toABString());

        // 测试只有单引号的情况
        try {
            ExcelCellRef ref3 = ExcelCellRef.parse("''!A1");
            fail();
        } catch (NopException e) {
            assertEquals(ERR_EXCEL_INVALID_CELL_REF.getErrorCode(), e.getErrorCode());
        }

    }

    @Test
    public void testToString() {
        ExcelCellRef ref1 = ExcelCellRef.parse("A1");
        assertEquals("A1", ref1.toString());

        ExcelCellRef ref2 = ExcelCellRef.parse("Sheet1!A1:C3");
        assertEquals("Sheet1!A1:C3", ref2.toString());

        ExcelCellRef ref3 = ExcelCellRef.parse("'My Sheet'!B2");
        assertEquals("'My Sheet'!B2", ref3.toString());
    }

    @Test
    public void testChangeSize() {
        ExcelCellRef ref = ExcelCellRef.parse("Sheet1!A1");
        ExcelCellRef newRef = ref.changeSize(3, 3);

        assertEquals("Sheet1", newRef.getSheetName());
        assertEquals("A1:C3", newRef.getCellRange().toABString());
        assertEquals(3, newRef.getCellRange().getRowCount());
        assertEquals(3, newRef.getCellRange().getColCount());
    }

    @Test
    public void testChangeSheetName() {
        ExcelCellRef ref = ExcelCellRef.parse("Sheet1!A1:C3");
        ExcelCellRef newRef = ref.changeSheetName("NewSheet");

        assertEquals("NewSheet", newRef.getSheetName());
        assertEquals("A1:C3", newRef.getCellRange().toABString());

        // 测试设置为null
        ExcelCellRef nullSheetRef = ref.changeSheetName(null);
        assertNull(nullSheetRef.getSheetName());
        assertEquals("A1:C3", nullSheetRef.getCellRange().toABString());
    }

    @Test
    public void testRoundTrip() {
        // 测试解析后再转换为字符串是否一致
        String[] testCases = {
                "A1",
                "A1:C3",
                "Sheet1!A1",
                "Sheet1!A1:C3"
        };

        for (String testCase : testCases) {
            ExcelCellRef ref = ExcelCellRef.parse(testCase);
            assertNotNull(ref, "Failed to parse: " + testCase);
            assertEquals(testCase, ref.toString(), "Round trip failed for: " + testCase);
        }
    }

    @Test
    public void testInvalidInput() {
        // 测试无效输入应该返回null或抛出异常
        try {
            ExcelCellRef.parse("!A1"); // 空工作表名
            fail();
        } catch (NopException e) {
            assertEquals(ERR_EXCEL_INVALID_CELL_REF.getErrorCode(), e.getErrorCode());
        }

        // 这些应该由CellRange.fromABString处理，可能返回null或抛出异常
        try {
            ExcelCellRef ref = ExcelCellRef.parse("InvalidRef");
            // 如果没有抛出异常，应该返回null
            fail();
        } catch (NopException e) {
            assertEquals(CoreErrors.ERR_TABLE_INVALID_CELL_POSITION.getErrorCode(), e.getErrorCode());
        }
    }
}