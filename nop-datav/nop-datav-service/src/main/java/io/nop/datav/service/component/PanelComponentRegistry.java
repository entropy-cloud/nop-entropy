package io.nop.datav.service.component;

import io.nop.datav.service.NopDatavErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.datav.biz.PanelComponentConfigArea;
import io.nop.datav.biz.PanelComponentMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_UNKNOWN_COMPONENT_TYPE;

/**
 * 面板组件注册表。维护「类型标识 → IPanelComponent」映射，启动时静态初始化完成所有内置类型的注册。
 *
 * <p>方案见 {@code ai-dev/design/nop-datav/runtime-design.md} §1.1 组件注册表方案；
 * D4-2 装饰/媒体组件族见 {@code ai-dev/design/nop-datav/screen-design.md} §10。
 * 不使用 Nop IoC 的 {@code <bean>} 注入（注册表自包含；新增类型只需实现接口 + 在注册表内登记一行）。</p>
 *
 * <p>查询未知类型抛 {@link NopException}（{@code ERR_DATAV_UNKNOWN_COMPONENT_TYPE}），不返回 null（rule #24 无静默跳过）。</p>
 */
public final class PanelComponentRegistry {

    private static final PanelComponentRegistry INSTANCE = new PanelComponentRegistry();

    private final Map<String, IPanelComponent> components;
    private final Map<String, IPanelComponent> componentsView;

    private PanelComponentRegistry() {
        Map<String, IPanelComponent> map = new LinkedHashMap<>();
        // D1-1 既有 8 类组件（无配置区域描述符，向后兼容）
        register(map, new SimplePanelComponent(PanelComponentTypes.CHART, "Chart", true));
        register(map, new SimplePanelComponent(PanelComponentTypes.PIVOT_TABLE, "Pivot Table", true));
        register(map, new SimplePanelComponent(PanelComponentTypes.STAT_TILE, "Stat Tile", true));
        register(map, new SimplePanelComponent(PanelComponentTypes.MAP, "Map", true));
        register(map, new SimplePanelComponent(PanelComponentTypes.TABLE, "Table", true));
        register(map, new SimplePanelComponent(PanelComponentTypes.TEXT, "Text", false));
        register(map, new SimplePanelComponent(PanelComponentTypes.IFRAME, "IFrame", false));
        register(map, new SimplePanelComponent(PanelComponentTypes.CONTAINER, "Container", false));

        // D4-2 装饰/媒体组件族（6 类，均 needsDataset=false，携带配置区域描述符）
        register(map, new SimplePanelComponent(PanelComponentTypes.DECORATIVE_BORDER, "Decorative Border", false,
                decorativeBorderAreas()));
        register(map, new SimplePanelComponent(PanelComponentTypes.SCROLL_TEXT, "Scroll Text", false,
                scrollTextAreas()));
        register(map, new SimplePanelComponent(PanelComponentTypes.TIME_CLOCK, "Time Clock", false,
                timeClockAreas()));
        register(map, new SimplePanelComponent(PanelComponentTypes.VIDEO, "Video", false,
                videoAreas()));
        register(map, new SimplePanelComponent(PanelComponentTypes.STREAM, "Stream", false,
                streamAreas()));
        register(map, new SimplePanelComponent(PanelComponentTypes.CAROUSEL_TAB, "Carousel Tab", false,
                carouselTabAreas()));

        this.components = map;
        this.componentsView = Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    /**
     * 装饰边框配置区域：{@code variant}（必填）+ {@code color}。
     */
    private static List<PanelComponentConfigArea> decorativeBorderAreas() {
        return Arrays.asList(
                new PanelComponentConfigArea("variant", "Border style variant", true),
                new PanelComponentConfigArea("color", "Border color", false)
        );
    }

    /**
     * 滚动文字配置区域：{@code text}（必填）+ {@code speed} + {@code direction}。
     */
    private static List<PanelComponentConfigArea> scrollTextAreas() {
        return Arrays.asList(
                new PanelComponentConfigArea("text", "Scrolling text content", true),
                new PanelComponentConfigArea("speed", "Scroll speed", false),
                new PanelComponentConfigArea("direction", "Scroll direction", false)
        );
    }

    /**
     * 时间时钟配置区域：{@code format} + {@code timezone}。
     */
    private static List<PanelComponentConfigArea> timeClockAreas() {
        return Arrays.asList(
                new PanelComponentConfigArea("format", "Time format pattern (e.g. yyyy-MM-dd HH:mm:ss)", false),
                new PanelComponentConfigArea("timezone", "Time zone", false)
        );
    }

    /**
     * 视频配置区域：{@code src}（必填）+ {@code autoplay} + {@code loop} + {@code controls}。
     */
    private static List<PanelComponentConfigArea> videoAreas() {
        return Arrays.asList(
                new PanelComponentConfigArea("src", "Video source URL", true),
                new PanelComponentConfigArea("autoplay", "Whether to autoplay", false),
                new PanelComponentConfigArea("loop", "Whether to loop", false),
                new PanelComponentConfigArea("controls", "Whether to show controls", false)
        );
    }

    /**
     * 流媒体配置区域：{@code src}（必填）+ {@code protocol}。
     */
    private static List<PanelComponentConfigArea> streamAreas() {
        return Arrays.asList(
                new PanelComponentConfigArea("src", "Stream source URL", true),
                new PanelComponentConfigArea("protocol", "Stream protocol (e.g. hls/rtmp)", false)
        );
    }

    /**
     * 轮询 Tab 配置区域：{@code tabs}（必填）+ {@code interval}。
     */
    private static List<PanelComponentConfigArea> carouselTabAreas() {
        return Arrays.asList(
                new PanelComponentConfigArea("tabs", "Tab definitions list", true),
                new PanelComponentConfigArea("interval", "Switch interval in seconds", false)
        );
    }

    private static void register(Map<String, IPanelComponent> map, IPanelComponent component) {
        PanelComponentMeta meta = component.getMetadata();
        if (map.containsKey(meta.getType())) {
            throw new NopException(NopDatavErrors.ERR_DATAV_DUPLICATE_PANEL_COMPONENT_TYPE).param("componentType", meta.getType());
        }
        map.put(meta.getType(), component);
    }

    public static PanelComponentRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 查询指定类型标识的组件。
     *
     * @param type 组件类型标识（如 "chart" / "text" / "decorative-border"）
     * @return 对应组件实现；类型未知时抛 {@link NopException}
     */
    public IPanelComponent requireComponent(String type) {
        IPanelComponent component = components.get(type);
        if (component == null) {
            throw new NopException(ERR_DATAV_UNKNOWN_COMPONENT_TYPE)
                    .param("componentType", type);
        }
        return component;
    }

    /**
     * 判断指定类型标识是否已注册。
     */
    public boolean isRegistered(String type) {
        return components.containsKey(type);
    }

    /**
     * 返回所有已注册组件的不可变视图（类型标识 → 组件）。
     */
    public Map<String, IPanelComponent> getComponents() {
        return componentsView;
    }
}
