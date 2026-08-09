package io.nop.datav.service.linkage;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.datav.biz.FilterState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_FILTER_STATE;

/**
 * filter_state 内容序列化/反序列化编解码器。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §9.3 filter_state 内容契约。</p>
 *
 * <p>{@code stateContent} JSON 结构：</p>
 * <pre>
 * {
 *   "globalFilters": {"region":"east","dateRange.start":"2024-01-01"},
 *   "panelSelections": {
 *     "p1": {"field":"region","value":"east"}
 *   },
 *   "urlState": "region=east&dateRange.start=2024-01-01"
 * }
 * </pre>
 *
 * <p>序列化失败不静默跳过（rule #24）：反序列化时 JSON 格式错误、结构非对象、
 * panelSelections 子元素缺 field/value 等，显式抛 {@code ERR_DATAV_INVALID_FILTER_STATE}。</p>
 */
public final class FilterStateCodec {

    private static final String KEY_GLOBAL_FILTERS = "globalFilters";
    private static final String KEY_PANEL_SELECTIONS = "panelSelections";
    private static final String KEY_URL_STATE = "urlState";
    private static final String KEY_FIELD = "field";
    private static final String KEY_VALUE = "value";

    private FilterStateCodec() {
    }

    /**
     * 序列化 filter_state 为 stateContent JSON 文本。
     */
    public static String encode(Map<String, Object> globalFilters,
                                Map<String, Map<String, Object>> panelSelections,
                                String urlState) {
        Map<String, Object> root = new LinkedHashMap<>(3);
        root.put(KEY_GLOBAL_FILTERS, globalFilters == null ? Collections.emptyMap() : globalFilters);
        root.put(KEY_PANEL_SELECTIONS,
                panelSelections == null ? Collections.emptyMap() : panelSelections);
        if (urlState != null) {
            root.put(KEY_URL_STATE, urlState);
        }
        return JsonTool.stringify(root);
    }

    /**
     * 反序列化 stateContent JSON 文本为 filter_state 结构。
     *
     * @param dashboardId    看板 ID（用于错误消息）
     * @param stateContentJson stateContent JSON 文本；允许 null/空（返回空 FilterState，由调用方判断 null 记录）
     * @return 结构化 filter_state；永不为 null（结构非法时显式抛异常）
     */
    public static FilterState decode(String dashboardId, String stateContentJson) {
        if (stateContentJson == null || stateContentJson.isEmpty()) {
            return new FilterState(Collections.emptyMap(), Collections.emptyMap(), null);
        }
        Object parsed;
        try {
            parsed = JsonTool.parse(stateContentJson);
        } catch (Exception e) {
            throw new NopException(ERR_DATAV_INVALID_FILTER_STATE)
                    .param("dashboardId", dashboardId)
                    .param("reason", "JSON parse failed: " + e.getMessage())
                    .cause(e);
        }
        if (!(parsed instanceof Map)) {
            throw new NopException(ERR_DATAV_INVALID_FILTER_STATE)
                    .param("dashboardId", dashboardId)
                    .param("reason", "stateContent must be a JSON object");
        }
        Map<?, ?> root = (Map<?, ?>) parsed;

        Map<String, Object> globalFilters = parseObjectMap(dashboardId, root.get(KEY_GLOBAL_FILTERS),
                KEY_GLOBAL_FILTERS);
        Map<String, FilterState.PanelSelection> panelSelections =
                parsePanelSelections(dashboardId, root.get(KEY_PANEL_SELECTIONS));
        Object urlStateObj = root.get(KEY_URL_STATE);
        String urlState = urlStateObj == null ? null : urlStateObj.toString();
        return new FilterState(globalFilters, panelSelections, urlState);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObjectMap(String dashboardId, Object raw, String region) {
        if (raw == null) {
            return Collections.emptyMap();
        }
        if (!(raw instanceof Map)) {
            throw new NopException(ERR_DATAV_INVALID_FILTER_STATE)
                    .param("dashboardId", dashboardId)
                    .param("reason", "'" + region + "' region must be a JSON object");
        }
        return (Map<String, Object>) raw;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, FilterState.PanelSelection> parsePanelSelections(String dashboardId, Object raw) {
        if (raw == null) {
            return Collections.emptyMap();
        }
        if (!(raw instanceof Map)) {
            throw new NopException(ERR_DATAV_INVALID_FILTER_STATE)
                    .param("dashboardId", dashboardId)
                    .param("reason", "'panelSelections' region must be a JSON object");
        }
        Map<?, ?> selectionsMap = (Map<?, ?>) raw;
        if (selectionsMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, FilterState.PanelSelection> result = new LinkedHashMap<>(selectionsMap.size());
        for (Map.Entry<?, ?> entry : selectionsMap.entrySet()) {
            String panelId = entry.getKey().toString();
            Object selectionObj = entry.getValue();
            if (!(selectionObj instanceof Map)) {
                throw new NopException(ERR_DATAV_INVALID_FILTER_STATE)
                        .param("dashboardId", dashboardId)
                        .param("reason", "panelSelections['" + panelId + "'] must be a JSON object");
            }
            Map<String, Object> selectionMap = (Map<String, Object>) selectionObj;
            Object field = selectionMap.get(KEY_FIELD);
            Object value = selectionMap.get(KEY_VALUE);
            if (field == null || !(field instanceof String) || ((String) field).isEmpty()) {
                throw new NopException(ERR_DATAV_INVALID_FILTER_STATE)
                        .param("dashboardId", dashboardId)
                        .param("reason", "panelSelections['" + panelId + "'] is missing non-empty 'field'");
            }
            result.put(panelId, new FilterState.PanelSelection((String) field, value));
        }
        return result;
    }
}
