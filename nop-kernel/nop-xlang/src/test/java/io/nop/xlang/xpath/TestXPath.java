/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpath;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static io.nop.xlang.XLangErrors.ERR_XPATH_ROOT_NOT_ALLOW_PARENT_SELECTOR;
import static io.nop.xlang.XLangErrors.ERR_XPATH_UNKNOWN_OPERATOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXPath {
    private XNode parse(String xml) {
        return XNodeParser.instance().parseFromText(null, xml);
    }

    @Test
    public void testPath() {
        String xml = "<root><child a='1'>3</child><child b='3' /></root>";
        XNode node = parse(xml);
        IXSelector<XNode> xpath = XPathHelper.parseXSelector("/root/child/@a");
        assertEquals("1", node.selectOne(xpath));

        xpath = XPathHelper.parseXSelector("/root/child/$value");
        assertEquals("3", node.selectOne(xpath));

        xpath = XPathHelper.parseXSelector("//child");
        assertEquals("1", ((XNode) node.selectOne(xpath)).attrText("a"));

        assertEquals(2, node.selectMany(xpath).size());
    }

    @Test
    public void testAttrFilter() {
        BaseTestCase.forceStackTrace();
        String xml = "<root><child a='1'>3</child><child b='3' /></root>";
        XNode node = parse(xml);
        IXSelector<XNode> xpath = XPathHelper.parseXSelector("/root/child[@a=='1']");
        assertEquals("3", ((XNode) node.selectOne(xpath)).contentText());
    }

    @Test
    public void testOperatorTag() {
        XNode node = parse("<root><child a='1'>3</child></root>");
        IXSelector<XNode> xpath = XPathHelper.parseXSelector("/root/$tag");
        assertEquals("root", node.selectOne(xpath));
    }

    @Test
    public void testOperatorXml() {
        XNode node = parse("<root><child a='1'>3</child></root>");
        IXSelector<XNode> xpath = XPathHelper.parseXSelector("/root/$xml");
        Object value = node.selectOne(xpath);
        assertTrue(value instanceof String);
        String xml = (String) value;
        assertTrue(xml.startsWith("<root"), xml);
        assertTrue(xml.contains("child"), xml);
        assertTrue(xml.trim().endsWith("</root>"), xml);
    }

    @Test
    public void testOperatorInnerXml() {
        XNode node = parse("<root><child a='1'>3</child></root>");
        IXSelector<XNode> xpath = XPathHelper.parseXSelector("/root/child/$innerXml");
        assertEquals("3", node.selectOne(xpath));
    }

    @Test
    public void testOperatorHtml() {
        XNode node = parse("<root><child a='1'>3</child></root>");
        IXSelector<XNode> xpath = XPathHelper.parseXSelector("/root/$html");
        Object value = node.selectOne(xpath);
        assertTrue(value instanceof String);
        String html = (String) value;
        assertTrue(html.startsWith("<root"), html);
        assertTrue(html.contains("child"), html);
        assertTrue(html.trim().endsWith("</root>"), html);
    }

    @Test
    public void testOperatorInnerHtml() {
        XNode node = parse("<root><child a='1'>3</child></root>");
        IXSelector<XNode> xpath = XPathHelper.parseXSelector("/root/$innerHtml");
        Object value = node.selectOne(xpath);
        assertTrue(value instanceof String);
        String html = (String) value;
        assertTrue(html.contains("child"), html);
        assertTrue(!html.trim().startsWith("<root"), html);
    }

    @Test
    public void testOperatorNode() {
        XNode node = parse("<root><child a='1'>3</child></root>");
        IXSelector<XNode> xpath = XPathHelper.parseXSelector("/root/$node");
        Object value = node.selectOne(xpath);
        assertTrue(value instanceof XNode);
        assertSame(node, value);
    }

    @Test
    public void testPipeUnion() {
        XNode node = parse("<root><sub><child id='1'/><child id='2'/></sub><child id='3'/></root>");
        IXSelector<XNode> xpath = XPathHelper.parseXSelector("//sub/child|/root/child");
        Collection<?> result = node.selectMany(xpath);
        assertEquals(3, result.size());
        int i = 1;
        for (Object o : result) {
            XNode child = (XNode) o;
            assertEquals(String.valueOf(i), child.attrText("id"));
            i++;
        }
    }

    @Test
    public void testUnknownOperator() {
        NopException e = assertThrows(NopException.class, () -> XPathHelper.parseXSelector("/root/$foo"));
        assertEquals(ERR_XPATH_UNKNOWN_OPERATOR.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testParentOnRoot() {
        XNode node = parse("<root/>");
        IXSelector<XNode> xpath = XPathHelper.parseXSelector("/root/..");
        NopException e = assertThrows(NopException.class, () -> node.selectOne(xpath));
        assertEquals(ERR_XPATH_ROOT_NOT_ALLOW_PARENT_SELECTOR.getErrorCode(), e.getErrorCode());
    }
}
