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

    /**
     * paramConfig widget 词表（封闭，§11.3）：词表外取值由筛选定义产出显式报错。
     */
    public static final String WIDGET_DROPDOWN = "dropdown";
    public static final String WIDGET_DATE_PICKER = "date-picker";

    /**
     * 筛选定义产出 control 词表（封闭，§11.3）：select / input-text / input-number / input-date / date-range。
     */
    public static final String CONTROL_SELECT = "select";
    public static final String CONTROL_INPUT_TEXT = "input-text";
    public static final String CONTROL_INPUT_NUMBER = "input-number";
    public static final String CONTROL_INPUT_DATE = "input-date";
    public static final String CONTROL_DATE_RANGE = "date-range";

    /**
     * date-range delimited 值形态契约（§11.4 钉死）：delimiter 与 valueFormat 两侧唯一。
     */
    public static final String DATE_RANGE_DELIMITER = ",";
    public static final String DATE_RANGE_VALUE_FORMAT = "yyyy-MM-dd";

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
