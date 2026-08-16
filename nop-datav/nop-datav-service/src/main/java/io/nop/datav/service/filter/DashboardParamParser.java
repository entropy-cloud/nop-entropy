package io.nop.datav.service.filter;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PARAM_CONFIG;

/**
 * 看板参数定义解析器。将 Dashboard 的 {@code paramConfig} JSON 文本解析为 {@link DashboardParamDefinition} 列表。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §一 参数定义存储方案。</p>
 *
 * <p>paramConfig JSON 结构为参数定义数组：</p>
 * <pre>
 * [{"name":"region","type":"string","defaultValue":"all"}, ...]
 * </pre>
 *
 * <p>解析失败（JSON 格式错误、非数组、元素缺 name/type、名称重复）时显式抛 {@code ERR_DATAV_INVALID_PARAM_CONFIG}，
 * 不返回 null/空列表（rule #24 无静默跳过）。</p>
 */
public final class DashboardParamParser {

    private DashboardParamParser() {
    }

    /**
     * 解析 paramConfig JSON 文本为参数定义列表。
     *
     * @param paramConfigJson paramConfig JSON 文本，允许 null/空（视为无参数定义，返回空列表）
     * @return 不可变的参数定义列表；永不为 null
     */
    public static List<DashboardParamDefinition> parse(String paramConfigJson) {
        if (paramConfigJson == null || paramConfigJson.isEmpty()) {
            return List.of();
        }
        Object parsed;
        try {
            parsed = JsonTool.parse(paramConfigJson);
        } catch (Exception e) {
            throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                    .param("reason", "JSON parse failed: " + e.getMessage());
        }
        if (parsed == null) {
            return List.of();
        }
        if (!(parsed instanceof List)) {
            throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                    .param("reason", "paramConfig must be a JSON array");
        }
        List<?> array = (List<?>) parsed;
        List<DashboardParamDefinition> result = new ArrayList<>(array.size());
        Set<String> seenNames = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            Object element = array.get(i);
            if (!(element instanceof Map)) {
                throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                        .param("reason", "paramConfig[" + i + "] must be a JSON object");
            }
            Map<?, ?> defMap = (Map<?, ?>) element;
            DashboardParamDefinition def = parseDefinition(defMap, i);
            if (!seenNames.add(def.getName())) {
                throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                        .param("reason", "duplicate param name: " + def.getName());
            }
            result.add(def);
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static DashboardParamDefinition parseDefinition(Map<?, ?> defMap, int index) {
        Object nameObj = defMap.get("name");
        if (!(nameObj instanceof String) || ((String) nameObj).isEmpty()) {
            throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                    .param("reason", "paramConfig[" + index + "] is missing non-empty 'name'");
        }
        String name = (String) nameObj;

        Object typeObj = defMap.get("type");
        if (!(typeObj instanceof String) || ((String) typeObj).isEmpty()) {
            throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                    .param("reason", "paramConfig[" + index + "] ('" + name + "') is missing non-empty 'type'");
        }
        String type = (String) typeObj;
        if (!isValidType(type)) {
            throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                    .param("reason", "paramConfig[" + index + "] ('" + name + "') has unsupported type: " + type);
        }

        Object defaultValue = defMap.get("defaultValue");
        Object labelObj = defMap.get("label");
        Object widgetObj = defMap.get("widget");
        String label = labelObj == null ? null : labelObj.toString();
        String widget = widgetObj == null ? null : widgetObj.toString();

        if (DashboardParamDefinition.TYPE_DATE_RANGE.equals(type) && defaultValue != null) {
            if (!(defaultValue instanceof Map)) {
                throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                        .param("reason", "paramConfig[" + index + "] ('" + name
                                + "') date-range defaultValue must be an object with start/end");
            }
        }
        return new DashboardParamDefinition(name, type, defaultValue, label, widget);
    }

    private static boolean isValidType(String type) {
        return DashboardParamDefinition.TYPE_STRING.equals(type)
                || DashboardParamDefinition.TYPE_NUMBER.equals(type)
                || DashboardParamDefinition.TYPE_DATE.equals(type)
                || DashboardParamDefinition.TYPE_DATE_RANGE.equals(type);
    }
}
