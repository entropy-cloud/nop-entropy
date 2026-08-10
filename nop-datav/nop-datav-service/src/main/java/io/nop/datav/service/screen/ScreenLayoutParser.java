package io.nop.datav.service.screen;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.datav.biz.ScreenLayoutConfig;
import io.nop.datav.biz.ScreenLayoutConfig.Adaptation;
import io.nop.datav.biz.ScreenLayoutConfig.Canvas;
import io.nop.datav.biz.ScreenLayoutConfig.Widget;
import io.nop.datav.biz.ScreenThemeConfig;
import io.nop.datav.dao.entity.NopDatavScreenSnapshot;
import io.nop.datav.service.component.PanelComponentRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ARG_CANVAS_HEIGHT;
import static io.nop.datav.service.NopDatavErrors.ARG_CANVAS_WIDTH;
import static io.nop.datav.service.NopDatavErrors.ARG_COMPONENT_TYPE;
import static io.nop.datav.service.NopDatavErrors.ARG_REASON;
import static io.nop.datav.service.NopDatavErrors.ARG_SCREEN_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_WIDGET_ID;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_SCREEN_LAYOUT;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SCREEN_WIDGET_UNKNOWN_COMPONENT;

/**
 * 大屏布局解析器。将已发布快照内容解析为 {@link ScreenLayoutConfig}，并执行运行时校验：
 *
 * <ul>
 *   <li>JSON 解析失败 → {@code ERR_DATAV_INVALID_SCREEN_LAYOUT}（不静默降级）</li>
 *   <li>画布尺寸缺失/非法 → {@code ERR_DATAV_INVALID_SCREEN_LAYOUT}</li>
 *   <li>widget 组件类型未注册 → {@code ERR_DATAV_SCREEN_WIDGET_UNKNOWN_COMPONENT}
 *       （经 {@link PanelComponentRegistry#requireComponent} 校验，rule #24）</li>
 *   <li>widget 越界（x+w &gt; canvasWidth / y+h &gt; canvasHeight / 负坐标 / 非正宽高）
 *       → {@code ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS}</li>
 *   <li>widget 重叠 → 不阻断（仅装饰层叠合法场景，见设计文档 §7.3）</li>
 * </ul>
 *
 * <p>参见 {@code ai-dev/design/nop-datav/screen-design.md} §7 widget 越界/重叠校验。</p>
 */
public final class ScreenLayoutParser {

    private final PanelComponentRegistry componentRegistry;
    private final ScreenThemeParser themeParser;

    public ScreenLayoutParser(PanelComponentRegistry componentRegistry) {
        this.componentRegistry = componentRegistry;
        this.themeParser = new ScreenThemeParser();
    }

    /**
     * 解析已发布快照内容为 {@link ScreenLayoutConfig}，含完整运行时校验。
     *
     * @param screenId   大屏 ID（用于错误上下文）
     * @param snapshot   已发布快照（snapshotContent 为 JSON 字符串）
     * @return 解析后的布局配置（含画布定义、适配基准、widget 列表）
     * @throws NopException JSON 非法 / 画布非法 / widget 越界 / 组件类型未注册
     */
    public ScreenLayoutConfig parse(String screenId, NopDatavScreenSnapshot snapshot) {
        if (snapshot == null) {
            throw new NopException(ERR_DATAV_INVALID_SCREEN_LAYOUT)
                    .param(ARG_SCREEN_ID, screenId)
                    .param(ARG_REASON, "snapshot is null");
        }

        Map<String, Object> content = parseContentJson(screenId, snapshot.getSnapshotContent());

        int canvasWidth = requireInt(screenId, content, "screenWidth");
        int canvasHeight = requireInt(screenId, content, "screenHeight");
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            throw new NopException(ERR_DATAV_INVALID_SCREEN_LAYOUT)
                    .param(ARG_SCREEN_ID, screenId)
                    .param(ARG_REASON, "canvas dimension must be positive: " + canvasWidth + "x" + canvasHeight);
        }

        int adaptorMode = requireInt(screenId, content, "adaptorMode");
        if (!ScreenAdaptorMode.isValid(adaptorMode)) {
            throw new NopException(ERR_DATAV_INVALID_SCREEN_LAYOUT)
                    .param(ARG_SCREEN_ID, screenId)
                    .param(ARG_REASON, "unknown adaptorMode: " + adaptorMode);
        }

        Object widgetsRaw = content.get("widgets");
        List<Widget> widgets = parseWidgets(screenId, widgetsRaw, canvasWidth, canvasHeight);

        // D4-3 主题解析：读取 backgroundConfig → 结构化 theme（palette 缺省值 + background type 缺省）。
        // 非破坏：Canvas.backgroundConfig 仍原样透传（见下方 setBackgroundConfig），theme 放入独立字段。
        Map<String, Object> backgroundConfig = asMap(content.get("backgroundConfig"));
        ScreenThemeConfig theme = themeParser.resolve(screenId, backgroundConfig);

        // widget 主题命名引用解析（widgetConfig.theme 命名引用 → palette 实际色值，放入 widget.resolvedTheme）
        Map<String, String> resolvedPalette = theme.getPalette();
        for (Widget widget : widgets) {
            widget.setResolvedTheme(themeParser.resolveWidgetTheme(widget.getWidgetConfig(), resolvedPalette));
        }

        ScreenLayoutConfig config = new ScreenLayoutConfig();
        config.setScreenId(screenId);
        config.setScreenName(asString(content.get("screenName")));
        config.setDisplayName(asString(content.get("displayName")));
        config.setSnapshotVersion(snapshot.getSnapshotVersion());

        Canvas canvas = new Canvas();
        canvas.setWidth(canvasWidth);
        canvas.setHeight(canvasHeight);
        canvas.setAdaptorMode(adaptorMode);
        // 既有契约：backgroundConfig 原样透传（D4-1 不变；向后兼容 legacy 自由格式）
        canvas.setBackgroundConfig(backgroundConfig);
        config.setCanvas(canvas);

        Adaptation adaptation = new Adaptation();
        adaptation.setBaseWidth(canvasWidth);
        adaptation.setBaseHeight(canvasHeight);
        adaptation.setAdaptorMode(adaptorMode);
        config.setAdaptation(adaptation);

        config.setTheme(theme);
        config.setWidgets(widgets);
        return config;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseContentJson(String screenId, String snapshotContent) {
        if (snapshotContent == null || snapshotContent.isEmpty()) {
            throw new NopException(ERR_DATAV_INVALID_SCREEN_LAYOUT)
                    .param(ARG_SCREEN_ID, screenId)
                    .param(ARG_REASON, "snapshotContent is empty");
        }
        try {
            Object parsed = JsonTool.parse(snapshotContent);
            if (!(parsed instanceof Map)) {
                throw new NopException(ERR_DATAV_INVALID_SCREEN_LAYOUT)
                        .param(ARG_SCREEN_ID, screenId)
                        .param(ARG_REASON, "snapshotContent root is not a JSON object");
            }
            return (Map<String, Object>) parsed;
        } catch (NopException e) {
            // Convert any JSON parse error (e.g. ERR_CORE_JSON_*) to ERR_DATAV_INVALID_SCREEN_LAYOUT
            // so callers see the screen-domain error code, not the low-level json parse code.
            if (ERR_DATAV_INVALID_SCREEN_LAYOUT.getErrorCode().equals(e.getErrorCode())) {
                throw e;
            }
            throw new NopException(ERR_DATAV_INVALID_SCREEN_LAYOUT)
                    .param(ARG_SCREEN_ID, screenId)
                    .param(ARG_REASON, "JSON parse failed: " + e.getMessage());
        } catch (Exception e) {
            throw new NopException(ERR_DATAV_INVALID_SCREEN_LAYOUT)
                    .param(ARG_SCREEN_ID, screenId)
                    .param(ARG_REASON, "JSON parse failed: " + e.getMessage());
        }
    }

    private int requireInt(String screenId, Map<String, Object> content, String key) {
        Object value = content.get(key);
        if (value == null) {
            throw new NopException(ERR_DATAV_INVALID_SCREEN_LAYOUT)
                    .param(ARG_SCREEN_ID, screenId)
                    .param(ARG_REASON, "missing required field: " + key);
        }
        try {
            return ((Number) value).intValue();
        } catch (ClassCastException e) {
            throw new NopException(ERR_DATAV_INVALID_SCREEN_LAYOUT)
                    .param(ARG_SCREEN_ID, screenId)
                    .param(ARG_REASON, "field " + key + " is not numeric: " + value);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Widget> parseWidgets(String screenId, Object widgetsRaw, int canvasWidth, int canvasHeight) {
        if (widgetsRaw == null) {
            return new ArrayList<>();
        }
        if (!(widgetsRaw instanceof List)) {
            throw new NopException(ERR_DATAV_INVALID_SCREEN_LAYOUT)
                    .param(ARG_SCREEN_ID, screenId)
                    .param(ARG_REASON, "widgets is not a JSON array");
        }
        List<?> widgetList = (List<?>) widgetsRaw;
        List<Widget> result = new ArrayList<>(widgetList.size());
        for (int i = 0; i < widgetList.size(); i++) {
            Object item = widgetList.get(i);
            if (!(item instanceof Map)) {
                throw new NopException(ERR_DATAV_INVALID_SCREEN_LAYOUT)
                        .param(ARG_SCREEN_ID, screenId)
                        .param(ARG_REASON, "widget[" + i + "] is not a JSON object");
            }
            result.add(parseWidget(screenId, (Map<String, Object>) item, canvasWidth, canvasHeight));
        }
        return result;
    }

    private Widget parseWidget(String screenId, Map<String, Object> map, int canvasWidth, int canvasHeight) {
        String widgetId = asString(map.get("widgetId"));
        String componentType = asString(map.get("componentType"));
        if (componentType == null || componentType.isEmpty()) {
            throw new NopException(ERR_DATAV_INVALID_SCREEN_LAYOUT)
                    .param(ARG_SCREEN_ID, screenId)
                    .param(ARG_REASON, "widget missing componentType: " + widgetId);
        }

        // 接线验证：经 PanelComponentRegistry.requireComponent 校验未知组件类型（rule #24 无静默跳过）
        componentRegistry.requireComponent(componentType);

        int x = requireWidgetInt(screenId, map, "x", widgetId);
        int y = requireWidgetInt(screenId, map, "y", widgetId);
        int w = requireWidgetInt(screenId, map, "w", widgetId);
        int h = requireWidgetInt(screenId, map, "h", widgetId);
        int z = map.get("z") == null ? 0 : requireWidgetInt(screenId, map, "z", widgetId);

        if (x < 0 || y < 0 || w <= 0 || h <= 0) {
            throw new NopException(ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS)
                    .param(ARG_WIDGET_ID, widgetId)
                    .param(ARG_CANVAS_WIDTH, canvasWidth)
                    .param(ARG_CANVAS_HEIGHT, canvasHeight);
        }
        if (x + w > canvasWidth || y + h > canvasHeight) {
            throw new NopException(ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS)
                    .param(ARG_WIDGET_ID, widgetId)
                    .param(ARG_CANVAS_WIDTH, canvasWidth)
                    .param(ARG_CANVAS_HEIGHT, canvasHeight);
        }

        // 重叠不阻断（设计文档 §7.3：装饰层叠合法场景），故不做重叠校验

        Widget widget = new Widget();
        widget.setWidgetId(widgetId);
        widget.setWidgetName(asString(map.get("widgetName")));
        widget.setDisplayName(asString(map.get("displayName")));
        widget.setComponentType(componentType);
        widget.setDatasetRefId(asString(map.get("datasetRefId")));
        widget.setX(x);
        widget.setY(y);
        widget.setW(w);
        widget.setH(h);
        widget.setZ(z);
        widget.setWidgetConfig(asMap(map.get("widgetConfig")));
        return widget;
    }

    private int requireWidgetInt(String screenId, Map<String, Object> map, String key, String widgetId) {
        Object value = map.get(key);
        if (value == null) {
            throw new NopException(ERR_DATAV_INVALID_SCREEN_LAYOUT)
                    .param(ARG_SCREEN_ID, screenId)
                    .param(ARG_REASON, "widget missing field " + key + ": " + widgetId);
        }
        try {
            return ((Number) value).intValue();
        } catch (ClassCastException e) {
            throw new NopException(ERR_DATAV_INVALID_SCREEN_LAYOUT)
                    .param(ARG_SCREEN_ID, screenId)
                    .param(ARG_REASON, "widget field " + key + " is not numeric: " + widgetId);
        }
    }

    /**
     * 验证单个 widget 的组件类型（用于直接 widget 校验场景，不解析完整布局）。
     * 经 {@link PanelComponentRegistry#requireComponent} 校验，未知类型显式报错。
     */
    public void validateComponentType(String componentType, String widgetId) {
        try {
            componentRegistry.requireComponent(componentType);
        } catch (NopException e) {
            throw new NopException(ERR_DATAV_SCREEN_WIDGET_UNKNOWN_COMPONENT)
                    .param(ARG_COMPONENT_TYPE, componentType)
                    .param(ARG_WIDGET_ID, widgetId);
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        if (value instanceof String) {
            String s = (String) value;
            if (s.isEmpty()) {
                return null;
            }
            Object parsed = JsonTool.parse(s);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
        }
        return null;
    }
}
