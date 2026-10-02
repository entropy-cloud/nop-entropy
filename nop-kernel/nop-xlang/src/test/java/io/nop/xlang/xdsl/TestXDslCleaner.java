package io.nop.xlang.xdsl;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WI3 补强：XDslCleaner（WI0 快照 0% 靶点，45 行）。
 * clean 依据 xdef 元模型剔除 XDSL 节点上的多余属性与未知子节点；removeMergeOp 剥离 x: 名字空间属性。
 */
public class TestXDslCleaner extends BaseTestCase {
    static final String X_NS = "xmlns:x='/nop/schema/xdsl.xdef'";
    static final String SCHEMA_ATTR = "x:schema='/nop/schema/xdef.xdef'";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private XNode parse(String xml) {
        return XNodeParser.instance().parseFromText(null, xml);
    }

    /**
     * clean 清除 xdef 未定义的属性，保留 xdef 定义的属性。
     */
    @Test
    public void testCleanRemovesUnknownAttrs() {
        XNode node = parse("<root " + SCHEMA_ATTR + " " + X_NS + " name='a' unknownAttr='x' count='3'/>");
        XDslCleaner.INSTANCE.cleanForXDef("/test/wi3-clean.xdef", node);

        assertEquals("a", node.attrText("name"));
        assertEquals("3", node.attrText("count"));
        assertNull(node.attrText("unknownAttr"), "xdef 未定义的属性必须被清除");
    }

    /**
     * clean 删除 xdef 未定义的子节点；子节点内容与 xdef 属性同名时回填为属性。
     */
    @Test
    public void testCleanRemovesUnknownChildAndPromotesContentToAttr() {
        XNode node = parse("<root " + SCHEMA_ATTR + " " + X_NS + ">"
                + "<desc>hello</desc>"
                + "<totallyUnknown>ignored</totallyUnknown>"
                + "</root>");
        XDslCleaner.INSTANCE.cleanForXDef("/test/wi3-clean.xdef", node);

        assertNull(node.childByTag("totallyUnknown"), "xdef 未定义的子节点必须被删除");
        assertNull(node.childByTag("desc"), "误生成为子节点的 desc 应被删除");
        assertEquals("hello", node.attrText("desc"),
                "xdef 同名属性存在时，子节点内容应回填为属性");
    }

    /**
     * 已有同名属性时不应被子节点内容覆盖（回填只在属性缺失时发生）。
     */
    @Test
    public void testCleanDoesNotOverwriteExistingAttr() {
        XNode node = parse("<root " + SCHEMA_ATTR + " " + X_NS + " desc='keep'>"
                + "<desc>drop</desc>"
                + "</root>");
        XDslCleaner.INSTANCE.cleanForXDef("/test/wi3-clean.xdef", node);

        assertEquals("keep", node.attrText("desc"), "已存在属性不得被子节点内容覆盖");
        assertNull(node.childByTag("desc"));
    }

    /**
     * xdef 定义的子节点（item，带 unique-attr）在 clean 后保留。
     */
    @Test
    public void testCleanKeepsDefinedChildren() {
        XNode node = parse("<root " + SCHEMA_ATTR + " " + X_NS + ">"
                + "<item name='a'/><item name='b'/>"
                + "</root>");
        XDslCleaner.INSTANCE.cleanForXDef("/test/wi3-clean.xdef", node);

        assertNotNull(node.childByTag("item"), "xdef 定义的子节点必须保留");
        assertEquals(2, node.getChildCount());
        assertEquals("a", node.child(0).attrText("name"));
    }

    /**
     * 内容为省略符 "..." 的节点：内容被清空（表示待填充占位），属性保留。
     */
    @Test
    public void testCleanClearsEllipsisContent() {
        XNode node = parse("<root " + SCHEMA_ATTR + " " + X_NS + " name='a'>...</root>");
        XDslCleaner.INSTANCE.cleanForXDef("/test/wi3-clean.xdef", node);

        assertNull(node.contentText(), "占位内容 ... 必须被清除");
        assertEquals("a", node.attrText("name"));
    }

    /**
     * defNode 为 null 时 clean 直接返回，不做任何修改。
     */
    @Test
    public void testCleanWithNullDefNodeIsNoop() {
        XNode node = parse("<root anything='1'><x/></root>");
        XDslCleaner.INSTANCE.clean(node, null);

        assertEquals("1", node.attrText("anything"), "无 defNode 时不得修改节点");
        assertNotNull(node.childByTag("x"));
    }

    /**
     * removeMergeOp 递归剥离 x: 前缀属性（保留 x:schema）。
     */
    @Test
    public void testRemoveMergeOpStripsXNamespaceAttrs() {
        XNode node = parse("<root " + X_NS + " x:override='remove' x:schema='/nop/schema/xdef.xdef' name='a'>"
                + "<child " + X_NS + " x:abstract='true' x:extends='base' id='c'/>"
                + "</root>");

        XDslCleaner.INSTANCE.removeMergeOp(node);

        assertNull(node.attrText("x:override"), "根节点上的 x:override 必须被剥离");
        assertEquals("/nop/schema/xdef.xdef", node.attrText("x:schema"), "x:schema 必须保留");
        XNode child = node.childByTag("child");
        assertNull(child.attrText("x:abstract"), "子节点上的 x: 属性必须递归剥离");
        assertNull(child.attrText("x:extends"));
        assertEquals("c", child.attrText("id"), "非 x: 名字空间属性不受影响");
    }
}
