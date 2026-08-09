package io.nop.datav.service.filter;

/**
 * 看板全局筛选参数定义。由 {@link DashboardParamParser} 从 Dashboard 的 {@code paramConfig} JSON 解析得到。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §一 参数定义结构约定。</p>
 *
 * <p>每个参数定义包含：name（唯一标识）、type（string/number/date/date-range）、
 * defaultValue（缺省 null）、label（显示文案，供前端）、widget（前端控件类型，供前端）。</p>
 */
public final class DashboardParamDefinition {

    public static final String TYPE_STRING = "string";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_DATE = "date";
    public static final String TYPE_DATE_RANGE = "date-range";

    private final String name;
    private final String type;
    private final Object defaultValue;
    private final String label;
    private final String widget;

    public DashboardParamDefinition(String name, String type, Object defaultValue, String label, String widget) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.label = label;
        this.widget = widget;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public String getLabel() {
        return label;
    }

    public String getWidget() {
        return widget;
    }

    /**
     * 是否复合类型（date-range，有 start/end 子键）。
     */
    public boolean isComposite() {
        return TYPE_DATE_RANGE.equals(type);
    }
}
