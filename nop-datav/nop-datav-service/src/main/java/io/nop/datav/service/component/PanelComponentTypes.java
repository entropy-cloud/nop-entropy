package io.nop.datav.service.component;

/**
 * 面板组件类型标识。值稳定不变，与 panelType dict 的 int 值通过 {@link PanelTypeMapping} 映射。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/runtime-design.md} §1.2 组件类型清单。</p>
 *
 * <p>D4-2 新增 6 类装饰/媒体组件（{@code DECORATIVE_BORDER} ~ {@code CAROUSEL_TAB}），均 needsDataset=false，
 * 不经 panelType dict 映射（大屏专用，看板面板不引用）。见 {@code screen-design.md} §10。</p>
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

    // ==================== D4-2 装饰/媒体组件族（大屏专用，不经 panelType dict 映射） ====================

    /** 装饰边框（大屏视觉装饰，可叠加在 chart 上）。 */
    public static final String DECORATIVE_BORDER = "decorative-border";

    /** 滚动文字（公告/跑马灯）。 */
    public static final String SCROLL_TEXT = "scroll-text";

    /** 时间时钟（实时显示当前时间）。 */
    public static final String TIME_CLOCK = "time-clock";

    /** 视频（点播/静态视频源）。 */
    public static final String VIDEO = "video";

    /** 流媒体（直播/实时流）。 */
    public static final String STREAM = "stream";

    /** 轮询 Tab（按间隔切换显示内嵌 widget 组）。 */
    public static final String CAROUSEL_TAB = "carousel-tab";

    private PanelComponentTypes() {
    }
}
