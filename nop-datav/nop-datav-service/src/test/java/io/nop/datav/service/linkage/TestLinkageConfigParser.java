package io.nop.datav.service.linkage;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_JUMP_CONFIG;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_LINKAGE_CONFIG;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PANEL_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 联动/跳转配置解析器单元测试（D2-2 Phase 1）。
 *
 * <p>覆盖：正常解析、无联动配置面板（返回空列表）、配置格式错误显式失败（非返回 null）、
 * 跳转规则解析、targetType 校验、参数映射解析。</p>
 */
public class TestLinkageConfigParser {

    // ==================== LinkageRules ====================

    @Test
    public void testParseLinkageNullEmptyReturnsEmpty() {
        assertTrue(LinkageConfigParser.parseLinkageRules("p1", null).isEmpty());
        assertTrue(LinkageConfigParser.parseLinkageRules("p1", "").isEmpty());
    }

    @Test
    public void testParseLinkageNoLinkageRegionReturnsEmpty() {
        // panelConfig has no 'linkage' region -> empty list (legit "no linkage configured")
        String json = JsonTool.stringify(Map.of("component", Map.of("type", "chart")));
        assertTrue(LinkageConfigParser.parseLinkageRules("p1", json).isEmpty());
    }

    @Test
    public void testParseLinkageNormal() {
        String json = JsonTool.stringify(Map.of(
                "linkage", List.of(
                        Map.of("sourceField", "region", "targetPanelId", "p2", "targetParam", "region"),
                        Map.of("sourceField", "category", "targetPanelId", "p3", "targetParam", "cat")
                )
        ));
        List<LinkageRule> rules = LinkageConfigParser.parseLinkageRules("p1", json);
        assertEquals(2, rules.size());
        assertEquals("region", rules.get(0).getSourceField());
        assertEquals("p2", rules.get(0).getTargetPanelId());
        assertEquals("region", rules.get(0).getTargetParam());
        assertEquals("category", rules.get(1).getSourceField());
    }

    @Test
    public void testParseLinkageRegionNotArrayThrows() {
        String json = JsonTool.stringify(Map.of("linkage", "not-an-array"));
        NopException ex = assertThrows(NopException.class,
                () -> LinkageConfigParser.parseLinkageRules("p1", json));
        assertEquals(ERR_DATAV_INVALID_LINKAGE_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseLinkageElementNotObjectThrows() {
        String json = "[{\"linkage\":[\"not-an-object\"]}]";
        // wrap linkage in a top-level config
        String json2 = JsonTool.stringify(Map.of("linkage", List.of("not-an-object")));
        NopException ex = assertThrows(NopException.class,
                () -> LinkageConfigParser.parseLinkageRules("p1", json2));
        assertEquals(ERR_DATAV_INVALID_LINKAGE_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseLinkageMissingSourceFieldThrows() {
        String json = JsonTool.stringify(Map.of(
                "linkage", List.of(Map.of("targetPanelId", "p2", "targetParam", "region"))
        ));
        NopException ex = assertThrows(NopException.class,
                () -> LinkageConfigParser.parseLinkageRules("p1", json));
        assertEquals(ERR_DATAV_INVALID_LINKAGE_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseLinkageMissingTargetPanelIdThrows() {
        String json = JsonTool.stringify(Map.of(
                "linkage", List.of(Map.of("sourceField", "region", "targetParam", "region"))
        ));
        NopException ex = assertThrows(NopException.class,
                () -> LinkageConfigParser.parseLinkageRules("p1", json));
        assertEquals(ERR_DATAV_INVALID_LINKAGE_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseLinkageMissingTargetParamThrows() {
        String json = JsonTool.stringify(Map.of(
                "linkage", List.of(Map.of("sourceField", "region", "targetPanelId", "p2"))
        ));
        NopException ex = assertThrows(NopException.class,
                () -> LinkageConfigParser.parseLinkageRules("p1", json));
        assertEquals(ERR_DATAV_INVALID_LINKAGE_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseLinkageMalformedJsonThrows() {
        NopException ex = assertThrows(NopException.class,
                () -> LinkageConfigParser.parseLinkageRules("p1", "{not valid json"));
        assertEquals(ERR_DATAV_INVALID_PANEL_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseLinkageNotObjectThrows() {
        // panelConfig is a JSON array instead of object
        NopException ex = assertThrows(NopException.class,
                () -> LinkageConfigParser.parseLinkageRules("p1", "[1,2,3]"));
        assertEquals(ERR_DATAV_INVALID_PANEL_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    // ==================== JumpRules ====================

    @Test
    public void testParseJumpNullEmptyReturnsEmpty() {
        assertTrue(LinkageConfigParser.parseJumpRules("p1", null).isEmpty());
        assertTrue(LinkageConfigParser.parseJumpRules("p1", "").isEmpty());
    }

    @Test
    public void testParseJumpNoJumpRegionReturnsEmpty() {
        String json = JsonTool.stringify(Map.of("component", Map.of("type", "chart")));
        assertTrue(LinkageConfigParser.parseJumpRules("p1", json).isEmpty());
    }

    @Test
    public void testParseJumpDashboardNormal() {
        String json = JsonTool.stringify(Map.of(
                "jump", List.of(
                        Map.of("sourceField", "region",
                                "targetType", "dashboard",
                                "targetId", "dash-2",
                                "params", Map.of("region", "${region}"))
                )
        ));
        List<JumpRule> rules = LinkageConfigParser.parseJumpRules("p1", json);
        assertEquals(1, rules.size());
        JumpRule rule = rules.get(0);
        assertEquals("region", rule.getSourceField());
        assertTrue(rule.isDashboardType());
        assertEquals("dash-2", rule.getTargetId());
        assertEquals("${region}", rule.getParams().get("region"));
    }

    @Test
    public void testParseJumpExternalUrlNormal() {
        String json = JsonTool.stringify(Map.of(
                "jump", List.of(
                        Map.of("sourceField", "region",
                                "targetType", "external-url",
                                "targetId", "https://example.com/r?region=${region}")
                )
        ));
        List<JumpRule> rules = LinkageConfigParser.parseJumpRules("p1", json);
        assertEquals(1, rules.size());
        JumpRule rule = rules.get(0);
        assertTrue(rule.isExternalUrlType());
        assertEquals("https://example.com/r?region=${region}", rule.getTargetId());
        assertNotNull(rule.getParams());
        assertTrue(rule.getParams().isEmpty());
    }

    @Test
    public void testParseJumpRegionNotArrayThrows() {
        String json = JsonTool.stringify(Map.of("jump", "not-an-array"));
        NopException ex = assertThrows(NopException.class,
                () -> LinkageConfigParser.parseJumpRules("p1", json));
        // jump region non-array uses LINKAGE_CONFIG error code (region-level error)
        assertEquals(ERR_DATAV_INVALID_LINKAGE_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseJumpMissingSourceFieldThrows() {
        String json = JsonTool.stringify(Map.of(
                "jump", List.of(Map.of("targetType", "dashboard", "targetId", "d2"))
        ));
        NopException ex = assertThrows(NopException.class,
                () -> LinkageConfigParser.parseJumpRules("p1", json));
        assertEquals(ERR_DATAV_INVALID_JUMP_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseJumpUnsupportedTargetTypeThrows() {
        String json = JsonTool.stringify(Map.of(
                "jump", List.of(Map.of(
                        "sourceField", "region",
                        "targetType", "bogus",
                        "targetId", "d2"))
        ));
        NopException ex = assertThrows(NopException.class,
                () -> LinkageConfigParser.parseJumpRules("p1", json));
        assertEquals(ERR_DATAV_INVALID_JUMP_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseJumpMissingTargetIdThrows() {
        String json = JsonTool.stringify(Map.of(
                "jump", List.of(Map.of(
                        "sourceField", "region",
                        "targetType", "dashboard"))
        ));
        NopException ex = assertThrows(NopException.class,
                () -> LinkageConfigParser.parseJumpRules("p1", json));
        assertEquals(ERR_DATAV_INVALID_JUMP_CONFIG.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testParseJumpParamsNotObjectThrows() {
        String json = JsonTool.stringify(Map.of(
                "jump", List.of(Map.of(
                        "sourceField", "region",
                        "targetType", "dashboard",
                        "targetId", "d2",
                        "params", "not-an-object"))
        ));
        NopException ex = assertThrows(NopException.class,
                () -> LinkageConfigParser.parseJumpRules("p1", json));
        assertEquals(ERR_DATAV_INVALID_JUMP_CONFIG.getErrorCode(), ex.getErrorCode());
    }
}
