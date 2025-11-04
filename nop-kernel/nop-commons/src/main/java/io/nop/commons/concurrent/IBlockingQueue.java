/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent;

import java.util.concurrent.TimeUnit;

/**
 * @param <T>
 */
public interface IBlockingQueue<T> extends IBlockingEndpoint<T> {

    /**
     * 添加记录。如果返回false, 则表示item没有被加入队列，由外部调用者负责释放item
     *
     * @param o 数据
     * @return 队列已满时返回false
     */
    boolean offer(T o);

    /**
     * 添加记录。如果返回false, 则表示item没有被加入队列，由外部调用者负责释放item
     *
     * @param o
     * @param timeout
     * @param unit
     * @return 队列已满时返回false
     * @throws InterruptedException
     */
    boolean offer(T o, long timeout, TimeUnit unit) throws InterruptedException;

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
     * 清空队列
     */
    void clear();

    /**
     * 如果队列已满，则按照{@link QueueOverflowPolicy}处理
     *
     * @param item
     * @throws InterruptedException
     */
    void send(T item) throws InterruptedException;

    /**
     * 得到队列大小
     *
     * @return
     */
    int size();

    /**
     * 判断队列是否为空
     *
     * @return
     */
    boolean isEmpty();
}