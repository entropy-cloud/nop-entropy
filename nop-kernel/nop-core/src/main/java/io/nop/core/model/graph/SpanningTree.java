/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.graph;

import io.nop.commons.util.CollectionHelper;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SpanningTree<V, E extends IEdge<V>> {
    private final List<E> edges;

    public SpanningTree(List<E> edges) {
        this.edges = edges;
    }

    public Map<V, V> getParentMap() {
        Map<V, V> map = CollectionHelper.newHashMap(edges.size());
        for (E edge : edges) {
            map.put(edge.getTarget(), edge.getSource());
        }
        return map;
    }

    public Set<V> getRoots() {
        Set<V> set = new LinkedHashSet<>();
        for (E edge : edges) {
            set.add(edge.getSource());
        }

        for (E edge : edges) {
            set.remove(edge.getTarget());
        }
        return set;
    }

    public Map<V, Set<V>> getChildrenMap() {
        Map<V, Set<V>> map = new HashMap<>();
        for (E edge : edges) {
            V source = edge.getSource();
            Set<V> set = map.get(source);
            if (set == null) {
                set = new LinkedHashSet<>();
                map.put(source, set);
            }
            set.add(edge.getTarget());
        }
        return map;
    }
}