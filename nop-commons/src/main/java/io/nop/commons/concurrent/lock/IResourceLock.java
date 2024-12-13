/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock;

import java.util.Set;
import java.util.concurrent.locks.Lock;

public interface IResourceLock extends Lock {

    /**
     * 锁对应的资源路径
     *
     * @return 如果是multi lock, 则返回列表，否则，返回单个资源路径
     */
    Set<String> getResourceIds();

    /**
     * 当前锁资源占有者的id
     *
     * @return 资源占有者的id
     */
    String getHolderId();

    boolean isHoldingLock();

    String getLockReason();

    void setLockReason(String lockReason);

    /**
     * 尝试获取锁
     *
     * @param waitTime  如果无法立刻获取锁，允许等待多少时间，单位为毫秒，不能为负数
     * @param leaseTime 如果获取锁，在锁只在租期时间内有效，单位为毫秒。从尝试获取锁时开始计时
     * @return 是否成功获取锁
     */
    boolean tryLockWithLease(long waitTime, long leaseTime);

    /**
     * 延长租期
     *
     * @param leaseTime 从当前开始计算的租期时间，以毫秒为单位
     * @return true表示成功续约，false表示锁已经被其他人抢占
     */
    boolean tryResetLease(long leaseTime);
}