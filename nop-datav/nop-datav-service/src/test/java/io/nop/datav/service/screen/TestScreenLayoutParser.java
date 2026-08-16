package io.nop.datav.service.screen;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.datav.biz.ScreenLayoutConfig;
import io.nop.datav.biz.ScreenThemeConfig;
import io.nop.datav.dao.entity.NopDatavScreenSnapshot;
import io.nop.datav.service.component.PanelComponentRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_SCREEN_LAYOUT;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_THEME_CONFIG;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SCREEN_WIDGET_UNKNOWN_COMPONENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 单元测试 {@link ScreenLayoutParser}。覆盖：
 *
 * <ul>
 *   <li>合法画布 JSON 解析正确（含画布尺寸/适配模式/widget 定位/组件类型/datasetRefId）</li>
 *   <li>非法 JSON → {@code ERR_DATAV_INVALID_SCREEN_LAYOUT}（不静默降级）</li>
 *   <li>widget 引用未知组件类型 → {@code ERR_DATAV_SCREEN_WIDGET_UNKNOWN_COMPONENT}（经 PanelComponentRegistry.requireComponent）</li>
 *   <li>widget 越界（x+w &gt; canvasWidth 等）→ {@code ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS}</li>
 *   <li>三种适配模式（heightFirst/full/keep）解析输出可区分</li>
 * </ul>
 *
 * <p>纯单元测试，不经 IoC；直接构造 parser 与内存快照实体。</p>
 */
public class TestScreenLayoutParser {

    private final ScreenLayoutParser parser = new ScreenLayoutParser(PanelComponentRegistry.getInstance());

    @Test
    public void testParseValidLayout() {
        NopDatavScreenSnapshot snapshot = newSnapshot(1920, 1080, ScreenAdaptorMode.FULL,
                widget("w1", "chart", 0, 0, 600, 400, 0, "ds-ref-1"));

        ScreenLayoutConfig config = parser.parse("screen-1", snapshot);

        assertEquals("screen-1", config.getScreenId());
        assertEquals(1L, config.getSnapshotVersion());
        assertNotNull(config.getCanvas());
        assertEquals(1920, config.getCanvas().getWidth());
        assertEquals(1080, config.getCanvas().getHeight());
        assertEquals(ScreenAdaptorMode.FULL, config.getCanvas().getAdaptorMode());
        assertEquals(1, config.getWidgets().size());

        ScreenLayoutConfig.Widget w = config.getWidgets().get(0);
        assertEquals("w1", w.getWidgetId());
        assertEquals("chart", w.getComponentType());
        assertEquals("ds-ref-1", w.getDatasetRefId());
        assertEquals(0, w.getX());
        assertEquals(0, w.getY());
        assertEquals(600, w.getW());
        assertEquals(400, w.getH());
        assertEquals(0, w.getZ());
    }

    @Test
    public void testParseInvalidJsonThrows() {
        NopDatavScreenSnapshot snapshot = new NopDatavScreenSnapshot();
        snapshot.setSnapshotVersion(1L);
        snapshot.setSnapshotContent("{ this is not valid json");

        NopException ex = assertThrows(NopException.class,
                () -> parser.parse("screen-bad-json", snapshot));
        assertEquals(ERR_DATAV_INVALID_SCREEN_LAYOUT.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseEmptyContentThrows() {
        NopDatavScreenSnapshot snapshot = new NopDatavScreenSnapshot();
        snapshot.setSnapshotVersion(1L);
        snapshot.setSnapshotContent("");

        NopException ex = assertThrows(NopException.class,
                () -> parser.parse("screen-empty", snapshot));
        assertEquals(ERR_DATAV_INVALID_SCREEN_LAYOUT.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseMissingCanvasDimensionThrows() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("screenWidth", 1920);
        // missing screenHeight
        content.put("adaptorMode", ScreenAdaptorMode.FULL);
        content.put("widgets", java.util.Collections.emptyList());

        NopDatavScreenSnapshot snapshot = new NopDatavScreenSnapshot();
        snapshot.setSnapshotVersion(1L);
        snapshot.setSnapshotContent(JsonTool.stringify(content));

        NopException ex = assertThrows(NopException.class,
                () -> parser.parse("screen-no-height", snapshot));
        assertEquals(ERR_DATAV_INVALID_SCREEN_LAYOUT.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseZeroCanvasDimensionThrows() {
        NopDatavScreenSnapshot snapshot = newSnapshot(0, 1080, ScreenAdaptorMode.FULL);

        NopException ex = assertThrows(NopException.class,
                () -> parser.parse("screen-zero-w", snapshot));
        assertEquals(ERR_DATAV_INVALID_SCREEN_LAYOUT.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseUnknownAdaptorModeThrows() {
        NopDatavScreenSnapshot snapshot = newSnapshot(1920, 1080, 999);

        NopException ex = assertThrows(NopException.class,
                () -> parser.parse("screen-bad-mode", snapshot));
        assertEquals(ERR_DATAV_INVALID_SCREEN_LAYOUT.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseWidgetUnknownComponentThrows() {
        NopDatavScreenSnapshot snapshot = newSnapshot(1920, 1080, ScreenAdaptorMode.FULL,
                widget("w1", "non-existent-component", 0, 0, 100, 100, 0, null));

        NopException ex = assertThrows(NopException.class,
                () -> parser.parse("screen-unknown-comp", snapshot));
        // requireComponent 直接抛 ERR_DATAV_UNKNOWN_COMPONENT_TYPE；validateComponentType 包装为 SCREEN_WIDGET_UNKNOWN_COMPONENT
        // 这里走 parse 路径，requireComponent 抛原码
        assertEquals(io.nop.datav.service.NopDatavErrors.ERR_DATAV_UNKNOWN_COMPONENT_TYPE.getErrorCode(),
                ex.getErrorCode());
    }

    @Test
    public void testValidateComponentTypeWrapsErrorCode() {
        NopException ex = assertThrows(NopException.class,
                () -> parser.validateComponentType("non-existent", "widget-1"));
        assertEquals(ERR_DATAV_SCREEN_WIDGET_UNKNOWN_COMPONENT.getErrorCode(), ex.getErrorCode());
        assertEquals("non-existent", ex.getParam("componentType"));
        assertEquals("widget-1", ex.getParam("widgetId"));
    }

    @Test
    public void testParseWidgetOutOfBoundsXPlusW() {
        // canvas 1000x1000, widget x=800 w=300 → x+w=1100 > 1000
        NopDatavScreenSnapshot snapshot = newSnapshot(1000, 1000, ScreenAdaptorMode.FULL,
                widget("w-oob-x", "chart", 800, 0, 300, 100, 0, null));

        NopException ex = assertThrows(NopException.class,
                () -> parser.parse("screen-oob-x", snapshot));
        assertEquals(ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS.getErrorCode(), ex.getErrorCode());
        assertEquals("w-oob-x", ex.getParam("widgetId"));
    }

    @Test
    public void testParseWidgetOutOfBoundsYPlusH() {
        NopDatavScreenSnapshot snapshot = newSnapshot(1000, 1000, ScreenAdaptorMode.FULL,
                widget("w-oob-y", "chart", 0, 900, 100, 200, 0, null));

        NopException ex = assertThrows(NopException.class,
                () -> parser.parse("screen-oob-y", snapshot));
        assertEquals(ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseWidgetNegativeXYThrows() {
        NopDatavScreenSnapshot snapshot = newSnapshot(1000, 1000, ScreenAdaptorMode.FULL,
                widget("w-neg", "chart", -10, 0, 100, 100, 0, null));

        NopException ex = assertThrows(NopException.class,
                () -> parser.parse("screen-neg-x", snapshot));
        assertEquals(ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseWidgetZeroWThrows() {
        NopDatavScreenSnapshot snapshot = newSnapshot(1000, 1000, ScreenAdaptorMode.FULL,
                widget("w-zero-w", "chart", 0, 0, 0, 100, 0, null));

        NopException ex = assertThrows(NopException.class,
                () -> parser.parse("screen-zero-w", snapshot));
        assertEquals(ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseWidgetEdgeBoundaryAllowed() {
        // x+w == canvasWidth is allowed (not strictly less-than)
        NopDatavScreenSnapshot snapshot = newSnapshot(1000, 1000, ScreenAdaptorMode.FULL,
                widget("w-edge", "chart", 900, 900, 100, 100, 0, null));

        ScreenLayoutConfig config = parser.parse("screen-edge", snapshot);
        assertEquals(1, config.getWidgets().size());
        assertEquals("w-edge", config.getWidgets().get(0).getWidgetId());
    }

    @Test
    public void testParseThreeAdaptorModesDistinguishable() {
        // heightFirst
        ScreenLayoutConfig hFirst = parser.parse("screen-hf",
                newSnapshot(1920, 1080, ScreenAdaptorMode.HEIGHT_FIRST));
        assertEquals(ScreenAdaptorMode.HEIGHT_FIRST, hFirst.getCanvas().getAdaptorMode());
        assertEquals(ScreenAdaptorMode.HEIGHT_FIRST, hFirst.getAdaptation().getAdaptorMode());

        // full
        ScreenLayoutConfig full = parser.parse("screen-full",
                newSnapshot(1920, 1080, ScreenAdaptorMode.FULL));
        assertEquals(ScreenAdaptorMode.FULL, full.getCanvas().getAdaptorMode());
        assertEquals(ScreenAdaptorMode.FULL, full.getAdaptation().getAdaptorMode());

        // keep
        ScreenLayoutConfig keep = parser.parse("screen-keep",
                newSnapshot(1920, 1080, ScreenAdaptorMode.KEEP));
        assertEquals(ScreenAdaptorMode.KEEP, keep.getCanvas().getAdaptorMode());
        assertEquals(ScreenAdaptorMode.KEEP, keep.getAdaptation().getAdaptorMode());

        // Three are mutually distinct
        assertEquals(3, java.util.stream.Stream.of(
                        hFirst.getCanvas().getAdaptorMode(),
                        full.getCanvas().getAdaptorMode(),
                        keep.getCanvas().getAdaptorMode())
                .distinct().count());
    }

    @Test
    public void testParseAdaptationMirrorsCanvas() {
        NopDatavScreenSnapshot snapshot = newSnapshot(2560, 1440, ScreenAdaptorMode.HEIGHT_FIRST);

        ScreenLayoutConfig config = parser.parse("screen-mirror", snapshot);
        assertEquals(config.getCanvas().getWidth(), config.getAdaptation().getBaseWidth());
        assertEquals(config.getCanvas().getHeight(), config.getAdaptation().getBaseHeight());
        assertEquals(config.getCanvas().getAdaptorMode(), config.getAdaptation().getAdaptorMode());
    }

    @Test
    public void testParseWidgetsNotArrayThrows() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("screenWidth", 1920);
        content.put("screenHeight", 1080);
        content.put("adaptorMode", ScreenAdaptorMode.FULL);
        content.put("widgets", "not-an-array");

        NopDatavScreenSnapshot snapshot = new NopDatavScreenSnapshot();
        snapshot.setSnapshotVersion(1L);
        snapshot.setSnapshotContent(JsonTool.stringify(content));

        NopException ex = assertThrows(NopException.class,
                () -> parser.parse("screen-bad-widgets", snapshot));
        assertEquals(ERR_DATAV_INVALID_SCREEN_LAYOUT.getErrorCode(), ex.getErrorCode());
    }

    // ==================== D4-3 主题解析接线 ====================

    /**
     * 接线验证：parse 接入主题解析——结构化 backgroundConfig → theme 字段填充（palette 缺省 + background 缺省）。
     * 同时验证 Canvas.backgroundConfig 原样透传（非破坏）。
     */
    @Test
    public void testParseResolvesThemeFromStructuredBackgroundConfig() {
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put("primary", "#FF0000");
        Map<String, Object> background = new LinkedHashMap<>();
        background.put("type", "image");
        background.put("value", "https://example.com/bg.png");
        Map<String, Object> backgroundConfig = new LinkedHashMap<>();
        backgroundConfig.put("palette", palette);
        backgroundConfig.put("background", background);

        NopDatavScreenSnapshot snapshot = newSnapshotWithBackgroundConfig(1920, 1080,
                ScreenAdaptorMode.FULL, backgroundConfig);

        ScreenLayoutConfig config = parser.parse("screen-theme", snapshot);

        // theme 字段已填充
        ScreenThemeConfig theme = config.getTheme();
        assertNotNull(theme);
        assertEquals("#FF0000", theme.getPalette().get("primary"));
        // 未指定的命名色回退缺省
        assertEquals("#13C2C2", theme.getPalette().get("secondary"));
        assertEquals("image", theme.getBackground().getType());
        assertEquals("https://example.com/bg.png", theme.getBackground().getValue());

        // Canvas.backgroundConfig 原样透传（非破坏）
        assertNotNull(config.getCanvas().getBackgroundConfig());
        @SuppressWarnings("unchecked")
        Map<String, Object> passedThrough = (Map<String, Object>) config.getCanvas().getBackgroundConfig().get("palette");
        assertEquals("#FF0000", passedThrough.get("primary"));
    }

    /**
     * 向后兼容验证：legacy 自由格式 backgroundConfig（{"color":"#000"}）→ theme 用缺省，不报错，
     * Canvas.backgroundConfig 原样透传。
     */
    @Test
    public void testParseLegacyBackgroundConfigUsesDefaultTheme() {
        NopDatavScreenSnapshot snapshot = newSnapshot(1920, 1080, ScreenAdaptorMode.FULL);

        ScreenLayoutConfig config = parser.parse("screen-legacy", snapshot);

        // theme 用缺省
        ScreenThemeConfig theme = config.getTheme();
        assertNotNull(theme);
        assertEquals("#1890FF", theme.getPalette().get("primary"));
        assertEquals("color", theme.getBackground().getType());
        assertEquals("#131A2E", theme.getBackground().getValue());

        // Canvas.backgroundConfig 原样透传（既有断言不回归）
        assertNotNull(config.getCanvas().getBackgroundConfig());
        assertEquals("#000", config.getCanvas().getBackgroundConfig().get("color"));
    }

    /**
     * 接线验证：widget.widgetConfig.theme 命名引用 → widget.resolvedTheme 解析为 palette 实际色值；
     * widgetConfig 原样透传（不被改写）。
     */
    @Test
    public void testParseResolvesWidgetThemeNamedReferences() {
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put("primary", "#FF0000");
        palette.put("background", "#000000");
        Map<String, Object> backgroundConfig = new LinkedHashMap<>();
        backgroundConfig.put("palette", palette);

        // widget with widgetConfig.theme 命名引用 + styleOptions
        Map<String, Object> widgetTheme = new LinkedHashMap<>();
        widgetTheme.put("color", "primary");
        widgetTheme.put("backgroundColor", "background");
        Map<String, Object> widgetConfig = new LinkedHashMap<>();
        widgetConfig.put("theme", widgetTheme);
        widgetConfig.put("styleOptions", Map.of("color", "primary"));

        NopDatavScreenSnapshot snapshot = newSnapshotWithWidgets(1920, 1080, ScreenAdaptorMode.FULL,
                backgroundConfig, widgetWithConfig("w-theme", "chart", 0, 0, 100, 100, 0, widgetConfig));

        ScreenLayoutConfig config = parser.parse("screen-widget-theme", snapshot);

        assertEquals(1, config.getWidgets().size());
        ScreenLayoutConfig.Widget w = config.getWidgets().get(0);

        // resolvedTheme 命名引用已解析为实际色值
        assertNotNull(w.getResolvedTheme());
        assertEquals("#FF0000", w.getResolvedTheme().get("color"));
        assertEquals("#000000", w.getResolvedTheme().get("backgroundColor"));

        // widgetConfig 原样透传（theme 区仍是命名引用 "primary"，styleOptions 不被改写）
        assertNotNull(w.getWidgetConfig());
        @SuppressWarnings("unchecked")
        Map<String, Object> passedTheme = (Map<String, Object>) w.getWidgetConfig().get("theme");
        assertEquals("primary", passedTheme.get("color"));
        assertEquals("primary", ((Map<?, ?>) w.getWidgetConfig().get("styleOptions")).get("color"));
    }

    /**
     * 接线验证：widget 无 theme 区域 → resolvedTheme 为 null（不报错）。
     */
    @Test
    public void testParseWidgetWithoutThemeHasNullResolvedTheme() {
        NopDatavScreenSnapshot snapshot = newSnapshot(1920, 1080, ScreenAdaptorMode.FULL,
                widget("w-no-theme", "chart", 0, 0, 100, 100, 0, null));

        ScreenLayoutConfig config = parser.parse("screen-no-theme", snapshot);

        assertEquals(1, config.getWidgets().size());
        assertNull(config.getWidgets().get(0).getResolvedTheme());
    }

    /**
     * 非法主题结构经 parse 接线显式报错（非静默降级）。
     */
    @Test
    public void testParseInvalidThemeConfigThrows() {
        Map<String, Object> backgroundConfig = new LinkedHashMap<>();
        backgroundConfig.put("palette", "not-an-object");

        NopDatavScreenSnapshot snapshot = newSnapshotWithBackgroundConfig(1920, 1080,
                ScreenAdaptorMode.FULL, backgroundConfig);

        NopException ex = assertThrows(NopException.class,
                () -> parser.parse("screen-invalid-theme", snapshot));
        assertEquals(ERR_DATAV_INVALID_THEME_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    // ==================== D4-4 草稿预览：parse(String, String) content overload 接线验证 ====================

    /**
     * 草稿预览 overload 接线验证（rule #23）：{@link ScreenLayoutParser#parse(String, String)}
     * 直接接受内容 JSON 字符串，与 {@link ScreenLayoutParser#parse(String, NopDatavScreenSnapshot)}
     * 解析行为一致（证明草稿预览复用既有解析路径，非独立第二套构建）。
     */
    @Test
    public void testParseContentOverloadParsesSameAsSnapshotOverload() {
        NopDatavScreenSnapshot snapshot = newSnapshot(1920, 1080, ScreenAdaptorMode.FULL,
                widget("w1", "chart", 0, 0, 600, 400, 0, null));

        // snapshot overload（含 snapshotVersion）
        ScreenLayoutConfig fromSnapshot = parser.parse("screen-1", snapshot);
        assertEquals(1L, fromSnapshot.getSnapshotVersion());

        // content overload（草稿预览入口；snapshotVersion 保留默认 0）
        ScreenLayoutConfig fromContent = parser.parse("screen-1", snapshot.getSnapshotContent());
        assertEquals(0L, fromContent.getSnapshotVersion(),
                "content overload must not set snapshotVersion (drafts have no version)");

        // 解析行为一致：画布/widget 定位/组件类型均相同
        assertEquals(fromSnapshot.getCanvas().getWidth(), fromContent.getCanvas().getWidth());
        assertEquals(fromSnapshot.getCanvas().getHeight(), fromContent.getCanvas().getHeight());
        assertEquals(fromSnapshot.getWidgets().size(), fromContent.getWidgets().size());
        assertEquals(fromSnapshot.getWidgets().get(0).getWidgetId(), fromContent.getWidgets().get(0).getWidgetId());
        assertEquals(fromSnapshot.getWidgets().get(0).getComponentType(),
                fromContent.getWidgets().get(0).getComponentType());
    }

    /**
     * content overload 主题解析生效（证明 D4-3 主题解析经同一 overload 接入）。
     */
    @Test
    public void testParseContentOverloadResolvesTheme() {
        Map<String, Object> backgroundConfig = Map.of("palette", Map.of("primary", "#FF0000"));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("screenWidth", 1920);
        content.put("screenHeight", 1080);
        content.put("adaptorMode", ScreenAdaptorMode.FULL);
        content.put("backgroundConfig", backgroundConfig);
        content.put("widgets", java.util.Collections.emptyList());

        ScreenLayoutConfig config = parser.parse("screen-theme-draft", JsonTool.stringify(content));

        assertNotNull(config.getTheme());
        assertEquals("#FF0000", config.getTheme().getPalette().get("primary"));
        assertEquals("#13C2C2", config.getTheme().getPalette().get("secondary"));
    }

    /**
     * content overload 校验路径生效：未知组件类型经 requireComponent 抛错（rule #23 接线验证）。
     */
    @Test
    public void testParseContentOverloadRejectsUnknownComponent() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("screenWidth", 1920);
        content.put("screenHeight", 1080);
        content.put("adaptorMode", ScreenAdaptorMode.FULL);
        content.put("widgets", java.util.Collections.singletonList(
                widget("w-bad", "non-existent-type", 0, 0, 100, 100, 0, null)));

        NopException ex = assertThrows(NopException.class,
                () -> parser.parse("screen-unknown", JsonTool.stringify(content)));
        assertEquals(io.nop.datav.service.NopDatavErrors.ERR_DATAV_UNKNOWN_COMPONENT_TYPE.getErrorCode(),
                ex.getErrorCode());
    }

    // ==================== Helpers ====================

    private NopDatavScreenSnapshot newSnapshot(int width, int height, int adaptorMode,
                                                Map<String, Object>... widgets) {
        return newSnapshotWithWidgets(width, height, adaptorMode, Map.of("color", "#000"), widgets);
    }

    private NopDatavScreenSnapshot newSnapshotWithBackgroundConfig(int width, int height, int adaptorMode,
                                                                    Map<String, Object> backgroundConfig) {
        return newSnapshotWithWidgets(width, height, adaptorMode, backgroundConfig);
    }

    private NopDatavScreenSnapshot newSnapshotWithWidgets(int width, int height, int adaptorMode,
                                                          Map<String, Object> backgroundConfig,
                                                          Map<String, Object>... widgets) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("screenWidth", width);
        content.put("screenHeight", height);
        content.put("adaptorMode", adaptorMode);
        content.put("screenName", "test-screen");
        content.put("displayName", "Test Screen");
        content.put("backgroundConfig", backgroundConfig);
        content.put("widgets", Arrays.asList(widgets));

        NopDatavScreenSnapshot snapshot = new NopDatavScreenSnapshot();
        snapshot.setSnapshotVersion(1L);
        snapshot.setSnapshotContent(JsonTool.stringify(content));
        return snapshot;
    }

    private Map<String, Object> widget(String id, String componentType, int x, int y, int w, int h,
                                        int z, String datasetRefId) {
        return widgetWithConfig(id, componentType, x, y, w, h, z,
                Map.of("option", Map.of("title", id)), datasetRefId);
    }

    private Map<String, Object> widgetWithConfig(String id, String componentType, int x, int y, int w, int h,
                                                  int z, Map<String, Object> widgetConfig) {
        return widgetWithConfig(id, componentType, x, y, w, h, z, widgetConfig, null);
    }

    private Map<String, Object> widgetWithConfig(String id, String componentType, int x, int y, int w, int h,
                                                  int z, Map<String, Object> widgetConfig, String datasetRefId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("widgetId", id);
        map.put("widgetName", id);
        map.put("displayName", id);
        map.put("componentType", componentType);
        if (datasetRefId != null) {
            map.put("datasetRefId", datasetRefId);
        }
        map.put("x", x);
        map.put("y", y);
        map.put("w", w);
        map.put("h", h);
        map.put("z", z);
        map.put("widgetConfig", widgetConfig);
        return map;
    }
}
