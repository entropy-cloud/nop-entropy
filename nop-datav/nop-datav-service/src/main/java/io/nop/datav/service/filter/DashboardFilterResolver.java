package io.nop.datav.service.filter;

import io.nop.api.core.exceptions.NopException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PARAM_CONFIG;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_PARAM_TYPE_MISMATCH;

/**
 * 看板全局筛选值解析器。按参数定义校验类型 / 填充默认值 / 过滤未定义参数，输出扁平 key 参数值 Map。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §二 参数值表示约定、§三 全局筛选应用流程。</p>
 *
 * <p>输入与输出均使用扁平 key：简单参数 {@code paramName=value}，复合参数（date-range）
 * {@code paramName.start=v1, paramName.end=v2}。输出可直接作为 {@code getPanelData} 的 requestParams，
 * 经既有 paramMapping 求值后注入 SQL（无需中间转换层）。</p>
 *
 * <p>未定义的传入 key 被显式过滤（宽松兼容，保持 D1 向后兼容）；类型不匹配显式失败（抛
 * {@code ERR_DATAV_PARAM_TYPE_MISMATCH}），不静默强转（rule #24）。</p>
 */
public final class DashboardFilterResolver {

    static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private DashboardFilterResolver() {
    }

    /**
     * 解析筛选值。
     *
     * @param definitions 看板参数定义列表（来自 {@link DashboardParamParser#parse}）
     * @param input       原始扁平 key 筛选值 Map，允许 null/空
     * @return 不可变的扁平化生效参数值 Map（按参数定义顺序）；永不为 null
     */
    public static Map<String, Object> resolve(List<DashboardParamDefinition> definitions,
                                              Map<String, Object> input) {
        if (definitions == null || definitions.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> inputMap = input == null ? Collections.emptyMap() : input;
        Map<String, Object> result = new LinkedHashMap<>();
        for (DashboardParamDefinition def : definitions) {
            if (def.isComposite()) {
                resolveDateRange(def, inputMap, result);
            } else {
                resolveSimple(def, inputMap, result);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static void resolveSimple(DashboardParamDefinition def, Map<String, Object> input,
                                     Map<String, Object> result) {
        String name = def.getName();
        Object value = input.get(name);
        if (value == null) {
            value = def.getDefaultValue();
        }
        if (value != null) {
            validateSimpleType(def, value);
            result.put(name, coerceSimpleValue(def.getType(), value));
        }
    }

    @SuppressWarnings("unchecked")
    private static void resolveDateRange(DashboardParamDefinition def, Map<String, Object> input,
                                         Map<String, Object> result) {
        String name = def.getName();
        String startKey = name + ".start";
        String endKey = name + ".end";

        Object startValue = input.get(startKey);
        Object endValue = input.get(endKey);

        // §11.4 delimited 接受形态：两个扁平 key 均缺省时，paramName 标量按 'start,end' 拆分（扁平 key 优先）
        if (startValue == null && endValue == null) {
            Object scalar = input.get(name);
            if (scalar != null) {
                String[] parts = splitDelimitedDateRange(def, scalar);
                startValue = parts[0];
                endValue = parts[1];
            }
        }

        if (startValue == null || endValue == null) {
            Object defaultValue = def.getDefaultValue();
            if (defaultValue instanceof Map) {
                Map<String, Object> defaultMap = (Map<String, Object>) defaultValue;
                if (startValue == null) {
                    startValue = defaultMap.get("start");
                }
                if (endValue == null) {
                    endValue = defaultMap.get("end");
                }
            }
        }

        if (startValue != null) {
            validateDate(def, "start", startValue);
            result.put(startKey, startValue.toString());
        }
        if (endValue != null) {
            validateDate(def, "end", endValue);
            result.put(endKey, endValue.toString());
        }
    }

    /**
     * 拆分 delimited date-range 提交值（§11.4 契约形态）：须为字符串、恰好含一个 delimiter。
     * 日期段合法性由 {@link #validateDate} 逐段校验（含 relative token 显式拒绝）。数组形态显式拒绝。
     */
    private static String[] splitDelimitedDateRange(DashboardParamDefinition def, Object scalar) {
        String expected = "date-range delimited string '"
                + DashboardParamDefinition.DATE_RANGE_VALUE_FORMAT
                + DashboardParamDefinition.DATE_RANGE_DELIMITER
                + DashboardParamDefinition.DATE_RANGE_VALUE_FORMAT + "'";
        if (!(scalar instanceof String)) {
            throw typeMismatch(def.getName(), scalar, expected);
        }
        String s = (String) scalar;
        String delimiter = DashboardParamDefinition.DATE_RANGE_DELIMITER;
        int first = s.indexOf(delimiter);
        if (first < 0 || first != s.lastIndexOf(delimiter)) {
            throw typeMismatch(def.getName(), scalar, expected);
        }
        return new String[]{s.substring(0, first), s.substring(first + delimiter.length())};
    }

    private static void validateSimpleType(DashboardParamDefinition def, Object value) {
        String type = def.getType();
        if (DashboardParamDefinition.TYPE_NUMBER.equals(type)) {
            if (!isNumber(value)) {
                throw typeMismatch(def.getName(), value, "number");
            }
        } else if (DashboardParamDefinition.TYPE_DATE.equals(type)) {
            validateDate(def, null, value);
        }
    }

    private static void validateDate(DashboardParamDefinition def, String subKey, Object value) {
        if (!isParsableDate(value)) {
            String paramName = subKey == null ? def.getName() : def.getName() + "." + subKey;
            throw typeMismatch(paramName, value, "date(yyyy-MM-dd)");
        }
    }

    private static NopException typeMismatch(String paramName, Object value, String expected) {
        return new NopException(ERR_DATAV_PARAM_TYPE_MISMATCH)
                .param("paramName", paramName)
                .param("value", value)
                .param("expectedType", expected);
    }

    private static Object coerceSimpleValue(String type, Object value) {
        if (DashboardParamDefinition.TYPE_STRING.equals(type)) {
            return value.toString();
        }
        if (DashboardParamDefinition.TYPE_DATE.equals(type)) {
            return value.toString();
        }
        return value;
    }

    static boolean isNumber(Object value) {
        if (value instanceof Number) {
            return true;
        }
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static boolean isParsableDate(Object value) {
        if (value == null) {
            return false;
        }
        try {
            LocalDate.parse(value.toString(), ISO_DATE);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
