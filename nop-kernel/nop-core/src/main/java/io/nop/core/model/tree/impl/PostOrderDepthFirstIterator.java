package io.nop.core.model.tree.impl;

import io.nop.commons.collections.IterableIterator;
import io.nop.core.model.tree.ITreeChildrenAdapter;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

public class PostOrderDepthFirstIterator<T> implements IterableIterator<T> {
    private final ITreeChildrenAdapter<T> adapter;
    private final Predicate<? super T> filter;
    private final Deque<IteratorFrame<T>> stack = new ArrayDeque<>();
    private T next;
    private final boolean filterEntireSubtree;

    public PostOrderDepthFirstIterator(ITreeChildrenAdapter<T> adapter, T root, boolean includeRoot, Predicate<? super T> filter) {
        this(adapter, root, includeRoot, filter, false);
    }

    public PostOrderDepthFirstIterator(ITreeChildrenAdapter<T> adapter, T root, boolean includeRoot,
                                       Predicate<? super T> filter, boolean filterEntireSubtree) {
        this.adapter = adapter;
        this.filter = filter;
        this.filterEntireSubtree = filterEntireSubtree;

        if (includeRoot) {
            if (shouldInclude(root)) {
                stack.push(new IteratorFrame<>(Collections.singleton(root).iterator(), null));
            }
        } else {
            Iterable<? extends T> children = adapter.getChildren(root);
            if (children != null) {
                stack.push(new IteratorFrame<>(children.iterator(), null));
            }
        }
        advance();
    }

    private boolean shouldInclude(T node) {
        if (filter == null) {
            return true;
        }
        return filter.test(node);
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public T next() {
        if (next == null) {
            throw new NoSuchElementException();
        }
        T result = next;
        advance();
        return result;
    }

    private void advance() {
        next = null;
        while (!stack.isEmpty()) {
            IteratorFrame<T> frame = stack.peek();

            if (frame.iterator != null && frame.iterator.hasNext()) {
                // 处理子节点
                T child = frame.iterator.next();

                // 如果启用子树过滤且当前节点被过滤，则跳过整个子树
                if (filterEntireSubtree && filter != null && !filter.test(child)) {
                    continue; // 跳过这个子节点及其所有后代
                }

                Collection<? extends T> grandchildren = adapter.getChildren(child);
                if (grandchildren != null && !grandchildren.isEmpty()) {
                    stack.push(new IteratorFrame<>(grandchildren.iterator(), child));
                } else {
                    // 叶子节点，检查是否应该包含
                    if (shouldInclude(child)) {
                        next = child;
                        return;
                    }
                }
            } else {
                // 当前节点的所有子节点都已处理完毕，处理当前节点
                stack.pop();
                if (frame.node != null && shouldInclude(frame.node)) {
                    next = frame.node;
                    return;
                }
            }
        }
    }

    private static class IteratorFrame<T> {
        final Iterator<? extends T> iterator;
        final T node;

        IteratorFrame(Iterator<? extends T> iterator, T node) {
            this.iterator = iterator;
            this.node = node;
        }
    }

    @Override
    public Iterator<T> iterator() {
        return this;
    }
}