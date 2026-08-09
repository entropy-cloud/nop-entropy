package io.nop.datav.service.screen;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.datav.biz.ScreenLayoutConfig;
import io.nop.datav.dao.entity.NopDatavScreenSnapshot;
import io.nop.datav.service.component.PanelComponentRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_SCREEN_LAYOUT;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SCREEN_WIDGET_UNKNOWN_COMPONENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    // ==================== Helpers ====================

    private NopDatavScreenSnapshot newSnapshot(int width, int height, int adaptorMode,
                                                Map<String, Object>... widgets) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("screenWidth", width);
        content.put("screenHeight", height);
        content.put("adaptorMode", adaptorMode);
        content.put("screenName", "test-screen");
        content.put("displayName", "Test Screen");
        content.put("backgroundConfig", Map.of("color", "#000"));
        content.put("widgets", java.util.Arrays.asList(widgets));

        NopDatavScreenSnapshot snapshot = new NopDatavScreenSnapshot();
        snapshot.setSnapshotVersion(1L);
        snapshot.setSnapshotContent(JsonTool.stringify(content));
        return snapshot;
    }

    private Map<String, Object> widget(String id, String componentType, int x, int y, int w, int h,
                                        int z, String datasetRefId) {
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
        map.put("widgetConfig", Map.of("option", Map.of("title", id)));
        return map;
    }
}
