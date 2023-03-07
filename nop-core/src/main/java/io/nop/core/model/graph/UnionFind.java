/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.graph;

import io.nop.commons.util.CollectionHelper;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 并查集是一种树型的数据结构，用于处理一些不交集（Disjoint Sets）的合并及查询问题。
 * <p>
 * Find：确定元素属于哪一个子集。它可以被用来确定两个元素是否属于同一子集。 Union：将两个子集合并成同一个集合。
 */
public class UnionFind<V> {
    private final Map<V, V> parentMap;
    private final Map<V, Integer> rankMap;

    // 不相交的集合的个数
    private int setCount;

    public UnionFind() {
        this.parentMap = new LinkedHashMap<>();
        this.rankMap = new HashMap<>();
    }

    public UnionFind(Collection<V> vertices) {
        this.parentMap = CollectionHelper.newLinkedHashMap(vertices.size());
        this.rankMap = CollectionHelper.newHashMap(vertices.size());
        for (V vertex : vertices) {
            addVertex(vertex);
        }
    }

    public int getSetCount() {
        return setCount;
    }

    public boolean addVertex(V vertex) {
        if (parentMap.putIfAbsent(vertex, vertex) == null) {
            rankMap.put(vertex, 0);
            setCount++;
            return true;
        }
        return false;
    }

    /**
     * 找到局部树的根节点
     */
    public V find(V vertex) {

        V parent = parentMap.get(vertex);
        if (parent == null)
            return null;

        if (parent.equals(vertex))
            return parent;

        V current = parent;
        do {
            parent = parentMap.get(current);
            if (parent.equals(current)) {
                break;
            }
            current = parent;
        } while (true);

        final V root = current;

        // 路径压缩，把路径上的所有节点都和根节点建立直接关联
        current = vertex;
        while (!current.equals(root)) {
            parent = parentMap.get(current);
            parentMap.put(current, root);
            current = parent;
        }

        return root;
    }

    public void union(V v1, V v2) {
        V parent1 = find(v1);
        V parent2 = find(v2);

        if (parent1 == null || parent2 == null)
            return;

        if (parent1.equals(parent2)) {
            return;
        }

        int rank1 = rankMap.get(parent1);
        int rank2 = rankMap.get(parent2);
        if (rank1 > rank2) {
            parentMap.put(parent2, parent1);
        } else if (rank1 < rank2) {
            parentMap.put(parent1, parent2);
        } else {
            parentMap.put(parent2, parent1);
            rankMap.put(parent1, rank1 + 1);
        }

        setCount--;
    }
}
