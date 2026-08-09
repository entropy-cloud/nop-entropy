package io.nop.datav.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PANEL_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试 {@link PanelRefreshConfig} 从 panelConfig JSON 的解析行为。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/runtime-design.md} §3 刷新机制。</p>
 */
public class TestPanelRefreshConfig {

    @Test
    public void testDisabledWhenPanelConfigIsNull() {
        PanelRefreshConfig config = PanelRefreshConfig.fromPanelConfigJson(null, "p1");
        assertNotNull(config);
        assertFalse(config.isEnabled());
        assertEquals(0, config.getIntervalSeconds());
    }

    @Test
    public void testDisabledWhenPanelConfigIsEmpty() {
        PanelRefreshConfig config = PanelRefreshConfig.fromPanelConfigJson("", "p1");
        assertFalse(config.isEnabled());
        assertEquals(0, config.getIntervalSeconds());
    }

    @Test
    public void testDisabledWhenRefreshSectionAbsent() {
        // panelConfig 含其他区域但无 refresh 区域
        String json = JsonTool.stringify(Map.of("title", Map.of("text", "Sales")));
        PanelRefreshConfig config = PanelRefreshConfig.fromPanelConfigJson(json, "p1");
        assertFalse(config.isEnabled());
        assertEquals(0, config.getIntervalSeconds());
    }

    @Test
    public void testParseEnabledWithInterval() {
        // 标准 refresh 配置
        String json = JsonTool.stringify(Map.of(
                "refresh", Map.of("enabled", true, "intervalSeconds", 30)
        ));
        PanelRefreshConfig config = PanelRefreshConfig.fromPanelConfigJson(json, "p1");
        assertTrue(config.isEnabled());
        assertEquals(30, config.getIntervalSeconds());
    }

    @Test
    public void testParseDefaultsWhenFieldsMissing() {
        // refresh 区域存在但字段缺失：enabled 缺省 false，interval 缺省 0
        String json = JsonTool.stringify(Map.of("refresh", Map.of()));
        PanelRefreshConfig config = PanelRefreshConfig.fromPanelConfigJson(json, "p1");
        assertFalse(config.isEnabled());
        assertEquals(0, config.getIntervalSeconds());
    }

    @Test
    public void testThrowsWhenPanelConfigIsInvalidJson() {
        // JsonTool 严格模式对 invalid JSON 抛 NopException（具体错误码取决于解析失败原因）
        // 我们的 wrapper 确保它是 NopException + ERR_DATAV_INVALID_PANEL_CONFIG（cause 包装原异常）
        NopException ex = assertThrows(NopException.class,
                () -> PanelRefreshConfig.fromPanelConfigJson("{not valid json", "p1"));
        assertEquals(ERR_DATAV_INVALID_PANEL_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testThrowsWhenPanelConfigIsInvalidJsonMalformed() {
        // 完全无法解析的 JSON
        NopException ex = assertThrows(NopException.class,
                () -> PanelRefreshConfig.fromPanelConfigJson("}{", "p1"));
        assertEquals(ERR_DATAV_INVALID_PANEL_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testThrowsWhenRefreshRegionIsNotObject() {
        // refresh 是个字符串而非对象
        String json = "{\"refresh\":\"oops\"}";
        NopException ex = assertThrows(NopException.class,
                () -> PanelRefreshConfig.fromPanelConfigJson(json, "p1"));
        assertEquals(ERR_DATAV_INVALID_PANEL_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testThrowsWhenPanelConfigIsNotObject() {
        // 顶层是数组
        String json = "[1,2,3]";
        NopException ex = assertThrows(NopException.class,
                () -> PanelRefreshConfig.fromPanelConfigJson(json, "p1"));
        assertEquals(ERR_DATAV_INVALID_PANEL_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testToConfigMapRoundTrip() {
        PanelRefreshConfig config = new PanelRefreshConfig(true, 60);
        Map<String, Object> map = config.toConfigMap();
        assertNotNull(map.get("refresh"));

        String json = JsonTool.stringify(map);
        PanelRefreshConfig parsed = PanelRefreshConfig.fromPanelConfigJson(json, "p1");
        assertTrue(parsed.isEnabled());
        assertEquals(60, parsed.getIntervalSeconds());
    }

    @Test
    public void testStringValueCoercedForEnabled() {
        // 字符串 "true" 应被解析为 boolean true
        String json = "{\"refresh\":{\"enabled\":\"true\",\"intervalSeconds\":\"15\"}}";
        PanelRefreshConfig config = PanelRefreshConfig.fromPanelConfigJson(json, "p1");
        assertTrue(config.isEnabled());
        assertEquals(15, config.getIntervalSeconds());
    }
}
