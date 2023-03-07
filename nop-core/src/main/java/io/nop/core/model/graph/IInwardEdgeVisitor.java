/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.graph;

import java.util.List;
import java.util.function.Consumer;

public interface IInwardEdgeVisitor<V, E extends IEdge<V>> {
    List<E> getInwardEdges(V source);

    default void forEachInwardEdge(V source, Consumer<E> action) {
        List<E> edges = getInwardEdges(source);
        if (edges != null) {
            for (E edge : edges) {
                action.accept(edge);
            }
        }
    }
}