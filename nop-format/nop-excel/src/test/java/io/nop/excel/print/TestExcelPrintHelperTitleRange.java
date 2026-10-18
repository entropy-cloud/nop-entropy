package io.nop.excel.print;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestExcelPrintHelperTitleRange {

    @Test
    public void testParseTitleRangeRows() {
        assertArrayEquals(new int[]{1, 2}, ExcelPrintHelper.parseTitleRange("1:2"));
        assertArrayEquals(new int[]{9, 9}, ExcelPrintHelper.parseTitleRange("9:9"));
    }

    @Test
    public void testParseTitleRangeCols() {
        assertArrayEquals(new int[]{1, 2}, ExcelPrintHelper.parseTitleRange("A:B"));
        assertArrayEquals(new int[]{26, 28}, ExcelPrintHelper.parseTitleRange("Z:AB"));
        assertEquals(28, ExcelPrintHelper.colLettersToIndex("AB"));
        assertEquals(703, ExcelPrintHelper.colLettersToIndex("AAA"));
    }

    @Test
    public void testParseTitleRangeInvalidForms() {
        assertThrows(IllegalArgumentException.class, () -> ExcelPrintHelper.parseTitleRange(null));
        assertThrows(IllegalArgumentException.class, () -> ExcelPrintHelper.parseTitleRange(""));
        assertThrows(IllegalArgumentException.class, () -> ExcelPrintHelper.parseTitleRange("1"));
        assertThrows(IllegalArgumentException.class, () -> ExcelPrintHelper.parseTitleRange("1:"));
        assertThrows(IllegalArgumentException.class, () -> ExcelPrintHelper.parseTitleRange(":2"));
        assertThrows(IllegalArgumentException.class, () -> ExcelPrintHelper.parseTitleRange("2:1"));
        assertThrows(IllegalArgumentException.class, () -> ExcelPrintHelper.parseTitleRange("A:1"));
        assertThrows(IllegalArgumentException.class, () -> ExcelPrintHelper.parseTitleRange("A1:B2"));
    }

    @Test
    public void testIsPrefixRange() {
        assertTrue(ExcelPrintHelper.isPrefixRange(new int[]{1, 1}));
        assertTrue(ExcelPrintHelper.isPrefixRange(new int[]{1, 5}));
        assertFalse(ExcelPrintHelper.isPrefixRange(new int[]{3, 4}));
        assertFalse(ExcelPrintHelper.isPrefixRange(null));
    }
}
