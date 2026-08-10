package io.nop.datav.service.screen;

import io.nop.api.core.exceptions.NopException;
import io.nop.datav.biz.ScreenThemeConfig;
import io.nop.datav.biz.ScreenThemeConfig.Background;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ARG_REASON;
import static io.nop.datav.service.NopDatavErrors.ARG_SCREEN_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_THEME_FIELD;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_THEME_CONFIG;

/**
 * 大屏主题解析器（D4-3）。将大屏 {@code backgroundConfig} 解析为结构化 {@link ScreenThemeConfig}
 * （命名色板 palette + 背景定义 background），并解析 widget 主题命名引用。
 *
 * <p><b>向后兼容裁定</b>（见 {@code ai-dev/design/nop-datav/screen-design.md} §11.2）：</p>
 * <ul>
 *   <li>backgroundConfig 缺省/空 → 缺省 palette + 缺省 background（不报错）</li>
 *   <li>backgroundConfig 不含 {@code palette}/{@code background} 键（legacy 自由格式）
 *       → 缺省 palette + 缺省 background，<b>不报错</b>（既有测试不回归）</li>
 *   <li>backgroundConfig 含 {@code palette} 或 {@code background} 键但值结构非法
 *       → 抛 {@code ERR_DATAV_INVALID_THEME_CONFIG}（非静默降级）</li>
 * </ul>
 *
 * <p>本解析器不修改传入的 backgroundConfig / widgetConfig（原始配置透传由调用方负责）。</p>
 */
public final class ScreenThemeParser {

    /** palette 命名色清单 + 缺省值（见设计文档 §11.3）。 */
    private static final Map<String, String> DEFAULT_PALETTE;

    /** background type 缺省值。 */
    public static final String DEFAULT_BACKGROUND_TYPE = "color";

    static {
        DEFAULT_PALETTE = new LinkedHashMap<>();
        DEFAULT_PALETTE.put("primary", "#1890FF");
        DEFAULT_PALETTE.put("secondary", "#13C2C2");
        DEFAULT_PALETTE.put("accent", "#722ED1");
        DEFAULT_PALETTE.put("success", "#52C41A");
        DEFAULT_PALETTE.put("warning", "#FAAD14");
        DEFAULT_PALETTE.put("danger", "#F5222D");
        DEFAULT_PALETTE.put("info", "#1890FF");
        DEFAULT_PALETTE.put("text", "#FFFFFF");
        DEFAULT_PALETTE.put("textSecondary", "#BFBFBF");
        DEFAULT_PALETTE.put("background", "#131A2E");
    }

    /**
     * 解析大屏 backgroundConfig 为结构化主题。
     *
     * <p>解析规则见类注释（向后兼容裁定）。产出非 null 的 {@link ScreenThemeConfig}：
     * palette 缺省值已填充、background type/value 缺省已应用。</p>
     *
     * @param screenId          大屏 ID（用于错误上下文）
     * @param backgroundConfig  原始 backgroundConfig（可能为 null/空/legacy 自由格式/结构化主题）
     * @return 解析后的结构化主题（非 null）
     * @throws NopException backgroundConfig 含 palette/background 键但值结构非法时
     */
    public ScreenThemeConfig resolve(String screenId, Map<String, Object> backgroundConfig) {
        // 缺省/空 或 legacy 自由格式 → 缺省 theme（不报错）
        if (backgroundConfig == null || backgroundConfig.isEmpty()
                || (!backgroundConfig.containsKey("palette") && !backgroundConfig.containsKey("background"))) {
            return defaultTheme();
        }

        // 含 palette/background 键 → 按结构化解析（值非法显式报错）
        Map<String, String> palette = resolvePalette(screenId, backgroundConfig.get("palette"));
        Background background = resolveBackground(screenId, backgroundConfig.get("background"), palette);

        ScreenThemeConfig theme = new ScreenThemeConfig();
        theme.setPalette(palette);
        theme.setBackground(background);
        return theme;
    }

    /**
     * 解析 widget 主题命名引用。将 {@code widgetConfig.theme} 中的命名引用（如 {@code "primary"}）
     * 解析为 palette 实际色值，产出 resolved theme Map。
     *
     * <p>{@code widgetConfig} 本身不被改写；styleOptions（D1-1）不被自动回退（见设计文档 §11.5）。
     * 仅 {@code widgetConfig.theme} 区域的 String 型命名引用被解析。</p>
     *
     * @param widgetConfig  widget 原始配置（不被修改）
     * @param screenPalette 屏幕级已解析 palette（name → hex）
     * @return resolved theme Map（name → 解析后值）；widgetConfig 无 theme 区域时返回 null
     */
    public Map<String, Object> resolveWidgetTheme(Map<String, Object> widgetConfig, Map<String, String> screenPalette) {
        if (widgetConfig == null) {
            return null;
        }
        Object themeRaw = widgetConfig.get("theme");
        if (!(themeRaw instanceof Map)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> themeMap = (Map<String, Object>) themeRaw;
        Map<String, Object> resolved = new LinkedHashMap<>(themeMap.size());
        for (Map.Entry<String, Object> entry : themeMap.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String color = screenPalette.get(value);
                resolved.put(entry.getKey(), color != null ? color : value);
            } else {
                resolved.put(entry.getKey(), value);
            }
        }
        return resolved;
    }

    private Map<String, String> resolvePalette(String screenId, Object paletteRaw) {
        // palette 缺省 → 全部默认
        if (paletteRaw == null) {
            return new LinkedHashMap<>(DEFAULT_PALETTE);
        }
        if (!(paletteRaw instanceof Map)) {
            throw new NopException(ERR_DATAV_INVALID_THEME_CONFIG)
                    .param(ARG_SCREEN_ID, screenId)
                    .param(ARG_THEME_FIELD, "palette")
                    .param(ARG_REASON, "palette must be a JSON object");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> userPalette = (Map<String, Object>) paletteRaw;
        // 以默认 palette 为基底，用户指定的覆盖；未指定的保留默认（非 null）
        Map<String, String> result = new LinkedHashMap<>(DEFAULT_PALETTE);
        for (Map.Entry<String, Object> entry : userPalette.entrySet()) {
            Object value = entry.getValue();
            result.put(entry.getKey(), value == null ? null : value.toString());
        }
        return result;
    }

    private Background resolveBackground(String screenId, Object backgroundRaw, Map<String, String> palette) {
        Background background = new Background();

        // background 缺省 → type=color，value 取 palette.background
        if (backgroundRaw == null) {
            background.setType(DEFAULT_BACKGROUND_TYPE);
            background.setValue(palette.get("background"));
            return background;
        }
        if (!(backgroundRaw instanceof Map)) {
            throw new NopException(ERR_DATAV_INVALID_THEME_CONFIG)
                    .param(ARG_SCREEN_ID, screenId)
                    .param(ARG_THEME_FIELD, "background")
                    .param(ARG_REASON, "background must be a JSON object");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> bgMap = (Map<String, Object>) backgroundRaw;

        Object typeRaw = bgMap.get("type");
        String type = typeRaw == null ? DEFAULT_BACKGROUND_TYPE : typeRaw.toString();
        background.setType(type);

        Object value = bgMap.get("value");
        if (value == null) {
            // value 缺省：color 类型取 palette.background；其他类型无统一缺省，置 null（向前兼容）
            background.setValue(DEFAULT_BACKGROUND_TYPE.equals(type) ? palette.get("background") : null);
        } else {
            background.setValue(value);
        }
        return background;
    }

    private ScreenThemeConfig defaultTheme() {
        ScreenThemeConfig theme = new ScreenThemeConfig();
        theme.setPalette(new LinkedHashMap<>(DEFAULT_PALETTE));
        Background background = new Background();
        background.setType(DEFAULT_BACKGROUND_TYPE);
        background.setValue(DEFAULT_PALETTE.get("background"));
        theme.setBackground(background);
        return theme;
    }
}
