/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.handler.XNodeHandlerAdapter;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class TestXNodeParser extends BaseTestCase {

    @Test
    public void testLooseModeParser() {
        // 1. Test that unhandled XML entities are preserved as-is in loose mode
        XNode entityNode = XNodeParser.instance().looseMode(true)
                .parseFromText(null, "<a attr=\"&unknown;\">&unknown;</a>");
        assertEquals("<a attr=\"&amp;unknown;\">&amp;unknown;</a>", entityNode.xml());

        // 2. Test that < characters in attribute values are allowed in loose mode
        XNode attrNode = XNodeParser.instance().looseMode(true)
                .parseFromText(null, "<a attr=\"value<test\">content <1</a>");
        assertEquals("<a attr=\"value&lt;test\">content &lt;1</a>", attrNode.xml());

        // 3. Test normal XML still works in loose mode
        XNode normalNode = XNodeParser.instance().looseMode(true)
                .parseFromText(null, "<a><b>1</b></a>");
        assertEquals("<a><b>1</b></a>", normalNode.outerXml(false, false));

        // 4. Test that standard XML entities still work in loose mode
        XNode stdEntityNode = XNodeParser.instance().looseMode(true)
                .parseFromText(null, "<a>&amp;</a>");
        assertEquals("<a>&amp;</a>", stdEntityNode.xml());
    }

    @ParameterizedTest
    @MethodSource
    public void runTest(IFile file) {
        XNode node = XNodeParser.instance().keepComment(true).parseFromResource(file);
        boolean html = file.getName().endsWith(".xhtml");
        String text = node.fullXml(true, false);

        System.out.println(text);

        IResource resultFile = attachmentResource("parse/" + StringHelper.replaceFileExt(file.getName(), ".result"));
        String resultText = ResourceHelper.readText(resultFile, StringHelper.ENCODING_UTF8);
        assertEquals(StringHelper.normalizeCRLF(resultText, false), StringHelper.normalizeCRLF(text, false));

        String compact = node.fullXml(false, false);
        XNode node2 = XNodeParser.instance().keepComment(true).parseFromText(null, compact);
        assertEquals(text, node2.fullXml(true, false));
        assertTrue(node.isXmlEquals(node2));
    }

    // 与参数化测试方法同名的静态方法作为参数工厂
    static Stream<IFile> runTest() {
        TestXNodeParser xs = new TestXNodeParser();
        return xs.attachmentResources("parse", true).stream()
                .filter(file -> file.getName().endsWith(".xml") || file.getName().endsWith(".xhtml"));
    }

    @Test
    public void testWhitespacePreservation() {
        String xml = "<root>\n"
                + "  <t>Normal text</t>\n"
                + "  <t>  Leading spaces</t>\n"
                + "  <t>Trailing spaces  </t>\n"
                + "  <t>\n  Line with indent\n</t>\n"
                + "</root>";

        XNode nodeWithWhitespace = XNodeParser.instance().keepWhitespace(true).parseFromText(null, xml);
        XNode nodeWithoutWhitespace = XNodeParser.instance().keepWhitespace(false).parseFromText(null, xml);

        List<XNode> childrenWithWs = nodeWithWhitespace.childrenByTag("t");
        List<XNode> childrenWithoutWs = nodeWithoutWhitespace.childrenByTag("t");

        assertEquals("Normal text", childrenWithWs.get(0).text());
        assertEquals("  Leading spaces", childrenWithWs.get(1).text());
        assertEquals("Trailing spaces  ", childrenWithWs.get(2).text());
        assertTrue(childrenWithWs.get(3).text().contains("\n  "));

        assertEquals("Normal text", childrenWithoutWs.get(0).text());
        assertEquals("  Leading spaces", childrenWithoutWs.get(1).text());
        assertEquals("Trailing spaces  ", childrenWithoutWs.get(2).text());
        assertTrue(childrenWithoutWs.get(3).text().contains("\n  "));
    }

    @Test
    public void testMultilineTextWithLeadingSpaces() {
        String xml = "<si><t>Line1\n"
                + "  Line2 with indent\n"
                + "    Line3 with more indent</t></si>";

        XNode node = XNodeParser.instance().keepWhitespace(true).parseFromText(null, xml);
        XNode t = node.childByTag("t");

        String text = t.text();
        assertTrue(text.startsWith("Line1\n  "));
        assertTrue(text.contains("\n    Line3"));
    }

    @Test
    public void testXmlSpacePreserveNested() {
        String xml = "<root>\n"
                + "  <outer xml:space=\"preserve\">\n"
                + "    <inner>\n"
                + "      \n"
                + "    </inner>\n"
                + "  </outer>\n"
                + "</root>";

        XNode node = XNodeParser.instance().keepWhitespace(false).parseFromText(null, xml);
        XNode outer = node.childByTag("outer");
        XNode inner = outer.childByTag("inner");

        assertTrue(inner.hasContent());
        assertTrue(inner.text().contains("\n"));
    }

    @Test
    public void testXmlSpacePreserveScope() {
        String xml = "<root>\n"
                + "  <a xml:space=\"preserve\">\n"
                + "    \n"
                + "  </a>\n"
                + "  <b>\n"
                + "    \n"
                + "  </b>\n"
                + "</root>";

        XNode node = XNodeParser.instance().keepWhitespace(false).parseFromText(null, xml);
        XNode a = node.childByTag("a");
        XNode b = node.childByTag("b");

        assertTrue(a.hasContent());
        assertTrue(!b.hasContent());
    }

    @Test
    public void testXmlSpaceDefault() {
        String xml = "<root>\n"
                + "  <outer xml:space=\"preserve\">\n"
                + "    <a>\n"
                + "      \n"
                + "    </a>\n"
                + "    <b xml:space=\"default\">\n"
                + "      \n"
                + "    </b>\n"
                + "    <c>\n"
                + "      \n"
                + "    </c>\n"
                + "  </outer>\n"
                + "</root>";

        XNode node = XNodeParser.instance().keepWhitespace(false).parseFromText(null, xml);
        XNode outer = node.childByTag("outer");

        assertTrue(outer.childByTag("a").hasContent());
        assertTrue(!outer.childByTag("b").hasContent());
        assertTrue(outer.childByTag("c").hasContent());
    }

    @Test
    public void testXmlSpacePreserveLeadingSpaces() {
        XNode node = XNodeParser.instance().keepWhitespace(false)
                .parseFromText(null, "<si><t xml:space=\"preserve\">  leading spaces</t></si>");
        assertEquals("  leading spaces", node.childByTag("t").text());
    }

    @Test
    public void testXmlSpacePreserveWithRun() {
        String xml = "<si>\n"
                + "  <r>\n"
                + "    <t xml:space=\"preserve\">  part1  </t>\n"
                + "  </r>\n"
                + "  <r>\n"
                + "    <t>part2</t>\n"
                + "  </r>\n"
                + "</si>";

        List<XNode> tNodes = collectTNodes(XNodeParser.instance().keepWhitespace(false).parseFromText(null, xml));
        assertEquals("  part1  ", tNodes.get(0).text());
        assertEquals("part2", tNodes.get(1).text());
    }

    @Test
    public void testXmlSpacePreservePureWhitespace() {
        XNode node = XNodeParser.instance().keepWhitespace(false)
                .parseFromText(null, "<si><t xml:space=\"preserve\">   </t></si>");
        XNode t = node.childByTag("t");
        assertTrue(t.hasContent());
        assertEquals("   ", t.text());
    }

    @Test
    public void testXmlSpacePreserveTrailingSpaces() {
        XNode node = XNodeParser.instance().keepWhitespace(false)
                .parseFromText(null, "<si><t xml:space=\"preserve\">trailing   </t></si>");
        assertEquals("trailing   ", node.childByTag("t").text());
    }

    @Test
    public void testXmlSpacePreserveWithHandler() {
        List<String> collectedTexts = new ArrayList<>();
        XNodeParser.instance().keepWhitespace(false).handler(new XNodeHandlerAdapter() {
            private boolean tIsOpen;

            @Override
            public void beginNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {
                if ("t".equals(tagName)) {
                    tIsOpen = true;
                }
            }

            @Override
            public void endNode(String tagName) {
                if ("t".equals(tagName)) {
                    tIsOpen = false;
                }
            }

            @Override
            public void text(SourceLocation loc, String text) {
                if (tIsOpen) {
                    collectedTexts.add(text);
                }
            }
        }).parseFromText(null, "<si><t xml:space=\"preserve\">  leading text  </t></si>");

        assertEquals(1, collectedTexts.size());
        assertEquals("  leading text  ", collectedTexts.get(0));
    }

    @Test
    public void testXmlSpacePreserveMultipleRunWithHandler() {
        List<String> collectedTexts = new ArrayList<>();
        XNodeParser.instance().keepWhitespace(false).handler(new XNodeHandlerAdapter() {
            private boolean tIsOpen;

            @Override
            public void beginNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {
                if ("t".equals(tagName)) {
                    tIsOpen = true;
                }
            }

            @Override
            public void endNode(String tagName) {
                if ("t".equals(tagName)) {
                    tIsOpen = false;
                }
            }

            @Override
            public void text(SourceLocation loc, String text) {
                if (tIsOpen) {
                    collectedTexts.add(text);
                }
            }
        }).parseFromText(null, "<si>\n"
                + "  <r><t xml:space=\"preserve\">  part1  </t></r>\n"
                + "  <r><t>part2</t></r>\n"
                + "</si>");

        assertEquals(2, collectedTexts.size());
        assertEquals("  part1  ", collectedTexts.get(0));
        assertEquals("part2", collectedTexts.get(1));
    }

    private List<XNode> collectTNodes(XNode node) {
        List<XNode> result = new ArrayList<>();
        collectTNodes(node, result);
        return result;
    }

    private void collectTNodes(XNode node, List<XNode> result) {
        if ("t".equals(node.getTagName())) {
            result.add(node);
        }
        for (XNode child : node.getChildren()) {
            collectTNodes(child, result);
        }
    }
}
