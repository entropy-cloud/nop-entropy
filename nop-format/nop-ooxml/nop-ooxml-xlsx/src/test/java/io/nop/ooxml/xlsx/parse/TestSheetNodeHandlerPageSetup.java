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

/**
 * WI2：pageSetup 的 scale/fitToWidth/fitToHeight 解析（fitToPage 门控）。
 */
public class TestSheetNodeHandlerPageSetup extends BaseTestCase {

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
    public void testFitToPageOnParsesFitAndScale() {
        ExcelSheet sheet = parseSheet(HEAD
                + "<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>"
                + "<pageSetup paperSize=\"9\" scale=\"80\" fitToWidth=\"2\" fitToHeight=\"3\"/>"
                + TAIL);
        assertEquals(Integer.valueOf(2), sheet.getPageSetup().getFitToWidth());
        assertEquals(Integer.valueOf(3), sheet.getPageSetup().getFitToHeight());
        assertEquals(Integer.valueOf(80), sheet.getPageSetup().getScale());
    }

    @Test
    public void testFitToPageOffIgnoresFitAttrs() {
        // fitToPage 缺省关闭：真实 Excel 文件常带默认 fitToWidth="1"，不得误启用 fit 模式
        ExcelSheet sheet = parseSheet(HEAD
                + "<pageSetup paperSize=\"9\" scale=\"80\" fitToWidth=\"2\" fitToHeight=\"3\"/>"
                + TAIL);
        assertNull(sheet.getPageSetup().getFitToWidth());
        assertNull(sheet.getPageSetup().getFitToHeight());
        assertEquals(Integer.valueOf(80), sheet.getPageSetup().getScale());
    }

    @Test
    public void testFitToPageOnWithDefaults() {
        // fitToPage 开且未写 fit 属性：OOXML 缺省 1×1 页
        ExcelSheet sheet = parseSheet(HEAD
                + "<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>"
                + "<pageSetup paperSize=\"9\"/>"
                + TAIL);
        assertEquals(Integer.valueOf(1), sheet.getPageSetup().getFitToWidth());
        assertEquals(Integer.valueOf(1), sheet.getPageSetup().getFitToHeight());
        assertNull(sheet.getPageSetup().getScale());
    }

    @Test
    public void testPlainPageSetupUnchanged() {
        ExcelSheet sheet = parseSheet(HEAD
                + "<pageSetup paperSize=\"9\" orientation=\"landscape\"/>"
                + TAIL);
        assertEquals(Integer.valueOf(9), sheet.getPageSetup().getPaperSize());
        assertNull(sheet.getPageSetup().getScale());
        assertNull(sheet.getPageSetup().getFitToWidth());
        assertNull(sheet.getPageSetup().getFitToHeight());
    }
}
