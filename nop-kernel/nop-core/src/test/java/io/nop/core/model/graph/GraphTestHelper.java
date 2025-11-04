/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.graph;

public class GraphTestHelper {
    /**
     * 创建图模型的帮助函数。每个link是一个单向路径。 比如newGraph(new String[]{"a", "b", "c", "d", "e", "f"}, new String[]{"b", "d", "x"})
     * 表示如下连接: a -> b -> c -> d -> e -> f b ------> d -> x
     */
    public static StringGraph newGraph(String[]... links) {
        StringGraph g = new StringGraph();
        for (String[] link : links) {
            for (int i = 0, n = link.length; i < n; i++) {
                String p = link[i];
                if (!g.containsVertex(p)) {
                    g.addVertex(p);
                }
                if (i != 0) {
                    String prev = link[i - 1];
                    if (!g.containsEdge(prev, p)) {
                        g.addEdge(prev, p);
                    }
                }
            }
        }
        return g;
    }
}
