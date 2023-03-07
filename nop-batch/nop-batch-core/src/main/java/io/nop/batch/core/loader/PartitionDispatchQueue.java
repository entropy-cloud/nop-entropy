/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core.loader;

import io.nop.api.core.exceptions.NopBreakException;
import io.nop.api.core.util.Guard;
import io.nop.commons.collections.IntHashMap;
import io.nop.commons.collections.MapOfInt;
import io.nop.commons.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * 对每条记录计算得到一个partitionIndex，按照partitionIndex将记录拆分到多个队列中，每个队列只有一个线程负责处理
 */
public class PartitionDispatchQueue<T> {
    static final Logger LOG = LoggerFactory.getLogger(PartitionDispatchQueue.class);

    static class PartitionQueue<T> {
        int threadIndex = -1;
        final Queue<T> queue = new ArrayDeque<>();
    }

    private final Semaphore semaphore;
    private final IntHashMap<PartitionQueue<T>> partitions = new IntHashMap<>();
    private final Function<T, Integer> partitionFn;

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition notEmpty = lock.newCondition();

    /**
     * 当前尚未被处理的记录数
     */
    private int count;

    private final int capacity;
    /**
     * 是否所有记录已经处理完毕
     */
    private volatile boolean finished = false;

    public PartitionDispatchQueue(int capacity, Function<T, Integer> partitionFn) {
        this.semaphore = new Semaphore(capacity);
        this.capacity = capacity;
        this.partitionFn = partitionFn;
    }

    public int getCapacity(){
        return capacity;
    }

    public MapOfInt<List<T>> takeBatch(int batchSize, int threadIndex) {
        Guard.checkArgument(batchSize > 0, "batchSize must be non negative");
        MutableInt remainSize = new MutableInt(batchSize);
        MapOfInt<List<T>> ret = new IntHashMap<>();

        do {
            lock.lock();
            try {
                try {
                    partitions.randomForEachEntry((queue, index) -> {
                        // threadIndex如果大于等于0，则表示此partition正被某个线程处理，需要被跳过
                        if (queue.threadIndex < 0) {
                            Queue<T> q = queue.queue;
                            if (!q.isEmpty()) {
                                List<T> list;
                                if (q.size() <= remainSize.get()) {
                                    list = new ArrayList<>(q);
                                    q.clear();
                                } else {
                                    list = new ArrayList<>(remainSize.get());
                                    for (int i = 0, n = remainSize.get(); i < n; i++) {
                                        list.add(q.remove());
                                    }
                                }
                                ret.put(index, list);
                                remainSize.addAndGet(-list.size());
                                queue.threadIndex = threadIndex;
                                if (remainSize.get() <= 0)
                                    throw NopBreakException.INSTANCE;
                            }
                        }
                    });
                } catch (NopBreakException e) { // NOPMD - break
                }

                if (!ret.isEmpty()) {
                    count -= batchSize - remainSize.get();
                    semaphore.release(batchSize - remainSize.get());

                    if (LOG.isTraceEnabled())
                        LOG.trace("fetch-queue:count={},semaphore={},threadIndex={},queue={},ret={}", count,
                                semaphore.availablePermits(), threadIndex, info(), allCount(ret));
                    return ret;
                }

                // 没有获取到数据。如果已经结束则直接返回
                if (finished)
                    return null;

                // 等待addBatch加入数据
                if (LOG.isDebugEnabled())
                    LOG.debug("wait-queue:count={},semaphore={},threadIndex={},queue={}", count,
                            semaphore.availablePermits(), threadIndex, info());
                try {
                    notEmpty.await();
                } catch (InterruptedException e) {

                }
            } finally {
                lock.unlock();
            }
        } while (true);
    }

    int allCount(MapOfInt<List<T>> map) {
        MutableInt ret = new MutableInt();
        map.forEachEntry((q, i) -> {
            ret.addAndGet(q.size());
        });
        return ret.get();
    }

    String info() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(partitions.size()).append(']');
        partitions.forEachEntry((queue, index) -> {
            sb.append(index + ":" + queue.threadIndex + "=>" + queue.queue.size());
            sb.append(',');
        });
        return sb.toString();
    }

    public void finish() {
        lock.lock();
        try {
            finished = true;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void completeBatch(MapOfInt<List<T>> batch, int threadIndex) {
        lock.lock();
        try {
            batch.forEachEntry((list, index) -> {
                PartitionQueue<T> queue = partitions.get(index);
                if (queue != null) {
                    // 如果由当前线程负责处理，且已处理完毕，则删除队列，减少内存消耗
                    if (queue.threadIndex == threadIndex) {
                        if (queue.queue.isEmpty()) {
                            partitions.remove(index);
                        }
                        queue.threadIndex = -1;
                    }
                }
            });
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void addBatch(List<T> data) {
        if (data.isEmpty())
            return;

        MapOfInt<List<T>> map = new IntHashMap<>();
        for (T record : data) {
            int index = partitionFn.apply(record);
            map.computeIfAbsent(index, k -> new ArrayList<>()).add(record);
        }

        lock.lock();
        try {
            map.forEachEntry((list, index) -> {
                PartitionQueue<T> queue = partitions.computeIfAbsent(index, k -> new PartitionQueue<>());
                queue.queue.addAll(list);
            });
            count += data.size();
            if (LOG.isTraceEnabled())
                LOG.trace("add-batch:count={},queue={}", count, info());
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }

        try {
            semaphore.acquire(data.size());
        } catch (InterruptedException e) {

        }
    }
}