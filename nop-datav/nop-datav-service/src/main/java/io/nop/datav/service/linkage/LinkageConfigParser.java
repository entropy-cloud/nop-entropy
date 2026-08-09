package io.nop.datav.service.linkage;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_JUMP_CONFIG;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_LINKAGE_CONFIG;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PANEL_CONFIG;

/**
 * 联动/跳转配置解析器。从 Panel 的 {@code panelConfig} JSON 中解析联动规则（{@code linkage} 区域）
 * 和跳转规则（{@code jump} 区域）。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §8.2 / §8.3。</p>
 *
 * <p>panelConfig JSON 结构（约定）：</p>
 * <pre>
 * {
 *   "component": {...},
 *   "refresh": {...},
 *   "linkage": [
 *     {"sourceField":"region","targetPanelId":"p2","targetParam":"region"}
 *   ],
 *   "jump": [
 *     {"sourceField":"region","targetType":"dashboard","targetId":"d2","params":{"region":"${region}"}}
 *   ]
 * }
 * </pre>
 *
 * <p>解析失败（panelConfig 非 JSON 对象、linkage/jump 区域非数组、规则元素缺必填字段、targetType 非法）
 * 时显式抛 {@code NopException}，不返回 null/空列表（rule #24 无静默跳过）。
 * 当 panelConfig 为 null/空或无 linkage/jump 区域时返回空列表（视为无联动/跳转配置，合法分支）。</p>
 */
public final class LinkageConfigParser {

    private LinkageConfigParser() {
    }

    /**
     * 解析 panelConfig JSON 中的 linkage 区域为联动规则列表。
     *
     * @param panelId        面板 ID（用于错误消息）
     * @param panelConfigJson panelConfig JSON 文本，允许 null/空
     * @return 不可变的联动规则列表；永不为 null。panelConfig 为 null/空或无 linkage 区域时返回空列表
     */
    public static List<LinkageRule> parseLinkageRules(String panelId, String panelConfigJson) {
        Map<String, Object> config = parsePanelConfigObject(panelId, panelConfigJson);
        if (config == null) {
            return Collections.emptyList();
        }
        Object linkage = config.get("linkage");
        if (linkage == null) {
            return Collections.emptyList();
        }
        if (!(linkage instanceof List)) {
            throw new NopException(ERR_DATAV_INVALID_LINKAGE_CONFIG)
                    .param("panelId", panelId)
                    .param("reason", "'linkage' region must be a JSON array");
        }
        List<?> array = (List<?>) linkage;
        List<LinkageRule> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            Object element = array.get(i);
            if (!(element instanceof Map)) {
                throw new NopException(ERR_DATAV_INVALID_LINKAGE_CONFIG)
                        .param("panelId", panelId)
                        .param("reason", "linkage[" + i + "] must be a JSON object");
            }
            result.add(parseLinkageRule(panelId, i, (Map<?, ?>) element));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 解析 panelConfig JSON 中的 jump 区域为跳转规则列表。
     *
     * @param panelId        面板 ID（用于错误消息）
     * @param panelConfigJson panelConfig JSON 文本，允许 null/空
     * @return 不可变的跳转规则列表；永不为 null。panelConfig 为 null/空或无 jump 区域时返回空列表
     */
    public static List<JumpRule> parseJumpRules(String panelId, String panelConfigJson) {
        Map<String, Object> config = parsePanelConfigObject(panelId, panelConfigJson);
        if (config == null) {
            return Collections.emptyList();
        }
        Object jump = config.get("jump");
        if (jump == null) {
            return Collections.emptyList();
        }
        if (!(jump instanceof List)) {
            throw new NopException(ERR_DATAV_INVALID_LINKAGE_CONFIG)
                    .param("panelId", panelId)
                    .param("reason", "'jump' region must be a JSON array");
        }
        List<?> array = (List<?>) jump;
        List<JumpRule> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            Object element = array.get(i);
            if (!(element instanceof Map)) {
                throw new NopException(ERR_DATAV_INVALID_LINKAGE_CONFIG)
                        .param("panelId", panelId)
                        .param("reason", "jump[" + i + "] must be a JSON object");
            }
            result.add(parseJumpRule(panelId, i, (Map<?, ?>) element));
        }
        return Collections.unmodifiableList(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parsePanelConfigObject(String panelId, String panelConfigJson) {
        if (panelConfigJson == null || panelConfigJson.isEmpty()) {
            return null;
        }
        Object parsed;
        try {
            parsed = JsonTool.parse(panelConfigJson);
        } catch (Exception e) {
            throw new NopException(ERR_DATAV_INVALID_PANEL_CONFIG)
                    .param("panelId", panelId)
                    .cause(e);
        }
        if (parsed == null) {
            return null;
        }
        if (!(parsed instanceof Map)) {
            throw new NopException(ERR_DATAV_INVALID_PANEL_CONFIG)
                    .param("panelId", panelId);
        }
        return (Map<String, Object>) parsed;
    }

    private static LinkageRule parseLinkageRule(String panelId, int index, Map<?, ?> ruleMap) {
        String sourceField = requireNonEmptyString(ruleMap, "sourceField",
                () -> new NopException(ERR_DATAV_INVALID_LINKAGE_CONFIG)
                        .param("panelId", panelId)
                        .param("reason", "linkage[" + index + "] is missing non-empty 'sourceField'"));
        String targetPanelId = requireNonEmptyString(ruleMap, "targetPanelId",
                () -> new NopException(ERR_DATAV_INVALID_LINKAGE_CONFIG)
                        .param("panelId", panelId)
                        .param("reason", "linkage[" + index + "] ('" + sourceField
                                + "') is missing non-empty 'targetPanelId'"));
        String targetParam = requireNonEmptyString(ruleMap, "targetParam",
                () -> new NopException(ERR_DATAV_INVALID_LINKAGE_CONFIG)
                        .param("panelId", panelId)
                        .param("reason", "linkage[" + index + "] ('" + sourceField
                                + "') is missing non-empty 'targetParam'"));
        return new LinkageRule(sourceField, targetPanelId, targetParam);
    }

    private static JumpRule parseJumpRule(String panelId, int index, Map<?, ?> ruleMap) {
        String sourceField = requireNonEmptyString(ruleMap, "sourceField",
                () -> new NopException(ERR_DATAV_INVALID_JUMP_CONFIG)
                        .param("panelId", panelId)
                        .param("reason", "jump[" + index + "] is missing non-empty 'sourceField'"));
        String targetType = requireNonEmptyString(ruleMap, "targetType",
                () -> new NopException(ERR_DATAV_INVALID_JUMP_CONFIG)
                        .param("panelId", panelId)
                        .param("reason", "jump[" + index + "] ('" + sourceField
                                + "') is missing non-empty 'targetType'"));
        if (!JumpRule.TARGET_TYPE_DASHBOARD.equals(targetType)
                && !JumpRule.TARGET_TYPE_EXTERNAL_URL.equals(targetType)) {
            throw new NopException(ERR_DATAV_INVALID_JUMP_CONFIG)
                    .param("panelId", panelId)
                    .param("reason", "jump[" + index + "] ('" + sourceField
                            + "') has unsupported targetType: " + targetType);
        }
        String targetId = requireNonEmptyString(ruleMap, "targetId",
                () -> new NopException(ERR_DATAV_INVALID_JUMP_CONFIG)
                        .param("panelId", panelId)
                        .param("reason", "jump[" + index + "] ('" + sourceField
                                + "') is missing non-empty 'targetId'"));
        Map<String, String> params = parseParamsMap(panelId, index, sourceField, ruleMap.get("params"));
        return new JumpRule(sourceField, targetType, targetId, params);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseParamsMap(String panelId, int index, String sourceField,
                                                       Object paramsObj) {
        if (paramsObj == null) {
            return Collections.emptyMap();
        }
        if (!(paramsObj instanceof Map)) {
            throw new NopException(ERR_DATAV_INVALID_JUMP_CONFIG)
                    .param("panelId", panelId)
                    .param("reason", "jump[" + index + "] ('" + sourceField + "') params must be a JSON object");
        }
        Map<?, ?> raw = (Map<?, ?>) paramsObj;
        if (raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>(raw.size());
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) {
                throw new NopException(ERR_DATAV_INVALID_JUMP_CONFIG)
                        .param("panelId", panelId)
                        .param("reason", "jump[" + index + "] ('" + sourceField
                                + "') params contains null key or value");
            }
            result.put(key.toString(), value.toString());
        }
        return result;
    }

    private static String requireNonEmptyString(Map<?, ?> map, String key,
                                                 java.util.function.Supplier<NopException> errorSupplier) {
        Object value = map.get(key);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw errorSupplier.get();
        }
        return (String) value;
    }
}
