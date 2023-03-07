/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.executor;

public interface IRateLimitExecutor {
    /**
     * 如果已经开始执行，则在一段时间内忽略后续的调用
     *
     * @param key
     * @param minTimeInterval
     * @param task
     */
    void throttle(Object key, long minTimeInterval, Runnable task);

    /**
     * 延迟一段时间执行，如果在等待期间又有新的事件到达，则扩展延迟时间
     *
     * @param key
     * @param waitTimeInterval
     * @param task
     */
    void debounce(Object key, long waitTimeInterval, Runnable task);

    /**
     * 如果任务尚未执行，则替换待执行的任务
     *
     * @param key
     * @param task
     */
    void replace(Object key, Runnable task);
}