package io.nop.office.model;

import io.nop.office.model.color.OfficeColorHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Office 颜色转换语义：0xRRGGBB -> #RRGGBB；0xAARRGGBB -> rgba(r,g,b,a)（透明度为首位字节/255）；
 * 短 hex 补零到6位，避免输出非法 CSS；已带 # 或空值原样保留
 */
public class TestOfficeColorHelper {

    @Test
    public void testSixDigitHexPrefixedWithHash() {
        assertEquals("#FF0000", OfficeColorHelper.toCssColor("0xFF0000"));
        assertEquals("#00FF00", OfficeColorHelper.toCssColor("00FF00"));
    }

    @Test
    public void testEightDigitHexBecomesRgba() {
        // 首位字节 AA=0x80=128 -> alpha=128/255
        String css = OfficeColorHelper.toCssColor("0x80FF0000");
        assertEquals("rgba(255,0,0," + (128 / 255.0) + ")", css);
    }

    @Test
    public void testShortHexPaddedToSixDigits() {
        // 默认字体色 "0x0" 必须补零为 #000000，而非非法的 "#0"
        assertEquals("#000000", OfficeColorHelper.toCssColor("0x0"));
        assertEquals("#000000", OfficeColorHelper.toCssColor("0x00"));
        assertEquals("#0000AB", OfficeColorHelper.toCssColor("AB"));
    }

    @Test
    public void testHashPrefixedAndEmptyPassThrough() {
        assertEquals("#abc", OfficeColorHelper.toCssColor("#abc"));
        assertEquals("", OfficeColorHelper.toCssColor(""));
        assertEquals(null, OfficeColorHelper.toCssColor(null));
    }
}
