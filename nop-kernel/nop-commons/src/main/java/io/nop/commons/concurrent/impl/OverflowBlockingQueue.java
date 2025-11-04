/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.IBlockingQueue;
import io.nop.commons.concurrent.IPeekableSource;
import io.nop.commons.concurrent.QueueOverflowPolicy;
import io.nop.commons.util.DestroyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.nop.commons.CommonErrors.ERR_QUEUE_FULL;

public class OverflowBlockingQueue<T> implements IBlockingQueue<T>, IPeekableSource<T> {
    static final Logger LOG = LoggerFactory.getLogger(OverflowBlockingQueue.class);

    private final int capacity;
    private final BlockingQueue<T> queue;
    private final QueueOverflowPolicy overflowPolicy;

    private final Consumer<T> removeProcessor;

    public OverflowBlockingQueue(int capacity, QueueOverflowPolicy overflowPolicy, Consumer<T> removeProcessor,
                                 BlockingQueue<T> queue) {
        this.capacity = capacity;
        this.overflowPolicy = overflowPolicy;
        this.queue = queue;
        this.removeProcessor = removeProcessor;
    }

    public OverflowBlockingQueue(int capacity, QueueOverflowPolicy overflowPolicy) {
        this(capacity, overflowPolicy, null, new ArrayBlockingQueue<>(capacity));
    }

    public OverflowBlockingQueue(int capacity) {
        this(capacity, QueueOverflowPolicy.BLOCK_WAIT);
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public void clear() {
        List<T> items = new ArrayList<T>();
        queue.drainTo(items);
        for (T item : items) {
            onRemoveItem(item);
        }
    }

    protected void onRemoveItem(T item) {
        if (this.removeProcessor != null) {
            this.removeProcessor.accept(item);
        } else {
            DestroyHelper.safeDestroy(item);
        }
    }

    @Override
    public boolean offer(T o) {
        return queue.offer(o);
    }

    @Override
    public boolean offer(T o, long timeout, TimeUnit unit) throws InterruptedException {
        return queue.offer(o, timeout, unit);
    }

    @Override
    public T poll() {
        return queue.poll();
    }

    @Override
    public T peek() {
        return queue.peek();
    }

    @Override
    public T take() throws InterruptedException {
        return queue.take();
    }

    @Override
    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    @Override
    public int drainTo(Collection<? super T> c, int maxElements) {
        return queue.drainTo(c, maxElements);
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public void send(T item) throws InterruptedException {
        if (overflowPolicy == QueueOverflowPolicy.BLOCK_WAIT) {
            queue.put(item);
        } else if (overflowPolicy == QueueOverflowPolicy.DROP_NEWEST) {
            if (!offer(item)) {
                onRemoveItem(item);
            }
        } else if (overflowPolicy == QueueOverflowPolicy.DROP_ELDEST) {
            while (!offer(item)) {
                T t = poll();
                if (t != null)
                    onRemoveItem(t);
            }
        } else {
            if (!offer(item)) {
                onRemoveItem(item);
                throw new NopException(ERR_QUEUE_FULL);
            }
        }
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isNextAvailable() {
        return !queue.isEmpty();
    }
}