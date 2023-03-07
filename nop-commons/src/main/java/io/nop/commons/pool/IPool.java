/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.pool;

/**
 * 缓冲池接口
 */
public interface IPool<O extends IPooledObject> extends AutoCloseable {
    String getId();

    boolean isClosed();

    O acquire(PoolAcquireOptions options);

    /**
     * 释放已经获取的连接。
     *
     * @param object
     * @param shouldDestroy 如果为true, 则不能被缓存复用，必须关闭
     */
    void release(O object, boolean shouldDestroy);

    PoolStats stats();
}