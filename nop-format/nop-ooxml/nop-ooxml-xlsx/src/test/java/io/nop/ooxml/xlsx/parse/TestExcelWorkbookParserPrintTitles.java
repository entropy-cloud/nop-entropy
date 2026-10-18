package io.nop.ooxml.xlsx.parse;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.api.core.exceptions.NopException;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.model.WorkbookPart;
import io.nop.ooxml.xlsx.model.XSSFSheetRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestExcelWorkbookParserPrintTitles extends BaseTestCase {

    private static XSSFSheetRef sheetRef(String name) {
        return new XSSFSheetRef("rId1", name, "1");
    }

    private static WorkbookPart workbookPart(String sheetsXml, String definedNamesXml) {
        String xml = "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + sheetsXml + definedNamesXml + "</workbook>";
        XNode node = XNode.parse(xml);
        return new WorkbookPart("xl/workbook.xml", node);
    }

    private static final String SHEETS_TWO = "<sheets>"
            + "<sheet name=\"SheetA\" sheetId=\"1\" r:id=\"rId1\"/>"
            + "<sheet name=\"SheetB\" sheetId=\"2\" r:id=\"rId2\"/>"
            + "</sheets>";

    @Test
    public void testParseSegmentsRowAndCol() {
        List<String[]> ret = ExcelWorkbookParser.parsePrintTitleSegments("Sheet1!$1:$2");
        assertEquals(1, ret.size());
        assertEquals("row", ret.get(0)[0]);
        assertEquals("1:2", ret.get(0)[1]);

        ret = ExcelWorkbookParser.parsePrintTitleSegments("'My Sheet'!$A:$B");
        assertEquals(1, ret.size());
        assertEquals("col", ret.get(0)[0]);
        assertEquals("A:B", ret.get(0)[1]);

        // Excel 真实形态：行+列同时设置为逗号分隔两段，各带 sheet 前缀，列段可在前
        ret = ExcelWorkbookParser.parsePrintTitleSegments("'First Sheet'!$A:$A,'First Sheet'!$1:$4");
        assertEquals(2, ret.size());
        assertEquals("col", ret.get(0)[0]);
        assertEquals("A:A", ret.get(0)[1]);
        assertEquals("row", ret.get(1)[0]);
        assertEquals("1:4", ret.get(1)[1]);
    }

    @Test
    public void testParseSegmentsInvalidForms() {
        // 单元格区域形态不支持
        NopException err = assertThrows(NopException.class,
                () -> ExcelWorkbookParser.parsePrintTitleSegments("Sheet1!$A$1:$B$2"));
        assertTrue(err.getMessage().contains("A$1"));

        // 倒序
        assertThrows(NopException.class,
                () -> ExcelWorkbookParser.parsePrintTitleSegments("Sheet1!$5:$2"));

        // 混合行列段
        assertThrows(NopException.class,
                () -> ExcelWorkbookParser.parsePrintTitleSegments("Sheet1!$A:$1"));

        // 空段
        assertThrows(NopException.class,
                () -> ExcelWorkbookParser.parsePrintTitleSegments("Sheet1!$1:$2,"));
    }

    @Test
    public void testApplyPrintTitlesRowsAndCols() {
        WorkbookPart part = workbookPart(SHEETS_TWO,
                "<definedNames>"
                        + "<definedName name=\"_xlnm.Print_Titles\" localSheetId=\"0\">SheetA!$1:$2,SheetA!$A:$B</definedName>"
                        + "</definedNames>");
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("SheetA");

        new ExcelWorkbookParser().applyPrintTitles(part.getSheets().get(0), part, sheet);

        assertNotNull(sheet.getPageSetup());
        assertEquals("1:2", sheet.getPageSetup().getRepeatTitleRows());
        assertEquals("A:B", sheet.getPageSetup().getRepeatTitleCols());
    }

    @Test
    public void testApplyPrintTitlesOtherSheetNotMatched() {
        WorkbookPart part = workbookPart(SHEETS_TWO,
                "<definedNames>"
                        + "<definedName name=\"_xlnm.Print_Titles\" localSheetId=\"1\">SheetB!$3:$4</definedName>"
                        + "</definedNames>");
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("SheetA");

        new ExcelWorkbookParser().applyPrintTitles(part.getSheets().get(0), part, sheet);

        assertNull(sheet.getPageSetup());
    }

    @Test
    public void testApplyPrintTitlesLocalIndexIgnoresSheetFilter() {
        // localSheetId 按完整 sheet 列表下标：第二个 sheet 声明的 titles 只对 index=1 生效
        WorkbookPart part = workbookPart(SHEETS_TWO,
                "<definedNames>"
                        + "<definedName name=\"_xlnm.Print_Titles\" localSheetId=\"1\">SheetB!$1:$1</definedName>"
                        + "</definedNames>");
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("SheetB");

        new ExcelWorkbookParser().applyPrintTitles(part.getSheets().get(1), part, sheet);

        assertEquals("1:1", sheet.getPageSetup().getRepeatTitleRows());
    }

    @Test
    public void testApplyPrintTitlesDuplicateDefinedNameFails() {
        WorkbookPart part = workbookPart(SHEETS_TWO,
                "<definedNames>"
                        + "<definedName name=\"_xlnm.Print_Titles\" localSheetId=\"0\">SheetA!$1:$2</definedName>"
                        + "<definedName name=\"_xlnm.Print_Titles\" localSheetId=\"0\">SheetA!$2:$3</definedName>"
                        + "</definedNames>");
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("SheetA");

        assertThrows(NopException.class,
                () -> new ExcelWorkbookParser().applyPrintTitles(part.getSheets().get(0), part, sheet));
    }

    @Test
    public void testApplyPrintTitlesMissingLocalSheetIdFails() {
        WorkbookPart part = workbookPart(SHEETS_TWO,
                "<definedNames>"
                        + "<definedName name=\"_xlnm.Print_Titles\">SheetA!$1:$2</definedName>"
                        + "</definedNames>");
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("SheetA");

        assertThrows(NopException.class,
                () -> new ExcelWorkbookParser().applyPrintTitles(part.getSheets().get(0), part, sheet));
    }

    @Test
    public void testApplyPrintTitlesKeepsExistingPageSetup() {
        // 已有 pageSetup 时只补字段，不覆盖 paperSize 等既有配置
        WorkbookPart part = workbookPart(SHEETS_TWO,
                "<definedNames>"
                        + "<definedName name=\"_xlnm.Print_Titles\" localSheetId=\"0\">SheetA!$1:$2</definedName>"
                        + "</definedNames>");
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("SheetA");
        io.nop.excel.model.ExcelPageSetup pageSetup = new io.nop.excel.model.ExcelPageSetup();
        pageSetup.setPaperSize(9);
        sheet.setPageSetup(pageSetup);

        new ExcelWorkbookParser().applyPrintTitles(part.getSheets().get(0), part, sheet);

        assertEquals(Integer.valueOf(9), sheet.getPageSetup().getPaperSize());
        assertEquals("1:2", sheet.getPageSetup().getRepeatTitleRows());
    }

    /**
     * 真实 xlsx fixture（含 CJK sheet 名的 Print_Titles 行段）端到端解析。
     */
    @Test
    public void testParseCalcFixtureWithPrintTitles() {
        ExcelWorkbook wb = new ExcelWorkbookParser()
                .parseFromResource(new ClassPathResource("classpath:xlsx/calc.xlsx"));
        ExcelSheet sheet = wb.getSheets().get(0);
        assertEquals("贷款计算器", sheet.getName());
        assertNotNull(sheet.getPageSetup(), "Print_Titles should create pageSetup");
        assertEquals("9:9", sheet.getPageSetup().getRepeatTitleRows());
        assertNull(sheet.getPageSetup().getRepeatTitleCols());
    }
}
