/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.feature;

import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFeatureConditionEvaluator {
    @Test
    public void testOr() {
        boolean b = new FeatureConditionEvaluator().evaluate(null, "nop.rpc.service-mesh.enabled or nop.rpc.mock-all");
        assertFalse(b);
    }

    @Test
    public void testVirtualNode() {
        String xml = "<root> <x:div feature:on='!test'><child1/><x:div feature:off='test'><child2/></x:div> </x:div> <child3/></root>";

        XNode node = XNodeParser.instance().parseFromText(null, xml);

        XModelInclude.instance().checkFeatureSwitch(node, new FeatureConditionEvaluator());
        assertEquals(3, node.getChildCount());
        assertEquals("child1", node.child(0).getTagName());
        assertEquals("child2", node.child(1).getTagName());
        assertEquals("child3", node.child(2).getTagName());
    }

    @Test
    public void testFeatureEnableMetaCfg() {
        String xml = "<root feature:enable-meta-cfg='true'><child fetchSize='@meta-cfg:my.fetch-size|10'/></root>";
        XNode node = XNodeParser.instance().parseFromText(null, xml);
        XModelInclude.instance().processNode(node);
        xml = node.xml();
        assertTrue(xml.contains("fetchSize=\"10\""));
    }
}
