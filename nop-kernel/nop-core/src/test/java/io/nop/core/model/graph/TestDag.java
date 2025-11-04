/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.graph;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.graph.dag.Dag;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDag extends BaseTestCase {
    /**
     * <pre>
     *         a
     *       /    \
     *       b    c
     *      / \  /  \
     *     d    e   g
     *     \   /
     *       f
     * </pre>
     */
    @Test
    public void testDag() {
        Dag dag = new Dag("a");
        // 循环链接
        dag.addNextNode("f", "b");

        // 双向链接
        dag.addNextNode("c", "g");
        dag.addNextNode("g", "c");

        dag.addNextNode("a", "b");
        dag.addNextNode("a", "c");
        dag.addNextNode("b", "d");
        dag.addNextNode("b", "e");
        dag.addNextNode("c", "g");
        dag.addNextNode("c", "e");
        dag.addNextNode("d", "f");
        dag.addNextNode("e", "f");

        dag.requireNode("b").setInternal(true);

        dag.analyze(true);

        assertEquals("[[f, b], [g, c]]", dag.getLoopEdges().toString());

        assertEquals("a", dag.getRootNode().getName());

        String json = JsonTool.serialize(dag, true);
        System.out.println(json);
        assertEquals(attachmentJsonText("dag.json"),json);

        System.out.println(dag.toDot());
    }
}
