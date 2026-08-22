package io.nop.plugin.manager;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.model.BeanModel;
import io.nop.ioc.model.BeansModel;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.parse.XDefinitionParser;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plugin.xdef 可解析性验证：新 schema（动态模型模式 + {@code <beans xdef:ref>} 复用 BeansModel 根）
 * 真实经 DslModelParser 消费，字段可读、非法输入校验报错——证明 schema 非空壳。
 */
public class TestPluginXdef extends BaseTestCase {

    private static final String PLUGIN_SCHEMA = "/nop/schema/plugin/plugin.xdef";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParseValidPlugin() {
        DynamicObject model = (DynamicObject) new DslModelParser(PLUGIN_SCHEMA)
                .parseFromVirtualPath("/nop/plugin/test/agent-tools.plugin.xml");

        assertInstanceOf(DynamicObject.class, model);
        assertEquals("agent-tools", model.prop_get("name"));
        assertEquals(Set.of("model-provider"), model.prop_get("requires"));
        assertEquals("agent.tools.enabled|true", model.prop_get("ifProperty"));
        assertEquals("agentToolsActivator", model.prop_get("activator"));

        BeansModel beans = assertInstanceOf(BeansModel.class, model.prop_get("beans"));
        List<BeanModel> beanList = beans.getBeans();
        assertEquals(2, beanList.size());
        assertEquals("tool.bash", beanList.get(0).getId());
        assertEquals("io.nop.plugin.test.BashTool", beanList.get(0).getClassName());
        assertTrue(beanList.get(0).isPrimary());
        assertEquals("tool.search", beanList.get(1).getId());
        assertEquals("io.nop.plugin.test.SearchTool", beanList.get(1).getClassName());
    }

    @Test
    public void testUnknownAttrFails() {
        XNode invalid = XNode.parse("<plugin name=\"bad\" unknown-attr=\"x\""
                + " x:schema=\"/nop/schema/plugin/plugin.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\"/>");
        assertThrows(NopException.class,
                () -> new DslModelParser(PLUGIN_SCHEMA).parseFromNode(invalid));
    }

    /**
     * 属性集冻结确认（R3 Phase 3，01 §7.6 + §五终裁）：plugin.xdef 根节点声明的属性集 =
     * name（必填）/requires/if-property/activator，子元素 = &lt;beans&gt;（xdef:ref 复用
     * beans.xdef），不新增 requires-service 等服务级条件属性（扩展点已关闭）。机器可检：
     * 任何属性集偏差（新增或删除）都会使本测试失败——冻结基线的守护测试。
     */
    @Test
    public void testAttributeSetFrozen() {
        IXDefinition xdef = new XDefinitionParser().parseFromVirtualPath(PLUGIN_SCHEMA);
        IXDefNode root = xdef.getRootNode();

        assertEquals("plugin", root.getTagName(), "根节点 plugin");
        assertEquals(Set.of("name", "requires", "if-property", "activator"),
                new TreeSet<>(root.getAttributes().keySet()),
                "属性集冻结：name/requires/if-property/activator（不新增 requires-service 等服务级条件属性）");
        assertTrue(root.getAttributes().get("name").isMandatory(), "name 必填（!string）");
        assertTrue(root.getAttributes().get("requires").getType().toString().contains("csv-set"),
                "requires 为 csv-set 类型");

        assertEquals(Set.of("beans"), root.getChildren().keySet(),
                "子元素仅 <beans>（xdef:ref 复用 beans.xdef 根，不改 beans.xdef）");
        assertNotNull(root.getChildren().get("beans"), "beans 子元素声明存在");
    }
}
