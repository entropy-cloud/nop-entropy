package io.nop.office.model;

import io.nop.office.model.constants.OfficeFontUnderline;
import io.nop.office.model.constants.OfficeHorizontalAlignment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Office 枚举三套文本（excel/css/wml）映射语义：CENTER_SELECTION 的 excel 文本
 * 与 css 文本不同；none 是 underline 的 excel 别名；越界 code 返回 null
 */
public class TestOfficeEnumTextMapping {

    @Test
    public void testHorizontalAlignmentExcelTextMapping() {
        // excelText "centerSelection" 唯一标识 CENTER_SELECTION
        assertEquals(OfficeHorizontalAlignment.CENTER_SELECTION,
                OfficeHorizontalAlignment.fromExcelText("centerSelection"));
        assertEquals(OfficeHorizontalAlignment.CENTER,
                OfficeHorizontalAlignment.fromExcelText("center"));
        // css/wml 文本下 CENTER_SELECTION 与 CENTER 同为 "center"，先注册的 CENTER 胜出
        assertEquals(OfficeHorizontalAlignment.CENTER,
                OfficeHorizontalAlignment.fromCssText("center"));
        assertEquals(OfficeHorizontalAlignment.CENTER,
                OfficeHorizontalAlignment.fromWmlText("center"));
        assertNull(OfficeHorizontalAlignment.fromExcelText("no-such-align"));
    }

    @Test
    public void testHorizontalAlignmentCodeRoundTrip() {
        for (OfficeHorizontalAlignment alignment : OfficeHorizontalAlignment.values()) {
            assertEquals(alignment, OfficeHorizontalAlignment.forInt(alignment.getCode()));
        }
        assertNull(OfficeHorizontalAlignment.forInt(-1));
        assertNull(OfficeHorizontalAlignment.forInt(OfficeHorizontalAlignment.values().length));
        assertEquals("general", OfficeHorizontalAlignment.GENERAL.toString());
    }

    @Test
    public void testFontUnderlineExcelTextWithNoneAlias() {
        assertEquals(OfficeFontUnderline.SINGLE_ACCOUNTING,
                OfficeFontUnderline.fromExcelText("singleAccounting"));
        // NONE 的 excelText 为空串，"none" 是补充注册的别名
        assertEquals(OfficeFontUnderline.NONE, OfficeFontUnderline.fromExcelText("none"));
        assertEquals(OfficeFontUnderline.SINGLE, OfficeFontUnderline.fromExcelText("single"));
        assertNull(OfficeFontUnderline.fromCssText("no-such"));
    }

    @Test
    public void testFontUnderlineByteValueMapping() {
        assertEquals(1, OfficeFontUnderline.SINGLE.getByteValue());
        assertEquals(2, OfficeFontUnderline.DOUBLE.getByteValue());
        assertEquals(0x21, OfficeFontUnderline.SINGLE_ACCOUNTING.getByteValue());
        assertEquals(0x22, OfficeFontUnderline.DOUBLE_ACCOUNTING.getByteValue());
        assertEquals(0, OfficeFontUnderline.NONE.getByteValue());

        assertEquals(OfficeFontUnderline.DOUBLE_ACCOUNTING,
                OfficeFontUnderline.fromByteValue((byte) 0x22));
        // 未知字节值回退 NONE
        assertEquals(OfficeFontUnderline.NONE, OfficeFontUnderline.fromByteValue((byte) 99));
        assertEquals(OfficeFontUnderline.DOUBLE, OfficeFontUnderline.fromValue(2));
    }
}
