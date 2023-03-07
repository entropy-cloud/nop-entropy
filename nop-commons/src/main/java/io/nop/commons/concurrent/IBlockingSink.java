/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public interface IBlockingSink<T> {

    /**
     * 阻塞发送
     *
     * @param item
     */
    void send(T item) throws InterruptedException;

    default void sendMulti(Collection<? extends T> items) throws InterruptedException {
        if (items != null) {
            for (T item : items) {
                send(item);
            }
        }
    }

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
}
