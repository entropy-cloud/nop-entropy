/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 保留最大的K个元素。按照从小到大的顺序排列
 *
 * @param <E>
 */
public class FixedPriorityQueue<E> implements Iterable<E> {
    private final int capacity;
    private final AtomicInteger count = new AtomicInteger();
    private final PriorityBlockingQueue<E> queue;

    public FixedPriorityQueue(int capacity, Comparator<? super E> comparator) {
        this.capacity = capacity;
        this.queue = new PriorityBlockingQueue<>(capacity + 2, comparator);
    }

    public int getCapacity() {
        return capacity;
    }

    public int getCount() {
        return count.get();
    }

    @Override
    public Iterator<E> iterator() {
        return queue.iterator();
    }

    public void add(E e) {
        int n = count.incrementAndGet();
        if (n > capacity) {
            count.decrementAndGet();
        } else {
            queue.add(e);
        }
    }

    public boolean remove(E e) {
        boolean b = queue.remove(e);
        if (b)
            count.decrementAndGet();
        return b;
    }

    public List<E> toList() {
        List<E> ret = new ArrayList<>(queue);
        return ret;
    }
}