/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
                .parseFromText(null, "<a attr=\"value<test\">content</a>");
        assertEquals("<a attr=\"value&lt;test\">content</a>", attrNode.xml());

        // 3. Test normal XML still works in loose mode
        XNode normalNode = XNodeParser.instance().looseMode(true)
                .parseFromText(null, "<a><b>1</b></a>");
        assertEquals("<a><b>1</b></a>", normalNode.outerXml(false,false));

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
}
