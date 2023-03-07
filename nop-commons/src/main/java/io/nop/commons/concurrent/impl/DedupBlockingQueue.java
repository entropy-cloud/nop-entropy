/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.impl;

import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.IBlockingQueue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 去重的阻塞队列。如果当前队列中已经存在对应记录，则替换该记录。
 *
 * @param <K> 唯一键的类型
 * @param <T> 记录类型
 */
public class DedupBlockingQueue<K, T> implements IBlockingQueue<T> {
    private final IBlockingQueue<T> queue;
    private final Function<T, K> keyFunc;

    private final Map<K, T> map = new ConcurrentHashMap<>();

    public DedupBlockingQueue(IBlockingQueue<T> queue, Function<T, K> keyFunc) {
        this.queue = queue;
        this.keyFunc = keyFunc;
    }

    void checkValid(T o) {
        Guard.notNull(o, "item");
    }

    @Override
    public boolean offer(T o) {
        checkValid(o);

        K key = keyFunc.apply(o);

        // 替换已有的记录
        if (map.put(key, o) != null)
            return false;

        if (!queue.offer(o)) {
            map.remove(key, o);
            return false;
        }
        return true;
    }

    @Override
    public boolean offer(T o, long timeout, TimeUnit unit) throws InterruptedException {
        checkValid(o);

        K key = keyFunc.apply(o);
        if (map.put(key, o) != null)
            return false;

        if (!queue.offer(o, timeout, unit)) {
            map.remove(key, o);
            return false;
        }
        return true;
    }

    @Override
    public T take() throws InterruptedException {
        do {
            T o = queue.take();
            K key = keyFunc.apply(o);

            // 返回的是最新的记录
            T v = map.remove(key);
            // if no value is obtained from cache, it means that the value
            // corresponding to the key has been processed
            if (v != null) {
                return v;
            }
        } while (true);
    }

    @Override
    public T poll() {
        do {
            T o = queue.poll();
            if (o == null)
                return null;

            K key = keyFunc.apply(o);
            T v = map.remove(key);
            if (v != null)
                return v;
        } while (true);
    }

    @Override
    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        do {
            T o = queue.poll(timeout, unit);
            if (o == null)
                return null;

            K key = keyFunc.apply(o);
            T v = map.remove(key);
            if (v != null)
                return v;
        } while (true);
    }

    @Override
    public void clear() {
        queue.clear();
        map.clear();
    }

    @Override
    public void send(T item) throws InterruptedException {
        K key = keyFunc.apply(item);
        if (map.put(key, item) == null)
            queue.send(item);
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }
}