/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.graph;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public interface ITargetVertexView<V> extends ITargetVertexVisitor<V> {
    Set<V> vertexSet();

    default boolean isEmpty() {
        return vertexSet().isEmpty();
    }

    default boolean containsVertex(V vertex) {
        return vertexSet().contains(vertex);
    }

    default TopologicalOrderIterator<V> topologicalOrderIterator(boolean allowLoop) {
        return new TopologicalOrderIterator(vertexSet(), this, allowLoop);
    }

    default Set<V> reachableVertexes(V vertex) {
        Set<V> set = new LinkedHashSet<>();
        GraphBreadthFirstIterator.reachable(set, this, vertex);
        return set;
    }

    default Set<V> findCycles() {
        return new CycleDetector<>(this).findCycles();
    }

    default Iterator<V> depthFirstIterator(V start) {
        return new GraphDepthFirstIterator<>(this, start);
    }

    default Iterator<V> breadthFirstIterator(V start) {
        return new GraphBreadthFirstIterator<>(this, start);
    }

}