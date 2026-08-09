package io.nop.datav.service.component;

import io.nop.api.core.exceptions.NopException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_UNKNOWN_COMPONENT_TYPE;

/**
 * 面板组件注册表。维护「类型标识 → IPanelComponent」映射，启动时静态初始化完成所有内置类型的注册。
 *
 * <p>方案见 {@code ai-dev/design/nop-datav/runtime-design.md} §1.1 组件注册表方案。
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
        register(map, new SimplePanelComponent(PanelComponentTypes.CHART, "Chart", true));
        register(map, new SimplePanelComponent(PanelComponentTypes.PIVOT_TABLE, "Pivot Table", true));
        register(map, new SimplePanelComponent(PanelComponentTypes.STAT_TILE, "Stat Tile", true));
        register(map, new SimplePanelComponent(PanelComponentTypes.MAP, "Map", true));
        register(map, new SimplePanelComponent(PanelComponentTypes.TABLE, "Table", true));
        register(map, new SimplePanelComponent(PanelComponentTypes.TEXT, "Text", false));
        register(map, new SimplePanelComponent(PanelComponentTypes.IFRAME, "IFrame", false));
        register(map, new SimplePanelComponent(PanelComponentTypes.CONTAINER, "Container", false));
        this.components = map;
        this.componentsView = Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    private static void register(Map<String, IPanelComponent> map, IPanelComponent component) {
        PanelComponentMeta meta = component.getMetadata();
        if (map.containsKey(meta.getType())) {
            throw new IllegalStateException("Duplicate panel component type: " + meta.getType());
        }
        map.put(meta.getType(), component);
    }

    public static PanelComponentRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 查询指定类型标识的组件。
     *
     * @param type 组件类型标识（如 "chart" / "text"）
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
