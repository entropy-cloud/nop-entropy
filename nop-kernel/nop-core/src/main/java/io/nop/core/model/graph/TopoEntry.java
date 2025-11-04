/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.graph;

/**
 * 拓扑排序的结果
 *
 * @author canonical_entropy@163.com
 */
public class TopoEntry<T> implements Comparable<TopoEntry<T>> {
    private final int topoOrder;
    private final T value;

    public TopoEntry(int topoOrder, T value) {
        this.topoOrder = topoOrder;
        this.value = value;
    }

    public int getTopoOrder() {
        return topoOrder;
    }

    public T getValue() {
        return value;
    }

    @Override
    public int compareTo(TopoEntry<T> o) {
        int d = topoOrder - o.topoOrder;
        return d;
    }
}