package io.nop.datav.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ARG_REASON;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PARAM_CONFIG;

/**
 * 面板查询参数求值器。根据 {@code NopDatavDatasetRef.paramMapping} JSON 规则，从 API 请求参数 Map 合成最终查询参数。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/runtime-design.md} §2.4 参数求值。</p>
 *
 * <p>paramMapping JSON 结构（裁定格式）：</p>
 * <pre>
 * {
 *   "start_date": {"source": "startDate", "defaultValue": null},
 *   "category":   {"source": "category",  "defaultValue": "all"}
 * }
 * </pre>
 * <p>顶层 key = SQL 中 {@code ${paramName}} 的占位符名；value 的 {@code source} 指定从请求参数 Map 中取值的 key，
 * {@code defaultValue} 在请求参数未提供时使用（缺省为 null）。仅出现在 paramMapping 中的参数会被传入 SQL（未声明的请求参数被忽略）。</p>
 */
public final class PanelParamEvaluator {

    private PanelParamEvaluator() {
    }

    /**
     * 求值查询参数。
     *
     * @param paramMappingJson DatasetRef.paramMapping JSON 文本，允许 null/空（视为无参数查询）
     * @param requestParams    API 请求参数 Map，允许 null/空
     * @return 不可变的查询参数 Map（按 paramMapping 中的顺序）；永不为 null
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> evaluate(String paramMappingJson, Map<String, Object> requestParams) {
        if (paramMappingJson == null || paramMappingJson.isEmpty()) {
            return Collections.emptyMap();
        }
        Object parsed;
        try {
            parsed = JsonTool.parse(paramMappingJson);
        } catch (Exception e) {
            throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                    .param(ARG_REASON, "Invalid paramMapping JSON: " + e.getMessage())
                    .cause(e);
        }
        if (parsed == null) {
            return Collections.emptyMap();
        }
        if (!(parsed instanceof Map)) {
            throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                    .param(ARG_REASON, "paramMapping JSON must be an object");
        }
        Map<String, Object> mapping = (Map<String, Object>) parsed;
        if (mapping.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> requestMap = requestParams == null ? Collections.emptyMap() : requestParams;
        Map<String, Object> result = new LinkedHashMap<>(mapping.size());
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            String paramName = entry.getKey();
            Object ruleObj = entry.getValue();
            if (!(ruleObj instanceof Map)) {
                throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                        .param(ARG_REASON,
                                "paramMapping rule for '" + paramName + "' must be an object with 'source'");
            }
            Map<String, Object> rule = (Map<String, Object>) ruleObj;
            Object sourceKey = rule.get("source");
            if (sourceKey == null || sourceKey.toString().isEmpty()) {
                throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                        .param(ARG_REASON,
                                "paramMapping rule for '" + paramName + "' is missing 'source'");
            }
            Object defaultValue = rule.get("defaultValue");
            Object value = requestMap.get(sourceKey.toString());
            if (value == null) {
                value = defaultValue;
            }
            result.put(paramName, value);
        }
        return Collections.unmodifiableMap(result);
    }
}
