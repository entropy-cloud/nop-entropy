package io.nop.excel.model.color;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 6位RRGGBB颜色的ARGB换算：修复前rgb()不置alpha位，6位hex颜色经
 * new Color(argb, true) 全透明，且纯黑 #000000 与 toArgbInt 的0哨兵混淆
 */
public class TestColorHelper {

    @Test
    public void testToArgbIntSetsOpaqueAlpha() {
        int argb = ColorHelper.toArgbInt("#5470C6");
        assertEquals(0xFF, new Color(argb, true).getAlpha(), "6位hex应是不透明颜色");
        assertEquals(0x54, new Color(argb, true).getRed());
        assertEquals(0xC6, new Color(argb, true).getBlue());
    }

    @Test
    public void testBlackIsNotSentinel() {
        assertNotEquals(0, ColorHelper.toArgbInt("#000000"), "纯黑不能与无效色哨兵0混淆");
        assertEquals(0xFF000000, ColorHelper.toArgbInt("#000000"));
        assertEquals(0, ColorHelper.toArgbInt(null));
        assertEquals(0, ColorHelper.toArgbInt(""));
    }

    @Test
    public void testArgb8DigitsUnchanged() {
        assertEquals(0x80FF0000, ColorHelper.toArgbInt("80FF0000"));
    }

    @Test
    public void testToCssColorPadsShortHex() {
        // 默认字体色"0x0"必须输出合法CSS（修复前输出"#0"，浏览器丢弃该声明）
        assertEquals("#000000", ColorHelper.toCssColor("0x0"));
        assertEquals("#5470c6", ColorHelper.toCssColor("#5470c6"));
        assertTrue(ColorHelper.toCssColor("80FF0000").startsWith("rgba("));
    }
}
