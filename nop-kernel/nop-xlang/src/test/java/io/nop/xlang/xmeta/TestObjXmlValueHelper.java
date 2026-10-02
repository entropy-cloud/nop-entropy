package io.nop.xlang.xmeta;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xmeta.impl.ObjPropMetaImpl;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import io.nop.core.type.PredefinedGenericTypes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 补强：ObjXmlValueHelper（WI0 快照 0% 靶点，33 行）。
 * getChildValueForDef 按 xdef 定义从 XDSL 节点读取属性值（集合节点转 JSON 列表）。
 */
public class TestObjXmlValueHelper extends BaseTestCase {
    static IXDefNode rootDef;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        IXDefinition def = SchemaLoader.loadXDefinition("/test/wi3-clean.xdef");
        rootDef = def.getRootNode();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static XNode parse(String xml) {
        return XNodeParser.instance().parseFromText(null, xml);
    }

    /**
     * xdef 未定义的属性名：直接读取同名子节点并转为 JSON 对象；子节点缺失返回 null。
     */
    @Test
    public void testChildValueWithoutDef() {
        XNode node = parse("<root><raw><k1>v1</k1><k2>v2</k2></raw></root>");
        Object value = ObjXmlValueHelper.getChildValueForDef(node, rootDef, "raw");
        assertNotNull(value, "无 xdef 定义时应读取同名子节点");
        assertTrue(JsonTool.serialize(value, true).contains("v1"));

        assertNull(ObjXmlValueHelper.getChildValueForDef(node, rootDef, "notExist"),
                "无定义且无同名子节点时返回 null");
    }

    /**
     * node 为 null 时直接返回 null。
     */
    @Test
    public void testNullNodeReturnsNull() {
        assertNull(ObjXmlValueHelper.getChildValueForDef(null, rootDef, "item"));
    }

    /**
     * xdef 定义了 unique-attr 的子节点按集合读取。注意 xdef 约定：
     * 标记 unique-attr 的节点的 bean 属性名缺省为 tagName+'s'（item → items），
     * 因此按属性名查找必须使用 "items"。
     */
    @Test
    public void testCollectionChildValue() {
        XNode node = parse("<root><item name='a'/><item name='b'/></root>");
        Object value = ObjXmlValueHelper.getChildValueForDef(node, rootDef, "items");

        assertTrue(value instanceof java.util.List, "unique-attr 节点必须转为列表，实际: " + value);
        java.util.List<?> list = (java.util.List<?>) value;
        assertEquals(2, list.size());

        Map<?, ?> first = (Map<?, ?>) list.get(0);
        assertEquals("a", first.get("name"), "item 节点按 xdef 转为 {name: ...} 对象");
    }

    /**
     * 集合属性在节点上完全缺失时返回空列表（stream 收集语义，不为 null）。
     */
    @Test
    public void testMissingCollectionChildReturnsEmptyList() {
        XNode node = parse("<root name='x'/>");
        Object value = ObjXmlValueHelper.getChildValueForDef(node, rootDef, "items");
        assertTrue(value instanceof java.util.List, "集合属性缺失时返回空列表，实际: " + value);
        assertTrue(((java.util.List<?>) value).isEmpty());
    }

    /**
     * setChildValueForDef：虚拟列表属性（xmlName == childXmlName）整体替换子节点列表。
     */
    @Test
    public void testSetChildValueForVirtualList() {
        ObjPropMetaImpl propMeta = new ObjPropMetaImpl();
        propMeta.setName("item");
        propMeta.setXmlName("item");
        propMeta.setChildXmlName("item");
        propMeta.setSchema(listOfStringSchema());

        XNode node = parse("<root><item name='old'/></root>");
        ObjXmlValueHelper.setChildValueForDef(node, propMeta, "item",
                Arrays.asList(Collections.singletonMap("name", "n1"),
                        Collections.singletonMap("name", "n2")));

        java.util.List<XNode> items = node.childrenByTag("item");
        assertEquals(2, items.size(), "虚拟列表必须整体替换旧子节点");
        assertEquals("n1", items.get(0).attrText("name"));
        assertEquals("n2", items.get(1).attrText("name"));
    }

    /**
     * setChildValueForDef：value 为 null 的虚拟列表属性清除既有子节点。
     */
    @Test
    public void testSetNullValueRemovesVirtualListChildren() {
        ObjPropMetaImpl propMeta = new ObjPropMetaImpl();
        propMeta.setName("item");
        propMeta.setXmlName("item");
        propMeta.setChildXmlName("item");
        propMeta.setSchema(listOfStringSchema());

        XNode node = parse("<root><item name='old'/></root>");
        ObjXmlValueHelper.setChildValueForDef(node, propMeta, "item", null);

        assertEquals(0, node.childrenByTag("item").size(), "value=null 时应清除既有子节点");
    }

    private static ISchema listOfStringSchema() {
        SchemaImpl itemSchema = new SchemaImpl();
        itemSchema.setType(PredefinedGenericTypes.STRING_TYPE);
        ObjPropMetaImpl nameProp = new ObjPropMetaImpl();
        nameProp.setName("name");
        nameProp.setMandatory(true);
        nameProp.setSchema(itemSchema);

        SchemaImpl objSchema = new SchemaImpl();
        objSchema.setProps(Arrays.asList(nameProp));

        SchemaImpl listSchema = new SchemaImpl();
        listSchema.setItemSchema(objSchema);
        return listSchema;
    }
}
