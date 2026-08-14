package io.nop.plugin.manager;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.plugin.manager.impl.VfsPluginDefinition;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.nop.plugin.manager.PluginManagerErrors.ARG_SPEC_ATTR;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INVALID_COEFFECT_SPEC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W5 Phase 1：coeffect spec 解析（VfsPluginDefinition 构造即提取类型化字段）——合法格式
 * （含无 | 形式、空值形式）、非法格式显式抛异常（No Silent No-Op）。
 */
public class TestPluginCoeffectSpec {

    private static final String PLUGIN_SCHEMA = "/nop/schema/plugin/plugin.xdef";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static VfsPluginDefinition parse(String xml) {
        XNode node = XNode.parse(xml);
        DynamicObject model = (DynamicObject) new DslModelParser(PLUGIN_SCHEMA).parseFromNode(node);
        return new VfsPluginDefinition("/nop/plugin/test/x.plugin.xml", model);
    }

    @Test
    public void testParseRequiresAndIfProperty() {
        VfsPluginDefinition def = parse("<plugin name=\"x\" requires=\"model-provider,other\""
                + " if-property=\"agent.tools.enabled|true\""
                + " x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\"/>");

        assertEquals("x", def.getName());
        assertEquals(Set.of("model-provider", "other"), def.getRequires());
        assertEquals("agent.tools.enabled", def.getIfPropertyName());
        assertEquals("true", def.getIfPropertyExpected(), "expectedValue 保留 spec 字符串");
    }

    @Test
    public void testIfPropertyWithoutPipeDefaultsExpectedToTrue() {
        VfsPluginDefinition def = parse("<plugin name=\"x\" if-property=\"agent.tools.enabled\""
                + " x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\"/>");

        assertEquals("agent.tools.enabled", def.getIfPropertyName());
        assertEquals(Boolean.TRUE, def.getIfPropertyExpected(), "缺省 expectedValue 视为 true");
        assertTrue(def.getRequires().isEmpty());
    }

    @Test
    public void testEmptySpecMeansNoConditions() {
        VfsPluginDefinition def = parse("<plugin name=\"x\""
                + " x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\"/>");

        assertTrue(def.getRequires().isEmpty(), "无 requires = 无条件");
        assertNull(def.getIfPropertyName(), "无 if-property = 无配置条件");
        assertNull(def.getIfPropertyExpected());
    }

    @Test
    public void testInvalidIfPropertyFailsExplicitly() {
        // propName 为空（| 分隔后前半空）：构造即校验，显式抛异常（No Silent No-Op）
        NopException e = assertThrows(NopException.class, () -> parse(
                "<plugin name=\"x\" if-property=\"|true\""
                        + " x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\"/>"));
        assertEquals(ERR_PLUGIN_INVALID_COEFFECT_SPEC.getErrorCode(), e.getErrorCode());
        assertEquals("if-property", e.getParam(ARG_SPEC_ATTR));
        assertEquals("/nop/plugin/test/x.plugin.xml", e.getParam("pluginId"));

        NopException blank = assertThrows(NopException.class, () -> parse(
                "<plugin name=\"x\" if-property=\" \""
                        + " x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\"/>"));
        assertEquals(ERR_PLUGIN_INVALID_COEFFECT_SPEC.getErrorCode(), blank.getErrorCode());
    }
}
