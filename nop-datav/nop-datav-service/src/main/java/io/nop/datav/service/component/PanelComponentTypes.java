package io.nop.datav.service.component;

/**
 * 面板组件类型标识。值稳定不变，与 panelType dict 的 int 值通过 {@link PanelTypeMapping} 映射。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/runtime-design.md} §1.2 组件类型清单。</p>
 */
public final class PanelComponentTypes {
    public static final String CHART = "chart";
    public static final String PIVOT_TABLE = "pivot-table";
    public static final String STAT_TILE = "stat-tile";
    public static final String MAP = "map";
    public static final String TABLE = "table";
    public static final String TEXT = "text";
    public static final String IFRAME = "iframe";

    /**
     * 布局容器（dict CONTAINER=40），注册为无数据集绑定组件，不参与查询委托。
     */
    public static final String CONTAINER = "container";

    private PanelComponentTypes() {
    }
}
