package io.nop.datav.service.screen;

import io.nop.api.core.exceptions.NopException;
import io.nop.datav.biz.ScreenThemeConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_THEME_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 单元测试 {@link ScreenThemeParser}（D4-3 主题解析）。覆盖：
 *
 * <ul>
 *   <li>缺省/空 backgroundConfig → 缺省 palette + 缺省 background（非 null）</li>
 *   <li>legacy 自由格式（不含 palette/background 键）→ 不报错，用缺省</li>
 *   <li>含 palette/background 键 → 命名色/background type 正确解析 + 未指定色回退缺省</li>
 *   <li>含 palette/background 键但值结构非法 → {@code ERR_DATAV_INVALID_THEME_CONFIG}</li>
 *   <li>widget 主题命名引用解析期替换为 palette 实际色值；styleOptions 不被自动改写</li>
 * </ul>
 *
 * <p>纯单元测试，不经 IoC。</p>
 */
public class TestScreenThemeParser {

    private final ScreenThemeParser parser = new ScreenThemeParser();

    // ==================== palette/background 解析 ====================

    @Test
    public void testNullBackgroundConfigReturnsDefaults() {
        ScreenThemeConfig theme = parser.resolve("screen-1", null);

        assertNotNull(theme);
        assertDefaultPalette(theme);
        assertEquals("color", theme.getBackground().getType());
        assertEquals("#131A2E", theme.getBackground().getValue());
    }

    @Test
    public void testEmptyBackgroundConfigReturnsDefaults() {
        ScreenThemeConfig theme = parser.resolve("screen-1", new LinkedHashMap<>());

        assertNotNull(theme);
        assertDefaultPalette(theme);
        assertEquals("color", theme.getBackground().getType());
        assertEquals("#131A2E", theme.getBackground().getValue());
    }

    @Test
    public void testLegacyFreeformBackgroundConfigDoesNotThrow() {
        // legacy 自由格式：{"color":"#123456"}，不含 palette/background 键 → 不报错，用缺省
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("color", "#123456");

        ScreenThemeConfig theme = parser.resolve("screen-1", legacy);

        assertNotNull(theme);
        assertDefaultPalette(theme);
        assertEquals("color", theme.getBackground().getType());
        assertEquals("#131A2E", theme.getBackground().getValue());
    }

    @Test
    public void testStructuredPaletteOverridesDefaults() {
        Map<String, Object> bgConfig = new LinkedHashMap<>();
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put("primary", "#FF0000");
        palette.put("custom-series", "#ABCDEF"); // 未声明名也保留
        bgConfig.put("palette", palette);

        ScreenThemeConfig theme = parser.resolve("screen-1", bgConfig);

        // 用户指定的覆盖
        assertEquals("#FF0000", theme.getPalette().get("primary"));
        assertEquals("#ABCDEF", theme.getPalette().get("custom-series"));
        // 未指定的保留默认
        assertEquals("#13C2C2", theme.getPalette().get("secondary"));
        assertEquals("#52C41A", theme.getPalette().get("success"));
        assertEquals("#131A2E", theme.getPalette().get("background"));
        // background 缺省：type=color，value=palette.background
        assertEquals("color", theme.getBackground().getType());
        assertEquals("#131A2E", theme.getBackground().getValue());
    }

    @Test
    public void testStructuredBackgroundTypeAndValue() {
        Map<String, Object> bgConfig = new LinkedHashMap<>();
        Map<String, Object> background = new LinkedHashMap<>();
        background.put("type", "image");
        background.put("value", "https://example.com/bg.png");
        bgConfig.put("background", background);

        ScreenThemeConfig theme = parser.resolve("screen-1", bgConfig);

        assertEquals("image", theme.getBackground().getType());
        assertEquals("https://example.com/bg.png", theme.getBackground().getValue());
        // palette 全部缺省
        assertDefaultPalette(theme);
    }

    @Test
    public void testBackgroundValueDefaultsToPaletteBackgroundWhenColor() {
        Map<String, Object> bgConfig = new LinkedHashMap<>();
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put("background", "#000000");
        bgConfig.put("palette", palette);
        Map<String, Object> background = new LinkedHashMap<>();
        background.put("type", "color"); // value 缺省
        bgConfig.put("background", background);

        ScreenThemeConfig theme = parser.resolve("screen-1", bgConfig);

        assertEquals("color", theme.getBackground().getType());
        // value 缺省取 palette.background（已被覆盖为 #000000）
        assertEquals("#000000", theme.getBackground().getValue());
    }

    @Test
    public void testBackgroundTypeDefaultsToColorWhenMissing() {
        Map<String, Object> bgConfig = new LinkedHashMap<>();
        Map<String, Object> background = new LinkedHashMap<>();
        // 只有 value，无 type
        background.put("value", "#ABCDEF");
        bgConfig.put("background", background);

        ScreenThemeConfig theme = parser.resolve("screen-1", bgConfig);

        assertEquals("color", theme.getBackground().getType());
        assertEquals("#ABCDEF", theme.getBackground().getValue());
    }

    @Test
    public void testGradientBackgroundValuePreservedAsMap() {
        Map<String, Object> bgConfig = new LinkedHashMap<>();
        Map<String, Object> gradient = new LinkedHashMap<>();
        gradient.put("angle", 90);
        gradient.put("stops", java.util.Arrays.asList(
                Map.of("color", "#1890FF", "offset", 0),
                Map.of("color", "#131A2E", "offset", 100)));
        Map<String, Object> background = new LinkedHashMap<>();
        background.put("type", "gradient");
        background.put("value", gradient);
        bgConfig.put("background", background);

        ScreenThemeConfig theme = parser.resolve("screen-1", bgConfig);

        assertEquals("gradient", theme.getBackground().getType());
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) theme.getBackground().getValue();
        assertEquals(90, ((Number) value.get("angle")).intValue());
    }

    // ==================== 非法结构显式报错（非静默降级） ====================

    @Test
    public void testPaletteNotObjectThrows() {
        Map<String, Object> bgConfig = new LinkedHashMap<>();
        bgConfig.put("palette", "#not-an-object");

        NopException ex = assertThrows(NopException.class,
                () -> parser.resolve("screen-bad", bgConfig));
        assertEquals(ERR_DATAV_INVALID_THEME_CONFIG.getErrorCode(), ex.getErrorCode());
        assertEquals("palette", ex.getParam("themeField"));
    }

    @Test
    public void testBackgroundNotObjectThrows() {
        Map<String, Object> bgConfig = new LinkedHashMap<>();
        bgConfig.put("background", "not-an-object");

        NopException ex = assertThrows(NopException.class,
                () -> parser.resolve("screen-bad", bgConfig));
        assertEquals(ERR_DATAV_INVALID_THEME_CONFIG.getErrorCode(), ex.getErrorCode());
        assertEquals("background", ex.getParam("themeField"));
    }

    @Test
    public void testPaletteAsArrayThrows() {
        Map<String, Object> bgConfig = new LinkedHashMap<>();
        bgConfig.put("palette", java.util.Arrays.asList("primary"));

        NopException ex = assertThrows(NopException.class,
                () -> parser.resolve("screen-bad", bgConfig));
        assertEquals(ERR_DATAV_INVALID_THEME_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    // ==================== widget 主题命名引用解析 ====================

    @Test
    public void testWidgetThemeNamedReferenceResolved() {
        Map<String, String> palette = new LinkedHashMap<>();
        palette.put("primary", "#FF0000");
        palette.put("background", "#000000");

        Map<String, Object> widgetConfig = new LinkedHashMap<>();
        Map<String, Object> theme = new LinkedHashMap<>();
        theme.put("color", "primary");
        theme.put("backgroundColor", "background");
        theme.put("fontSize", 14); // 非字符串，原样保留
        theme.put("borderColor", "non-existent-name"); // 未知名，原样保留
        widgetConfig.put("theme", theme);
        widgetConfig.put("styleOptions", Map.of("color", "primary")); // styleOptions 不应被本方法处理

        Map<String, Object> resolved = parser.resolveWidgetTheme(widgetConfig, palette);

        assertNotNull(resolved);
        assertEquals("#FF0000", resolved.get("color"));
        assertEquals("#000000", resolved.get("backgroundColor"));
        assertEquals(14, resolved.get("fontSize"));
        assertEquals("non-existent-name", resolved.get("borderColor"));
    }

    @Test
    public void testWidgetThemeNullWhenNoThemeKey() {
        Map<String, Object> widgetConfig = new LinkedHashMap<>();
        widgetConfig.put("styleOptions", Map.of("color", "primary"));

        Map<String, Object> resolved = parser.resolveWidgetTheme(widgetConfig, Map.of("primary", "#FF0000"));

        assertNull(resolved);
    }

    @Test
    public void testWidgetThemeNullWhenWidgetConfigNull() {
        Map<String, Object> resolved = parser.resolveWidgetTheme(null, Map.of("primary", "#FF0000"));
        assertNull(resolved);
    }

    @Test
    public void testWidgetThemeNotMapReturnsNull() {
        Map<String, Object> widgetConfig = new LinkedHashMap<>();
        widgetConfig.put("theme", "primary"); // theme 非 Map

        Map<String, Object> resolved = parser.resolveWidgetTheme(widgetConfig, Map.of("primary", "#FF0000"));
        assertNull(resolved);
    }

    @Test
    public void testWidgetConfigNotMutatedByResolve() {
        Map<String, String> palette = Map.of("primary", "#FF0000");
        Map<String, Object> theme = new LinkedHashMap<>();
        theme.put("color", "primary");
        Map<String, Object> widgetConfig = new LinkedHashMap<>();
        widgetConfig.put("theme", theme);

        parser.resolveWidgetTheme(widgetConfig, palette);

        // widgetConfig.theme 原始命名引用未被改写（styleOptions 也不被自动改写）
        assertEquals("primary", theme.get("color"));
    }

    // ==================== Helpers ====================

    private void assertDefaultPalette(ScreenThemeConfig theme) {
        assertNotNull(theme.getPalette());
        assertEquals("#1890FF", theme.getPalette().get("primary"));
        assertEquals("#13C2C2", theme.getPalette().get("secondary"));
        assertEquals("#722ED1", theme.getPalette().get("accent"));
        assertEquals("#52C41A", theme.getPalette().get("success"));
        assertEquals("#FAAD14", theme.getPalette().get("warning"));
        assertEquals("#F5222D", theme.getPalette().get("danger"));
        assertEquals("#1890FF", theme.getPalette().get("info"));
        assertEquals("#FFFFFF", theme.getPalette().get("text"));
        assertEquals("#BFBFBF", theme.getPalette().get("textSecondary"));
        assertEquals("#131A2E", theme.getPalette().get("background"));
    }
}
