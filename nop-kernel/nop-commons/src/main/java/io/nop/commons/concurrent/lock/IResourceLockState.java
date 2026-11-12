/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock;

/**
 * 用户可以锁定一个资源一段时间
 */
public interface IResourceLockState {
    String getResourceId();

    String getLockerId();

    long getLockTime();

    /**
     * 锁到期时间。
     *
     * <p>R4-32（C7-004，plan 2566）契约修订：时间语义由具体 IResourceLockManager 实现定义——
     * 本地实现（{@link io.nop.commons.concurrent.lock.impl.LocalResourceLockManager}）自本批起
     * 使用单调时钟，本方法返回值为<strong>单调相对值（纳秒）</strong>，仅可与同进程内
     * {@code CoreMetrics.nanoTime()} 的返回值比较或相减求剩余租期，不得持久化、不得与墙钟
     * （currentTimeMillis/epoch）比较、不得跨进程传输。其它实现（如 DB 锁实现）可能仍为
     * epoch 毫秒，消费方必须以管理器实现为准，不得假设统一语义。
     *
     * @return 到期时间（语义见上，随实现而定）
     */
    long getExpireTime();

    long getCreateTime();

    String getLockReason();

    long getVersion();
}