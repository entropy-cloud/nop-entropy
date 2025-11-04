/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpath;

import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestXPath {
    @Test
    public void testPath() {
        String xml = "<root><child a='1'>3</child><child b='3' /></root>";
        XNode node = XNodeParser.instance().parseFromText(null, xml);
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
        XNode node = XNodeParser.instance().parseFromText(null, xml);
        IXSelector<XNode> xpath = XPathHelper.parseXSelector("/root/child[@a=='1']");
        node.selectOne(xpath);
    }
}