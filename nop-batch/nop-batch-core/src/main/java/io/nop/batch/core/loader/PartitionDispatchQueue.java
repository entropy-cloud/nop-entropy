/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.loader;

import io.nop.api.core.exceptions.NopBreakException;
import io.nop.api.core.exceptions.NopException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 对每条记录计算得到一个partitionIndex，按照partitionIndex将记录拆分到多个队列中，每个队列只有一个线程负责处理。
 * 这个类用于派发任务，partitionIndex相同的任务总是顺序被处理（不一定在同一个线程上）
 */
public class PartitionDispatchQueue<T> {
    static final Logger LOG = LoggerFactory.getLogger(PartitionDispatchQueue.class);

    static class PartitionQueue<T> {
        /**
         * 线程的唯一标识。小于0时表示此线程没有被占据
         */
        long threadId = -1;
        final Queue<T> queue = new ArrayDeque<>();
    }

    private final Semaphore semaphore;

    /**
     * 从partitionIndex映射得到对应的队列，每个队列由一个线程负责处理
     */
    private final IntHashMap<PartitionQueue<T>> partitions = new IntHashMap<>();
    private final Function<T, Integer> partitionFn;

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition notEmpty = lock.newCondition();

    private volatile boolean finished;
    private volatile boolean noMoreData;

    private int maxLockQueueCountPerThread = 100;

    /**
     * 当前尚未被处理的记录数
     */
    private int count;

    private final int capacity;

    private final CountDownLatch fetchThreadCount;

    public PartitionDispatchQueue(int capacity, Function<T, Integer> partitionFn, int fetchThreadCount) {
        this.semaphore = new Semaphore(capacity);
        this.capacity = capacity;
        this.partitionFn = partitionFn;
        this.fetchThreadCount = new CountDownLatch(fetchThreadCount);
    }

    public PartitionDispatchQueue(int capacity, Function<T, Integer> partitionFn) {
        this(capacity, partitionFn, 0);
    }

    public void exitFetchThread() {
        fetchThreadCount.countDown();
        // 所有线程都已经结束
        if (fetchThreadCount.getCount() == 0) {
            noMoreData = true;
            lock.lock();
            try {
                notEmpty.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    public void setMaxLockQueueCountPerThread(int maxLockQueueCountPerThread) {
        this.maxLockQueueCountPerThread = maxLockQueueCountPerThread;
    }

    public int getMaxLockQueueCountPerThread() {
        return maxLockQueueCountPerThread;
    }

    public void markNoMorData() {
        this.noMoreData = true;
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

    public int getCapacity() {
        return capacity;
    }

    public MapOfInt<List<T>> takeBatch(int batchSize, long threadId, Supplier<List<T>> fetcher) {
        Guard.checkArgument(batchSize > 0, "batchSize must be non negative");
        MutableInt remainSize = new MutableInt(batchSize);
        MapOfInt<List<T>> ret = new IntHashMap<>();

        do {
            lock.lock();
            try {
                if (finished)
                    return null;

                try {
                    partitions.randomForEachEntry((queue, index) -> {
                        // threadId如果小于0，则表示此partition没有被某个线程处理
                        if (queue.threadId < 0 || queue.threadId == threadId) {
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
                                List<T> retList = ret.get(index);
                                if (retList != null) {
                                    retList.addAll(list);
                                } else {
                                    ret.put(index, list);
                                }
                                remainSize.addAndGet(-list.size());
                                // 此时有可能已经空了，但是取到的数据还未处理，所以需要标记队列正在被threadId对应的线程处理
                                queue.threadId = threadId;

                                // 占用的队列数也不能太多，否则其他线程也无法处理数据
                                if (remainSize.get() <= 0 || ret.size() >= maxLockQueueCountPerThread)
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
                        LOG.trace("nop.batch.fetch-queue:count={},semaphore={},threadId={},queue={},ret={}", count,
                                semaphore.availablePermits(), threadId, info(), allCount(ret));
                    return ret;
                }

                // 如果所有fetch线程都已经结束，并且当前队列中也没有任何元素
                if (count <= 0 && fetchThreadCount.getCount() == 0 && noMoreData) {
                    if (LOG.isDebugEnabled())
                        LOG.debug("nop.batch.no-more-data:count={},semaphore={},threadId={},queue={}", count,
                                semaphore.availablePermits(), threadId, info());
                    return null;
                }

                if (LOG.isDebugEnabled())
                    LOG.debug("nop.batch.wait-queue:count={},semaphore={},threadId={},queue={}", count,
                            semaphore.availablePermits(), threadId, info());

                if (fetcher == null) {
                    // 如果没有fetcher，则需要等待addBatch加入数据
                    try {
                        notEmpty.await(500, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw NopException.adapt(e);
                    }
                }
            } finally {
                lock.unlock();
            }

            if (fetcher != null) {
                if (!noMoreData) {
                    // fetcher返回空集合后表示所有数据都已经取完
                    List<T> list = fetcher.get();
                    if (!list.isEmpty()) {
                        addBatch(list);
                    } else {
                        noMoreData = true;
                    }
                } else {
                    lock.lock();
                    try {
                        notEmpty.await(500, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw NopException.wrap(e);
                    } finally {
                        lock.unlock();
                    }
                }
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
            sb.append(index + ":" + queue.threadId + "=>" + queue.queue.size());
            sb.append(',');
        });
        return sb.toString();
    }

    public void completeBatch(MapOfInt<List<T>> batch, long threadId) {
        lock.lock();
        try {
            batch.forEachEntry((list, index) -> {
                PartitionQueue<T> queue = partitions.get(index);
                if (queue != null) {
                    // 如果由当前线程负责处理，且已处理完毕，则删除队列，减少内存消耗
                    if (queue.threadId == threadId) {
                        if (queue.queue.isEmpty()) {
                            partitions.remove(index);
                        }
                        queue.threadId = -1;
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

        try {
            semaphore.acquire(data.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        }

        lock.lock();
        try {
            map.forEachEntry((list, index) -> {
                PartitionQueue<T> queue = partitions.computeIfAbsent(index, k -> new PartitionQueue<>());
                queue.queue.addAll(list);
            });
            count += data.size();
            if (LOG.isTraceEnabled())
                LOG.trace("nop.batch.add-to-queue:count={},queue={}", count, info());
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }
}