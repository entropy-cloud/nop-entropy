package io.nop.datav.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 面板刷新配置。从 {@code panelConfig} JSON 的 {@code refresh} 区域解析。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/runtime-design.md} §3.1 刷新配置存储。</p>
 *
 * <p>不可变值对象。</p>
 */
public final class PanelRefreshConfig {

    public static final PanelRefreshConfig DISABLED = new PanelRefreshConfig(false, 0);

    private final boolean enabled;
    private final int intervalSeconds;

    public PanelRefreshConfig(boolean enabled, int intervalSeconds) {
        this.enabled = enabled;
        this.intervalSeconds = intervalSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    /**
     * 从 panelConfig JSON 解析刷新配置。
     *
     * <p>规则（参见 design doc §3.3 配置读取）：</p>
     * <ul>
     *   <li>panelConfig 为 null/空 → 返回 {@link #DISABLED}（enabled=false, intervalSeconds=0）</li>
     *   <li>panelConfig 无 {@code refresh} 区域 → 返回 {@link #DISABLED}</li>
     *   <li>{@code refresh} 区域缺省字段：{@code enabled} 缺省 false，{@code intervalSeconds} 缺省 0</li>
     *   <li>panelConfig JSON 解析失败 → 抛 {@link NopException}</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public static PanelRefreshConfig fromPanelConfigJson(String panelConfigJson, String panelId) {
        if (panelConfigJson == null || panelConfigJson.isEmpty()) {
            return DISABLED;
        }
        Map<String, Object> config;
        try {
            Object parsed = JsonTool.parse(panelConfigJson);
            if (!(parsed instanceof Map)) {
                throw new NopException(io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PANEL_CONFIG)
                        .param("panelId", panelId);
            }
            config = (Map<String, Object>) parsed;
        } catch (NopException e) {
            // JsonTool 解析失败会抛 NopException（strict-mode）；统一包装为 ERR_DATAV_INVALID_PANEL_CONFIG，
            // 保留原异常作为 cause 以便诊断，但替换错误码为 datav 域错误码
            if (io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PANEL_CONFIG.getErrorCode()
                    .equals(e.getErrorCode())) {
                throw e;
            }
            throw new NopException(io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PANEL_CONFIG)
                    .param("panelId", panelId)
                    .cause(e);
        } catch (Exception e) {
            throw new NopException(io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PANEL_CONFIG)
                    .param("panelId", panelId)
                    .cause(e);
        }
        Object refreshObj = config.get("refresh");
        if (refreshObj == null) {
            return DISABLED;
        }
        if (!(refreshObj instanceof Map)) {
            throw new NopException(io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PANEL_CONFIG)
                    .param("panelId", panelId);
        }
        Map<String, Object> refresh = (Map<String, Object>) refreshObj;
        boolean enabled = toBoolean(refresh.get("enabled"), false);
        int interval = toInt(refresh.get("intervalSeconds"), 0);
        return new PanelRefreshConfig(enabled, interval);
    }

    /**
     * 序列化为 {@code refresh} 区域 JSON 片段。主要用于测试与配置生成。
     */
    public Map<String, Object> toConfigMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> refresh = new LinkedHashMap<>();
        refresh.put("enabled", enabled);
        refresh.put("intervalSeconds", intervalSeconds);
        map.put("refresh", refresh);
        return Collections.unmodifiableMap(map);
    }

    private static boolean toBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    private static int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
