/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * 可以不断拉取到数据的输入队列
 *
 * @param <T>
 */
public interface IBlockingSource<T> {

    /**
     * 阻塞获取队首记录
     *
     * @return
     * @throws InterruptedException
     */
    T take() throws InterruptedException;

    /**
     * 如果队列为空，则返回null
     *
     * @return
     */
    T poll();

    /**
     * 阻塞一段时间获取记录
     *
     * @param timeout
     * @param unit
     * @return 如果在规定时间内没有获取到记录，则返回null
     * @throws InterruptedException
     */
    T poll(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * 将当前队列中的记录批量读取到集合中
     *
     * @param c
     * @param maxElements
     * @return 实际读取的记录个数
     */
    default int drainTo(Collection<? super T> c, int maxElements) {
        if (maxElements <= 0)
            return 0;

        for (int i = 0; i < maxElements; i++) {
            T item = poll();
            if (item == null)
                return i;
            c.add(item);
        }
        return maxElements;
    }

    /**
     * 从队列中批量获取一批数据。如果返回的数据少于maxElements, 则允许等待一段时间。 返回时要么超时时间已到，要么获取到的数据条目数为maxElements。
     *
     * @param c
     * @param maxElements
     * @param minWait     如果没有获取到足够多的对象，则可以继续等待一段时间。等待此时间后，如果能够获取到一些对象，则返回。
     * @param maxWait     无论是否获取到对象，超过此时间都要返回
     * @return
     * @throws InterruptedException
     */
    default int drainTo(Collection<? super T> c, int maxElements, long minWait, long maxWait)
            throws InterruptedException {
        if (maxElements <= 0)
            return 0;

        int n = drainTo(c, maxElements);
        if (n >= maxElements)
            return n;

        Guard.nonNegativeLong(maxWait, "maxWait should not be negative");
        Guard.nonNegativeLong(minWait, "minWait should not be negative");
        if (minWait > maxWait)
            minWait = maxWait;

        long begin = CoreMetrics.nanoTime();
        long leftTimeout = minWait;
        int i;
        for (i = 0; i < maxElements; i++) {
            T item = poll(i == 0 ? maxWait : leftTimeout, TimeUnit.MILLISECONDS);
            if (item == null)
                break;
            c.add(item);
            if (i == maxElements - 1)
                break;
            i += drainTo(c, maxElements - i);

            long diff = CoreMetrics.nanoTimeDiff(begin);
            leftTimeout = minWait - TimeUnit.MILLISECONDS.convert(diff, TimeUnit.NANOSECONDS);
            if (leftTimeout < 0)
                break;
        }
        return i;
    }

    /**
     * 阻塞接收
     *
     * @param items
     * @param maxCount
     * @return 实际接收的元素个数
     */
    default int takeMulti(Collection<? super T> items, int maxCount) throws InterruptedException {
        T item = take();
        items.add(item);
        if (maxCount <= 1) {
            return 1;
        }
        int n = drainTo(items, maxCount - 1);
        return n + 1;
    }
}