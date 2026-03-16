import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBeanValuePropSplit extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    @DisplayName("测试同时配置bean-value-prop和bean-body-prop时简单值和子节点分离")
    public void testSplitValueAndBody() {
        String xml = "<TestElement x:schema='/test/test-bean-value-prop.xdef'>"
                + "simple text value"
                + "<item name='a' content='content-a'/>"
                + "<item name='b' content='content-b'/>"
                + "</TestElement>";

        XNode node = XNode.parse(xml);
        Object obj = new DslModelParser().parseFromNode(node);
        assertNotNull(obj);

        Object value = BeanTool.getProperty(obj, "value");
        Object items = BeanTool.getProperty(obj, "items");

        System.out.println("=== Parsed object: " + obj);
        System.out.println("=== value property: " + value);
        System.out.println("=== items property: " + items);

        assertNotNull(value, "value property should not be null when text content exists");
        assertEquals("simple text value", value);
        assertNotNull(items, "items property should not be null when child elements exist");
    }

    @Test
    @DisplayName("测试只有简单值的情况")
    public void testOnlyValue() {
        String xml = "<TestElement x:schema='/test/test-bean-value-prop.xdef'>"
                + "simple text value"
                + "</TestElement>";
        
        XNode node = XNode.parse(xml);
        Object obj = new DslModelParser().parseFromNode(node);
        
        assertNotNull(obj);
        
        Object value = BeanTool.getProperty(obj, "value");
        assertEquals("simple text value", value);
    }

    @Test
    @DisplayName("测试只有子节点的情况")
    public void testOnlyChildren() {
        String xml = "<TestElement x:schema='/test/test-bean-value-prop.xdef'>"
                + "<item name='a' content='content-a'/>"
                + "<item name='b' content='content-b'/>"
                + "</TestElement>";

        XNode node = XNode.parse(xml);
        Object obj = new DslModelParser().parseFromNode(node);

        assertNotNull(obj);

        Object items = BeanTool.getProperty(obj, "items");
        assertNotNull(items);
    }

    @Test
    @DisplayName("测试模型转换回XNode保持一致性")
    public void testModelToXNodeRoundTrip() {
        String xml = "<TestElement x:schema='/test/test-bean-value-prop.xdef'>"
                + "simple text value"
                + "<item name='a' content='content-a'/>"
                + "<item name='b' content='content-b'/>"
                + "</TestElement>";

        XNode node = XNode.parse(xml);
        Object obj = new DslModelParser().parseFromNode(node);

        XNode node2 = DslModelHelper.dslModelToXNode("/test/test-bean-value-prop.xdef", obj);

        assertNotNull(node2);

        String content = node2.content().asString();
        assertNotNull(content);
        assertEquals("simple text value", content.trim());

        assertEquals(2, node2.getChildCount());
    }

    @Test
    @DisplayName("测试空内容只有子节点时value为null")
    public void testEmptyContentWithChildren() {
        String xml = "<TestElement x:schema='/test/test-bean-value-prop.xdef'>"
                + "<item name='a' content='content-a'/>"
                + "</TestElement>";

        XNode node = XNode.parse(xml);
        Object obj = new DslModelParser().parseFromNode(node);

        assertNotNull(obj);

        Object value = BeanTool.getProperty(obj, "value");
        Object items = BeanTool.getProperty(obj, "items");

        assertNull(value, "value should be null when no text content");
        assertNotNull(items, "items should not be null");
    }

    @Test
    @DisplayName("测试只有空白内容时value为null")
    public void testWhitespaceOnlyContent() {
        String xml = "<TestElement x:schema='/test/test-bean-value-prop.xdef'>"
                + "   "
                + "<item name='a' content='content-a'/>"
                + "</TestElement>";

        XNode node = XNode.parse(xml);
        Object obj = new DslModelParser().parseFromNode(node);

        assertNotNull(obj);

        Object value = BeanTool.getProperty(obj, "value");
        Object items = BeanTool.getProperty(obj, "items");

        assertNull(value, "value should be null when content is whitespace only");
        assertNotNull(items, "items should not be null");
    }

    @Test
    @DisplayName("测试子节点列表包含正确的数据")
    public void testChildrenDataCorrectness() {
        String xml = "<TestElement x:schema='/test/test-bean-value-prop.xdef'>"
                + "test value"
                + "<item name='first' content='content-first'/>"
                + "<item name='second' content='content-second'/>"
                + "</TestElement>";

        XNode node = XNode.parse(xml);
        Object obj = new DslModelParser().parseFromNode(node);

        assertNotNull(obj);

        Object items = BeanTool.getProperty(obj, "items");
        assertNotNull(items);
        assertTrue(items instanceof List, "items should be a List");
        
        List<?> itemList = (List<?>) items;
        assertEquals(2, itemList.size(), "items should have 2 elements");

        Object firstItem = itemList.get(0);
        assertEquals("first", BeanTool.getProperty(firstItem, "name"));
        assertEquals("content-first", BeanTool.getProperty(firstItem, "content"));

        Object secondItem = itemList.get(1);
        assertEquals("second", BeanTool.getProperty(secondItem, "name"));
        assertEquals("content-second", BeanTool.getProperty(secondItem, "content"));
    }

    @Test
    @DisplayName("测试往返转换保持数据完整性")
    public void testRoundTripDataIntegrity() {
        String xml = "<TestElement x:schema='/test/test-bean-value-prop.xdef'>\n"
                + "  test content value\n"
                + "  <item name='item1' content='c1'/>\n"
                + "  <item name='item2' content='c2'/>\n"
                + "</TestElement>";

        XNode node = XNode.parse(xml);
        Object obj = new DslModelParser().parseFromNode(node);

        XNode node2 = DslModelHelper.dslModelToXNode("/test/test-bean-value-prop.xdef", obj);

        String content = node2.content().asString();
        assertNotNull(content);
        assertTrue(content.contains("test content value"), 
                "Content should contain 'test content value', but was: " + content);

        assertEquals(2, node2.getChildCount());
        
        XNode item1 = node2.child(0);
        assertEquals("item", item1.getTagName());
        assertEquals("item1", item1.attrText("name"));
        assertEquals("c1", item1.attrText("content"));

        XNode item2 = node2.child(1);
        assertEquals("item", item2.getTagName());
        assertEquals("item2", item2.attrText("name"));
        assertEquals("c2", item2.attrText("content"));
    }
}
