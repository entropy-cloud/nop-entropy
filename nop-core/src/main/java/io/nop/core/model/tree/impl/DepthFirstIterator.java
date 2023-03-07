/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.tree.impl;

import io.nop.commons.collections.IterableIterator;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.model.tree.ITreeChildrenAdapter;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

public class DepthFirstIterator<T> implements IterableIterator<T> {

    private final Deque<T> deque;
    private final Predicate<? super T> filter;
    private final ITreeChildrenAdapter<T> adapter;

    T peek;

    public DepthFirstIterator(ITreeChildrenAdapter<T> adapter, T root, boolean includeRoot,
                              Predicate<? super T> filter) {
        this.adapter = adapter;
        deque = new ArrayDeque<>();

        fillQueue(root, includeRoot, filter);

        this.filter = filter;
        peek = _next();
    }

    void fillQueue(T root, boolean includeRoot, Predicate<? super T> filter) {
        if (includeRoot) {
            if (filter == null || filter.test(root))
                deque.addLast(root);
            return;
        }
        Iterable<? extends T> children = adapter.getChildren(root);
        if (children == null)
            children = Collections.emptyList();
        for (T child : children) {
            if (filter == null || filter.test(child))
                deque.addLast(child);
        }
    }

    @Override
    public boolean hasNext() {
        return peek != null;
    }

    @Override
    public T next() {
        T ret = peek;
        peek = _next();
        return ret;
    }

    T _next() {
        T o = deque.pollFirst();
        if (o == null)
            return null;
        List<T> children = CollectionHelper.toList(adapter.getChildren(o));
        if (children != null) {
            // 将o替换为它的子节点。因此只有当所有子节点都返回之后，才会返回后面的兄弟节点
            for (int i = children.size() - 1; i >= 0; i--) {
                T child = children.get(i);
                if (filter == null || filter.test(child))
                    deque.addFirst(child);
            }
        }
        return o;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove");
    }
}
