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
        // 变量名不能取裸名 test：maven -Dtest=... 会把 test 设为系统属性，
        // AppConfig.var("test") 在 surefire 过滤运行时读到 truthy 值导致本用例环境敏感地失败
        String xml = "<root> <x:div feature:on='!nop.test.flag-not-set'><child1/><x:div feature:off='nop.test.flag-not-set'><child2/></x:div> </x:div> <child3/></root>";

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

    /**
     * 表达式形式的 feature:off 求值为真时节点应被删除（此前实现误用 onAttr 求值，off 表达式完全失效）
     */
    @Test
    public void testFeatureOffExpressionRemovesNode() {
        String xml = "<root><child1 feature:off='1 &gt; 0'/><child2 feature:off='1 &gt; 1'/></root>";

        XNode node = XNodeParser.instance().parseFromText(null, xml);
        XModelInclude.instance().checkFeatureSwitch(node, new FeatureConditionEvaluator());

        assertEquals(1, node.getChildCount());
        assertEquals("child2", node.child(0).getTagName());
    }

    /**
     * feature:on 为真、feature:off 为未开启的 config var 时节点应被保留（此前实现缺少 else，
     * 回落重复求值 on 表达式导致节点被错误删除）
     */
    @Test
    public void testFeatureOnPassAndOffConfigVarFalsyKeepsNode() {
        String xml = "<root><child feature:on='1 &gt; 0' feature:off='nop.test.feature-off-flag'/></root>";

        XNode node = XNodeParser.instance().parseFromText(null, xml);
        XModelInclude.instance().checkFeatureSwitch(node, new FeatureConditionEvaluator());

        assertEquals(1, node.getChildCount());
        assertEquals("child", node.child(0).getTagName());
    }
}
