package io.nop.ooxml.xlsx.parse;

import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.model.SharedStringsPart;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3：rowBreaks/colBreaks/brk 解析写入既有 pageBreaks 模型（0-based break-after）。
 */
public class TestSheetNodeHandlerPageBreaks extends BaseTestCase {

    private ExcelSheet parseSheet(String sheetXml) {
        ExcelWorkbook workbook = new ExcelWorkbook();
        SimpleSheetContentsHandler contentsHandler = new SimpleSheetContentsHandler(workbook, "s1");
        SheetNodeHandler handler = new SheetNodeHandler((SharedStringsPart) null, contentsHandler);
        XNodeParser.instance().handler(handler).parseFromResource(new ByteArrayResource(
                "/sheet1.xml", sheetXml.getBytes(StandardCharsets.UTF_8), 0));
        return contentsHandler.getSheet();
    }

    static final String HEAD = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">";
    static final String TAIL = "</worksheet>";

    @Test
    public void testParseRowAndColBreaks() {
        ExcelSheet sheet = parseSheet(HEAD
                + "<rowBreaks count=\"2\" manualBreakCount=\"2\">"
                + "<brk id=\"9\" max=\"16383\" man=\"1\"/><brk id=\"19\" max=\"16383\" man=\"1\"/>"
                + "</rowBreaks>"
                + "<colBreaks count=\"1\" manualBreakCount=\"1\">"
                + "<brk id=\"2\" max=\"255\" man=\"1\"/>"
                + "</colBreaks>"
                + TAIL);
        assertEquals(java.util.Arrays.asList(9, 19), sheet.getPageBreaks().getRows());
        assertEquals(java.util.Arrays.asList(2), sheet.getPageBreaks().getCols());
    }

    @Test
    public void testNoBreaksLeavesPageBreaksNull() {
        ExcelSheet sheet = parseSheet(HEAD + "<pageSetup paperSize=\"9\"/>" + TAIL);
        assertNull(sheet.getPageBreaks());
    }

    @Test
    public void testBrkMissingIdIgnored() {
        ExcelSheet sheet = parseSheet(HEAD
                + "<rowBreaks count=\"1\" manualBreakCount=\"0\"><brk max=\"16383\" man=\"0\"/></rowBreaks>"
                + TAIL);
        // brk 无 id 被忽略：pageBreaks 容器虽创建但 rows 未收集（空即无分页符）
        assertTrue(sheet.getPageBreaks() == null || sheet.getPageBreaks().getRows() == null,
                "brk without id must not collect any row break");
    }
}
