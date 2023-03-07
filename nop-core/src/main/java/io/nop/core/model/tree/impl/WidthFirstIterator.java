/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.tree.impl;

import io.nop.commons.collections.IterableIterator;
import io.nop.core.model.tree.ITreeChildrenAdapter;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.function.Predicate;

public class WidthFirstIterator<T> implements IterableIterator<T> {

    private final Deque<T> deque;
    private final Predicate<? super T> filter;
    private final ITreeChildrenAdapter<T> adapter;

    T peek;

    public WidthFirstIterator(ITreeChildrenAdapter<T> adapter, T root, boolean includeRoot,
                              Predicate<? super T> filter) {
        this.adapter = adapter;
        deque = new ArrayDeque<>();
        if (includeRoot) {
            deque.add(root);
        } else {
            Iterable<? extends T> children = adapter.getChildren(root);
            if (children == null)
                children = Collections.emptyList();
            for (T child : children) {
                if (filter == null || filter.test(child))
                    deque.addLast(child);
            }
        }
        this.filter = filter;
        next();
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

        Iterable<? extends T> children = adapter.getChildren(o);
        if (children != null) {
            // 子节点追加到最后，所以会先遍历兄弟节点，再遍历子节点
            for (T child : children) {
                if (filter == null || filter.test(child))
                    deque.addLast(child);
            }
        }
        return o;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove");
    }
}
