package io.nop.commons.collections.iterator;

import io.nop.commons.collections.IterableIterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 支持推回单个元素的迭代器装饰器
 *
 * @param <T> 元素类型
 */
public final class PushbackIterator<T> implements IterableIterator<T> {

    private final Iterator<? extends T> delegate; // 被包装的原始迭代器
    private T buffer;                             // 缓存已被推回的元素
    private boolean buffered;                     // 标记 buffer 是否有效

    public PushbackIterator(Iterator<? extends T> delegate) {
        this.delegate = delegate;
    }

    /**
     * 将最近一次读取的元素推回流
     * 只能缓存一个元素，再次推回会覆盖旧值
     */
    public void pushback(T element) {
        if (buffered) {
            throw new IllegalStateException("Buffer already contains a pushed-back element");
        }
        buffer = element;
        buffered = true;
    }

    @Override
    public boolean hasNext() {
        return buffered || delegate.hasNext();
    }

    @Override
    public T next() {
        if (buffered) {
            T tmp = buffer;
            buffer = null;
            buffered = false;
            return tmp;
        }
        if (!delegate.hasNext()) {
            throw new NoSuchElementException();
        }
        return delegate.next();
    }

    @Override
    public void remove() {
        if (buffered) {
            throw new IllegalStateException("Cannot remove when a pushed-back element exists");
        }
        delegate.remove();
    }
}