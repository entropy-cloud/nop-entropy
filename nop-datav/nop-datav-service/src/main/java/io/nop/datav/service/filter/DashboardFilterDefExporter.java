package io.nop.datav.service.filter;

import io.nop.api.core.exceptions.NopException;
import io.nop.datav.service.NopDatavErrors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_FILTER_DEF_UNKNOWN_WIDGET;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_FILTER_DEF_WIDGET_TYPE_MISMATCH;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PARAM_CONFIG;

/**
 * flux dashboard-filter 筛选定义产出器。将看板参数定义列表转换为 flux 筛选栏可直接消费的
 * 「参数描述符数组」工件。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §十一 D2-4 对齐契约：</p>
 * <ul>
 *   <li>11.2 产出结构：{@code {dashboardId, fields[], dateRangeValue}}；fields 顺序 = paramConfig
 *       声明顺序；无参数看板产出空 fields（非报错）；不含 options（11.3 裁定）。</li>
 *   <li>11.3 widget 词表：接受集 {dropdown, date-picker}，产出 control 封闭集
 *       {select, input-text, input-number, input-date, date-range}；词表外/组合非法显式报错，
 *       不静默回退。</li>
 *   <li>11.4 date-range initialValue 为 delimited 绝对日期串（delimiter ','、格式 yyyy-MM-dd）。</li>
 * </ul>
 *
 * <p>产出侧校验 defaultValue 与声明类型/契约形态一致（定义工件不携带非法初值，rule #24）。</p>
 */
public final class DashboardFilterDefExporter {

    private DashboardFilterDefExporter() {
    }

    /**
     * 产出筛选定义工件。
     *
     * @param dashboardId 看板 ID
     * @param definitions 参数定义列表（来自 {@link DashboardParamParser#parse}），允许 null/空
     * @return 不可变的筛选定义 Map（fields 为空数组表示无参数看板）；永不为 null
     */
    public static Map<String, Object> export(String dashboardId, List<DashboardParamDefinition> definitions) {
        List<DashboardParamDefinition> defs = definitions == null ? List.of() : definitions;
        List<Map<String, Object>> fields = new ArrayList<>(defs.size());
        for (DashboardParamDefinition def : defs) {
            fields.add(buildField(def));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dashboardId", dashboardId);
        out.put("fields", Collections.unmodifiableList(fields));
        Map<String, Object> dateRangeValue = new LinkedHashMap<>();
        dateRangeValue.put("delimiter", DashboardParamDefinition.DATE_RANGE_DELIMITER);
        dateRangeValue.put("valueFormat", DashboardParamDefinition.DATE_RANGE_VALUE_FORMAT);
        out.put("dateRangeValue", Collections.unmodifiableMap(dateRangeValue));
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, Object> buildField(DashboardParamDefinition def) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", def.getName());
        field.put("label", def.getLabel() == null ? def.getName() : def.getLabel());
        field.put("type", def.getType());
        field.put("control", resolveControl(def));
        Object initial = resolveInitialValue(def);
        if (initial != null) {
            field.put("initialValue", initial);
        }
        return field;
    }

    /**
     * type + widget → control 映射（§11.3 映射表）。未声明 widget 走类型缺省；词表外取值或
     * 组合非法显式报错（不静默回退）。
     */
    static String resolveControl(DashboardParamDefinition def) {
        String type = def.getType();
        String widget = def.getWidget();
        if (widget == null) {
            if (DashboardParamDefinition.TYPE_STRING.equals(type)) {
                return DashboardParamDefinition.CONTROL_INPUT_TEXT;
            }
            if (DashboardParamDefinition.TYPE_NUMBER.equals(type)) {
                return DashboardParamDefinition.CONTROL_INPUT_NUMBER;
            }
            if (DashboardParamDefinition.TYPE_DATE.equals(type)) {
                return DashboardParamDefinition.CONTROL_INPUT_DATE;
            }
            return DashboardParamDefinition.CONTROL_DATE_RANGE;
        }
        if (DashboardParamDefinition.WIDGET_DROPDOWN.equals(widget)) {
            if (DashboardParamDefinition.TYPE_STRING.equals(type)
                    || DashboardParamDefinition.TYPE_NUMBER.equals(type)) {
                return DashboardParamDefinition.CONTROL_SELECT;
            }
            throw widgetTypeMismatch(def, "string|number");
        }
        if (DashboardParamDefinition.WIDGET_DATE_PICKER.equals(widget)) {
            if (DashboardParamDefinition.TYPE_DATE.equals(type)) {
                return DashboardParamDefinition.CONTROL_INPUT_DATE;
            }
            if (DashboardParamDefinition.TYPE_DATE_RANGE.equals(type)) {
                return DashboardParamDefinition.CONTROL_DATE_RANGE;
            }
            throw widgetTypeMismatch(def, "date|date-range");
        }
        throw new NopException(ERR_DATAV_FILTER_DEF_UNKNOWN_WIDGET)
                .param(NopDatavErrors.ARG_WIDGET, widget)
                .param(NopDatavErrors.ARG_PARAM_NAME, def.getName());
    }

    /**
     * initialValue（flux 可直接消费形态）：date-range 合并为 delimited 绝对日期串（§11.4），
     * 仅含单侧子键或缺省时省略；简单类型按类型校验后透传。
     */
    static Object resolveInitialValue(DashboardParamDefinition def) {
        Object defaultValue = def.getDefaultValue();
        if (defaultValue == null) {
            return null;
        }
        String type = def.getType();
        if (DashboardParamDefinition.TYPE_DATE_RANGE.equals(type)) {
            Map<?, ?> defaultMap = (Map<?, ?>) defaultValue;
            Object start = defaultMap.get("start");
            Object end = defaultMap.get("end");
            if (start == null || end == null) {
                // 半开默认值无法以 delimited 形态表达 → 不产出 initialValue（§11.2；后端 resolve 仍按段填充）
                return null;
            }
            if (!DashboardFilterResolver.isParsableDate(start) || !DashboardFilterResolver.isParsableDate(end)) {
                throw invalidDefaultValue(def, "date-range defaultValue start/end must be dates in '"
                        + DashboardParamDefinition.DATE_RANGE_VALUE_FORMAT + "' format");
            }
            return start.toString() + DashboardParamDefinition.DATE_RANGE_DELIMITER + end.toString();
        }
        if (DashboardParamDefinition.TYPE_NUMBER.equals(type)) {
            if (!DashboardFilterResolver.isNumber(defaultValue)) {
                throw invalidDefaultValue(def, "number defaultValue must be numeric");
            }
            return defaultValue;
        }
        if (DashboardParamDefinition.TYPE_DATE.equals(type)) {
            if (!DashboardFilterResolver.isParsableDate(defaultValue)) {
                throw invalidDefaultValue(def, "date defaultValue must be a date in '"
                        + DashboardParamDefinition.DATE_RANGE_VALUE_FORMAT + "' format");
            }
            return defaultValue;
        }
        return defaultValue;
    }

    private static NopException widgetTypeMismatch(DashboardParamDefinition def, String applicableTypes) {
        return new NopException(ERR_DATAV_FILTER_DEF_WIDGET_TYPE_MISMATCH)
                .param(NopDatavErrors.ARG_WIDGET, def.getWidget())
                .param(NopDatavErrors.ARG_EXPECTED_TYPE, applicableTypes)
                .param(NopDatavErrors.ARG_PARAM_NAME, def.getName());
    }

    private static NopException invalidDefaultValue(DashboardParamDefinition def, String reason) {
        return new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                .param(NopDatavErrors.ARG_PARAM_NAME, def.getName())
                .param(NopDatavErrors.ARG_REASON, reason);
    }
}
