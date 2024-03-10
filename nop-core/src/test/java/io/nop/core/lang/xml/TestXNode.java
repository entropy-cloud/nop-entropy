/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml;

import io.nop.commons.text.CDataText;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.model.tree.TreeVisitors;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXNode {

    @Test
    public void testFindAll() {
        String text = "<r><c/><a><r/></a><c/></r>";
        XNode node = XNodeParser.instance().parseFromText(null, text);
        List<XNode> list = node.findAllByTag("c");
        assertEquals(2, list.size());

        assertNotNull(node.findByTag("c"));

        assertEquals(1, node.findAllByTag("r").size());
    }

    @Test
    public void testDepthFirstIterator() {
        String text = "<r><c/><a><r/></a><c/></r>";
        XNode node = XNodeParser.instance().parseFromText(null, text);
        Iterator<XNode> it = TreeVisitors.depthFirstIterator(node, true);
        List<String> names = new ArrayList<>();
        it.forEachRemaining(n -> names.add(n.getTagName()));

        assertEquals(Arrays.asList("r", "c", "a", "r", "c"), names);
    }

    @Test
    public void testWidthFirstIterator() {
        String text = "<r><c/><a><r/></a><c/></r>";
        XNode node = XNodeParser.instance().parseFromText(null, text);
        Iterator<XNode> it = TreeVisitors.widthFirstIterator(node, true);
        List<String> names = new ArrayList<>();
        it.forEachRemaining(n -> names.add(n.getTagName()));

        assertEquals(Arrays.asList("r", "c", "a", "c", "r"), names);
    }

    @Test
    public void testInnerXml() {
        XNode node = XNodeParser.instance().parseFromText(null, "<source> \na + b \n</source>");
        assertEquals(" \na + b \n", node.innerXml());
    }

    @Test
    public void testBlank() {
        String s = "<root>\n<child> A</child></root>";
        XNode node = XNodeParser.instance().parseFromText(null, s);
        assertEquals(" A", node.childByTag("child").content().asString());
    }

    @Test
    public void testInnerXml2() {
        String s = "<root>sss&lt;a&gt;</root>";
        XNode node = XNodeParser.instance().parseFromText(null, s);
        assertEquals("sss&lt;a&gt;", node.innerXml(false, false));
    }

    @Test
    public void testInnerXml3() {
        String s = "<root>\n<child>\n    <sub />\n    <sub/></child></root>";
        XNode node = XNodeParser.instance().parseFromText(null, s);
        System.out.println(node.innerXml());
    }

    @Test
    public void testSetInnerXml() {
        String s = "<root>sss&lt;a&gt;</root>";
        XNode node = XNodeParser.instance().parseFromText(null, s);
        node.setInnerXml("abc<b/>");
        node.dump();
        assertEquals("abc<b/>", node.innerXml(true, false));
    }

    @Test
    public void testEscape() {
        String s = "<root value='a\r\nb'>${'\n'}</root>";
        XNode node = XNodeParser.instance().parseFromText(null, s);
        String value = node.content().asString();
        assertEquals("${'\n'}", value);
        value = node.attrText("value");
        assertEquals("a\r\nb", value);
    }

    @Test
    public void testCDATA() {
        String s = "<!DOCTYPE html>\r\n<root> A <![CDATA[B < D ]]> E \n&lt; sss &gt;<sub>USER &#160;</sub><other></other></root>";
        XNode node = XNodeParser.instance().parseFromText(null, s);
        System.out.println(node.getDocType());
        System.out.println(node.outerXml(false, false));
        String xml = node.outerXml(false, false);
        assertEquals("<root><![CDATA[ A B < D  E \n< sss >]]><sub>USER &#160;</sub><other/></root>", xml);
    }

    @Test
    public void testSibling() {
        String s = "<root><child>A</child>B<child>C</child></root>";
        XNode node = XNodeParser.instance().parseFromText(null, s);

        XNode child = node.firstChild();
        assertEquals("A", child.content().asString());

        child = child.nextSibling();
        assertEquals("B", child.content().asString());

        child = child.nextSibling();
        assertEquals("C", child.content().asString());

        assertEquals(child, node.lastChild());

        assertEquals(null, child.nextSibling());

        child = child.prevSibling();
        assertEquals("B", child.content().asString());

        assertEquals(1, child.childIndex());

        child = child.prevSibling();
        assertEquals("A", child.content().asString());

        assertEquals(null, child.prevSibling());
    }

    @Test
    public void testAppend() {
        String s = "<root><child>A</child>B<child>C</child></root>";
        XNode node = XNodeParser.instance().parseFromText(null, s);
        assertEquals(3, node.getChildCount());

        node.appendBodyXml("<div/>");
        node.dump();
        assertEquals(4, node.getChildCount());
        assertEquals("div", node.child(3).getTagName());

        node.prependBodyXml("<v1/>");
        node.dump();
        assertEquals(5, node.getChildCount());
        assertEquals("v1", node.child(0).getTagName());

        XNode child = node.child(1);
        assertEquals("A", child.content().asString());

        child.insertBeforeXml("<s2/>");
        child.getParent().dump();
        assertEquals(6, node.getChildCount());
        assertEquals("s2", child.prevSibling().getTagName());

        child.insertAfterXml("<s3/>");
        child.getParent().dump();
        assertEquals(7, node.getChildCount());
        assertEquals("s3", child.nextSibling().getTagName());
    }

    @Test
    public void testUniqueAttr() {
        XNode node = XNode.make("r");
        node.setAttr("name", "aaa");

        XNode bNode = node.makeChild("b");
        bNode.setAttr("name", "bbb");
        XNode cNode = bNode.makeChild("c");

        String s = cNode.toString();
        System.out.println(s);
        assertTrue(s.indexOf("bbb") > 0);
    }

    @Test
    public void testCDATANested() {
        XNode node = XNode.make("test");
        node.appendContent(new CDataText("<![CDATA[]]>"));

        String xml = node.outerXml(true, true);
        System.out.println(xml);
        node = XNodeParser.instance().parseFromText(null, xml);
        assertEquals("<![CDATA[]]>", node.content().asString());
    }

    @Test
    public void testDepth() {
        XNode node = XNodeParser.instance().parseFromText(null, "<d1><d2><d3/></d2></d1>");
        XNode d3 = node.findByTag("d3");
        assertEquals(3, d3.depth());
        assertEquals(2, d3.getParent().depth());
        assertEquals(d3.getParent(), d3.parent(0));
        assertEquals(d3.getParent().getParent(), d3.parent(1));
    }

    @Test
    public void testCommonAncestor() {
        XNode node = XNodeParser.instance().parseFromText(null,
                "<d1><d2><d3><d4/></d3><d3_2><d4_2/></d3_2></d2><d2_2/></d1>");

        XNode d4 = node.findByTag("d4");
        XNode d3_2 = node.findByTag("d3_2");
        XNode d3 = node.findByTag("d3");
        XNode d2_2 = node.findByTag("d2_2");
        XNode d2 = node.findByTag("d2");

        assertEquals("d2_2", d2_2.getTagName());

        assertNotNull(d4.commonAncestor(d3_2));
        assertEquals(d2, d3_2.commonAncestor(d4));
        assertEquals(d2, d4.commonAncestor(d3_2));
        assertEquals(node, d4.commonAncestor(d2_2));

        assertEquals(node, d2_2.commonAncestor(d4));

        assertEquals(d2, d4.commonAncestor(d3));
        assertEquals(d2, d3.commonAncestor(d4));

        assertTrue(node.contains(d2));
        assertTrue(node.contains(d3));
        assertTrue(!d2.contains(node));
        assertTrue(d3.contains(d4));
        assertTrue(!d2_2.contains(d3_2));
    }

    @Test
    public void testLeaf() {
        XNode node = XNodeParser.instance().parseFromText(null,
                "<d1><d2><d3><d4/></d3><d3_2><d4_2/></d3_2></d2><d2_2/></d1>");

        XNode next = node.nextLeaf();
        assertEquals("d4", next.getTagName());
        next = next.nextLeaf();
        assertEquals("d4_2", next.getTagName());
        next = next.nextLeaf();
        assertEquals("d2_2", next.getTagName());

        assertNull(next.nextLeaf());

        XNode prev = node.prevLeaf();
        assertEquals("d2_2", prev.getTagName());
        prev = prev.prevLeaf();
        assertEquals("d4_2", prev.getTagName());
        prev = prev.prevLeaf();
        assertEquals("d4", prev.getTagName());

        assertNull(prev.prevLeaf());
    }

    @Test
    public void testParseFragments() {
        XNode node = XNodeParser.instance().forFragments(true).parseFromText(null, "aaa");
        assertEquals("aaa", node.contentText());
    }

    @Test
    public void testHexEntity() {
        String str = "<div>&#xFFDD;</div>";
        XNode node = XNodeParser.instance().parseFromText(null, str);
        node.dump();

        XNode node2 = XNodeParser.instance().parseFromText(null, "<div>&#xffdd;</div>");
        assertEquals(node.contentText(), node2.contentText());
    }

    @Test
    public void testUnicode() {
        String str = "<div>&#24207;&#21495;</div>";
        XNode node = XNodeParser.instance().parseFromText(null, str);
        node.dump();
    }
}
