package io.nop.ooxml.xlsx.parse;

import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.model.SharedStringsPart;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestExcelHeaderFooterCodes extends BaseTestCase {

    @Test
    public void testParseSectionsDefaultCenter() {
        List<ExcelHeaderFooterCodes.Section> sections = ExcelHeaderFooterCodes.parseSections("plain text");
        assertEquals(1, sections.size());
        assertEquals("C", sections.get(0).getPosition());
        assertEquals("plain text", sections.get(0).getText());
    }

    @Test
    public void testParseSectionsThreePositions() {
        List<ExcelHeaderFooterCodes.Section> sections = ExcelHeaderFooterCodes.parseSections("&Lleft&Cmid&Rright");
        assertEquals(3, sections.size());
        assertEquals("L", sections.get(0).getPosition());
        assertEquals("left", sections.get(0).getText());
        assertEquals("C", sections.get(1).getPosition());
        assertEquals("mid", sections.get(1).getText());
        assertEquals("R", sections.get(2).getPosition());
        assertEquals("right", sections.get(2).getText());
    }

    @Test
    public void testParseSectionsKeepsFontCodes() {
        // 对版：test-pdf.xpt.xlsx 的真实 oddFooter
        List<ExcelHeaderFooterCodes.Section> sections = ExcelHeaderFooterCodes
                .parseSections("&C&\"SimSun,Plain\"&9 第1页/共3页");
        assertEquals(1, sections.size());
        assertEquals("C", sections.get(0).getPosition());
        assertEquals("&\"SimSun,Plain\"&9 第1页/共3页", sections.get(0).getText());
        assertEquals("SimSun", ExcelHeaderFooterCodes.firstFontName(sections.get(0).getText()));
        assertEquals(9, ExcelHeaderFooterCodes.firstFontSize(sections.get(0).getText()));
    }

    @Test
    public void testEscapeAndLeadingPosition() {
        List<ExcelHeaderFooterCodes.Section> sections = ExcelHeaderFooterCodes.parseSections("&La&&b");
        assertEquals(1, sections.size());
        assertEquals("L", sections.get(0).getPosition());
        assertEquals("a&&b", sections.get(0).getText());
    }

    /**
     * WI4 审计 Major-2：六元素 + differentFirst/differentOddEven 开关的 SAX 链组合。
     */
    @Test
    public void testSixElementsAndFlagsViaSaxChain() {
        ExcelWorkbook workbook = new ExcelWorkbook();
        SimpleSheetContentsHandler contentsHandler = new SimpleSheetContentsHandler(workbook, "s1");
        SheetNodeHandler handler = new SheetNodeHandler((SharedStringsPart) null, contentsHandler);
        String xml = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<headerFooter differentFirst=\"1\" differentOddEven=\"1\">"
                + "<oddHeader>&amp;Loh</oddHeader><oddFooter>&amp;Cof</oddFooter>"
                + "<firstHeader>&amp;Lfh</firstHeader><firstFooter>&amp;Cff</firstFooter>"
                + "<evenHeader>&amp;Leh</evenHeader><evenFooter>&amp;Cef</evenFooter>"
                + "</headerFooter>"
                + "</worksheet>";
        XNodeParser.instance().handler(handler).parseFromResource(new ByteArrayResource(
                "/sheet1.xml", xml.getBytes(StandardCharsets.UTF_8), 0));
        ExcelSheet sheet = contentsHandler.getSheet();

        assertTrue(sheet.getPageSetup().getDifferentFirst());
        assertTrue(sheet.getPageSetup().getDifferentOddEven());
        assertEquals("oh", sheet.getPageSetup().getHeader().getLeft());
        assertEquals("of", sheet.getPageSetup().getFooter().getCenter());
        assertEquals("fh", sheet.getPageSetup().getFirstHeader().getLeft());
        assertEquals("ff", sheet.getPageSetup().getFirstFooter().getCenter());
        assertEquals("eh", sheet.getPageSetup().getEvenHeader().getLeft());
        assertEquals("ef", sheet.getPageSetup().getEvenFooter().getCenter());
    }
}
