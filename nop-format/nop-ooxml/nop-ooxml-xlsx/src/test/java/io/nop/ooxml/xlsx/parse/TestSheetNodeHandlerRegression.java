package io.nop.ooxml.xlsx.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.model.table.CellPosition;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.ooxml.xlsx.XlsxErrors;
import io.nop.ooxml.xlsx.model.SharedStringsPart;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归：不可信单元格引用的边界校验与缺失 r 属性的顺序推算
 */
public class TestSheetNodeHandlerRegression {

    static class RecordingHandler implements SheetContentsHandler {
        final List<String> cells = new ArrayList<>();

        @Override
        public void startSheet(String sheetName) {
        }

        @Override
        public void cols(List<io.nop.excel.model.ExcelColumnConfig> cols) {
        }

        @Override
        public void pageMargins(io.nop.excel.model.ExcelPageMargins pageMargins) {
        }

        @Override
        public void sheetFormat(Double defaultRowHeight) {
        }

        @Override
        public void startRow(int rowNum, Double height, boolean hidden) {
        }

        @Override
        public void endRow(int rowNum) {
        }

        @Override
        public void cell(CellPosition cellRef, Object value, String formulaStr, int styleId) {
            cells.add(cellRef.toABString() + "=" + value);
        }

        @Override
        public void mergeCell(io.nop.core.model.table.CellRange range) {
        }

        @Override
        public void drawing(String id) {
        }

        @Override
        public void link(String ref, String location, String rId) {
        }
    }

    static List<String> parseCells(String sheetXml) {
        RecordingHandler recorder = new RecordingHandler();
        SheetNodeHandler handler = new SheetNodeHandler((SharedStringsPart) null, recorder);
        XNodeParser.instance().handler(handler).parseFromResource(new ByteArrayResource(
                "sheet1.xml", sheetXml.getBytes(StandardCharsets.UTF_8), 0));
        return recorder.cells;
    }

    static final String SHEET_HEAD = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>";
    static final String SHEET_TAIL = "</sheetData></worksheet>";

    // 修复前 ZZZZZZ1 会被解析为约3.2亿的列索引，下游集合按索引补null造成内存放大
    @Test
    public void testCellRefOutOfRangeRejected() {
        String xml = SHEET_HEAD
                + "<row r=\"1\"><c r=\"ZZZZZZ1\" t=\"inlineStr\"><is><t>x</t></is></c></row>"
                + SHEET_TAIL;
        NopException e = assertThrows(NopException.class, () -> parseCells(xml));
        assertEquals(XlsxErrors.ERR_XLSX_CELL_REF_OUT_OF_RANGE.getErrorCode(), e.getErrorCode());
    }

    // ECMA-376 允许省略 c/@r：缺失时按出现顺序推算（此前静默变null，下游NPE）
    @Test
    public void testMissingCellRefInferredByOrder() {
        String xml = SHEET_HEAD
                + "<row r=\"1\">"
                + "<c t=\"inlineStr\"><is><t>a</t></is></c>"
                + "<c t=\"inlineStr\"><is><t>b</t></is></c>"
                + "</row>"
                + SHEET_TAIL;
        List<String> cells = parseCells(xml);
        assertEquals(2, cells.size());
        assertTrue(cells.get(0).startsWith("A1="), cells.toString());
        assertTrue(cells.get(1).startsWith("B1="), cells.toString());
    }

    // 混合：显式引用后，后续省略的引用从上一列继续
    @Test
    public void testMissingCellRefContinuesFromExplicit() {
        String xml = SHEET_HEAD
                + "<row r=\"1\">"
                + "<c r=\"C1\" t=\"inlineStr\"><is><t>a</t></is></c>"
                + "<c t=\"inlineStr\"><is><t>b</t></is></c>"
                + "</row>"
                + SHEET_TAIL;
        List<String> cells = parseCells(xml);
        assertEquals(2, cells.size());
        assertTrue(cells.get(0).startsWith("C1="), cells.toString());
        assertTrue(cells.get(1).startsWith("D1="), cells.toString());
    }
}
