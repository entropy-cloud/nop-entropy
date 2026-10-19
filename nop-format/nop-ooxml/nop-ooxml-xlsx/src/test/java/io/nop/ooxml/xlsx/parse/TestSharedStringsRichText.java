package io.nop.ooxml.xlsx.parse;

import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.excel.model.ExcelRichText;
import io.nop.excel.model.ExcelRichTextPart;
import io.nop.ooxml.xlsx.model.SharedStringsPart;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI11：SharedStringsTableParser 富文本 run 捕获——richItems 与 items 平行，
 * 拍平语义不变；SpreadsheetML rPr 映射（sz 为 pt、b/i 空元素、rFont/color 带 val/rgb）。
 */
public class TestSharedStringsRichText {

    private SharedStringsPart parse(String xml, boolean includePhonetic) {
        return new SharedStringsTableParser(includePhonetic).parseFromResource(new ByteArrayResource(
                "/sst.xml", xml.getBytes(StandardCharsets.UTF_8), 0));
    }

    static final String SST_HEAD = "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
            + " count=\"4\" uniqueCount=\"4\">";

    @Test
    public void testRichRunsParsedAndFlattenUnchanged() {
        SharedStringsPart part = parse(SST_HEAD
                // si0：两个 run（run1 带 rPr：b/sz/rFont/color；run2 无 rPr）
                + "<si><r><rPr><b/><sz val=\"14\"/><rFont val=\"Arial\"/><color rgb=\"FFFF0000\"/></rPr>"
                + "<t>Bold</t></r><r><t> normal</t></r></si>"
                // si1：纯文本（无 run）——richItems 对应位必须为 null
                + "<si><t>plain</t></si>"
                // si2：带 rPh 的 run（phonetic 不进 runs）
                + "<si><r><t>Main</t></r><rPh><t>phonetic</t></rPh></si>"
                // si3：i val=0 显式关闭
                + "<si><r><rPr><i val=\"0\"/></rPr><t>notitalic</t></r></si>"
                + "</sst>", false);

        // 拍平语义不变
        assertEquals(4, part.getItems().size());
        assertEquals("Bold normal", part.getItems().get(0));
        assertEquals("plain", part.getItems().get(1));
        assertEquals("Main", part.getItems().get(2));

        // richItems 与 items 同长对齐
        ExcelRichText rich0 = part.getRichItemAt(0);
        assertNotNull(rich0, "si0 has runs");
        List<ExcelRichTextPart> parts = rich0.getParts();
        assertEquals(2, parts.size());

        ExcelRichTextPart p0 = parts.get(0);
        assertEquals("Bold", p0.getText());
        assertNotNull(p0.getFont(), "rPr present → font mapped");
        assertTrue(p0.getFont().isBold());
        assertEquals(14f, p0.getFont().getFontSize(), 0.001f, "SpreadsheetML sz is pt (no /100)");
        assertEquals("Arial", p0.getFont().getFontName());
        assertEquals("FFFF0000", p0.getFont().getFontColor());

        ExcelRichTextPart p1 = parts.get(1);
        assertEquals(" normal", p1.getText());
        assertNull(p1.getFont(), "run without rPr → null font (cell style fallback)");

        assertNull(part.getRichItemAt(1), "plain si → null rich entry");

        ExcelRichText rich2 = part.getRichItemAt(2);
        assertNotNull(rich2);
        assertEquals(1, rich2.getParts().size(), "rPh must not enter runs");
        assertEquals("Main", rich2.getParts().get(0).getText());

        ExcelRichText rich3 = part.getRichItemAt(3);
        assertNotNull(rich3);
        assertFalse(rich3.getParts().get(0).getFont().isItalic(), "i val=0 must not set italic");
    }

    @Test
    public void testNoRunsLeavesRichItemsNull() {
        SharedStringsPart part = parse(SST_HEAD + "<si><t>a</t></si><si><t>b</t></si>" + "</sst>", false);
        assertNull(part.getRichItems(), "no rich si → richItems stays null");
        assertEquals("a", part.getItemAt(0));
    }
}
