package io.nop.ooxml.xlsx.parse;

import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.model.table.ICell;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelRichText;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.model.SharedStringsPart;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI11：SheetNodeHandler inline <is> 富文本 run 捕获 + SST rich 查找——
 * value 拍平语义不变，run 结构经 richText 回调回挂 ExcelCell.richText。
 */
public class TestSheetNodeHandlerRichText {

    private ExcelSheet parseSheet(String sheetXml, SharedStringsPart sst) {
        ExcelWorkbook workbook = new ExcelWorkbook();
        // cell.setLocation 依赖 workbook.resourcePath()（Guard 非空校验）
        workbook.setLocation(io.nop.api.core.util.SourceLocation.fromPath("/test.xlsx"));
        SimpleSheetContentsHandler contentsHandler = new SimpleSheetContentsHandler(workbook, "s1");
        SheetNodeHandler handler = new SheetNodeHandler(sst, contentsHandler);
        XNodeParser.instance().handler(handler).parseFromResource(new ByteArrayResource(
                "/sheet1.xml", sheetXml.getBytes(StandardCharsets.UTF_8), 0));
        return contentsHandler.getSheet();
    }

    static final String HEAD = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">";
    static final String TAIL = "</worksheet>";

    @Test
    public void testInlineRichRunsAttachedToCell() {
        ExcelSheet sheet = parseSheet(HEAD
                + "<row r=\"1\">"
                + "<c r=\"A1\" t=\"inlineStr\"><is>"
                + "<r><rPr><b/><sz val=\"14\"/><rFont val=\"Arial\"/><color rgb=\"FF00FF00\"/></rPr><t>Bold</t></r>"
                + "<r><t> plain</t></r>"
                + "</is></c>"
                + "</row>"
                + TAIL, null);

        ICell icell = sheet.getTable().getCell(0, 0);
        assertTrue(icell instanceof ExcelCell);
        ExcelCell cell = (ExcelCell) icell;
        // 拍平语义不变
        assertEquals("Bold plain", cell.getValue());
        // run 结构已回挂
        ExcelRichText rich = cell.getRichText();
        assertNotNull(rich, "rich runs must attach to cell");
        assertEquals(2, rich.getParts().size());
        assertEquals("Bold", rich.getParts().get(0).getText());
        assertNotNull(rich.getParts().get(0).getFont());
        assertTrue(rich.getParts().get(0).getFont().isBold());
        assertEquals(14f, rich.getParts().get(0).getFont().getFontSize(), 0.001f);
        assertEquals(" plain", rich.getParts().get(1).getText());
        assertNull(rich.getParts().get(1).getFont());
    }

    @Test
    public void testInlinePlainTextNoRichText() {
        ExcelSheet sheet = parseSheet(HEAD
                + "<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>plain</t></is></c></row>"
                + TAIL, null);
        ExcelCell cell = (ExcelCell) sheet.getTable().getCell(0, 0);
        assertEquals("plain", cell.getValue());
        assertNull(cell.getRichText(), "no runs → no richText attached");
    }

    @Test
    public void testSstRichLookup() {
        SharedStringsPart sst = new SharedStringsTableParser(false).parseFromResource(new ByteArrayResource(
                "/sst.xml", ("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                        + "<si><r><rPr><b/></rPr><t>Rich</t></r></si>"
                        + "<si><t>plain</t></si>"
                        + "</sst>").getBytes(StandardCharsets.UTF_8), 0));

        ExcelSheet sheet = parseSheet(HEAD
                + "<row r=\"1\">"
                + "<c r=\"A1\" t=\"s\"><v>0</v></c>"
                + "<c r=\"B1\" t=\"s\"><v>1</v></c>"
                + "</row>"
                + TAIL, sst);

        ExcelCell a1 = (ExcelCell) sheet.getTable().getCell(0, 0);
        assertEquals("Rich", a1.getValue());
        assertNotNull(a1.getRichText(), "SST rich entry must attach");
        assertEquals("Rich", a1.getRichText().getParts().get(0).getText());

        ExcelCell b1 = (ExcelCell) sheet.getTable().getCell(0, 1);
        assertEquals("plain", b1.getValue());
        assertNull(b1.getRichText(), "plain SST entry → no richText");
    }
}
