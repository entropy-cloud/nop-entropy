/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock;

import io.nop.api.core.time.CoreMetrics;

import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;

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

    /**
     * 在持有锁的状态下重复执行指定操作，直到操作成功或获取锁失败
     *
     * <p>本方法执行流程：
     * <ol>
     *   <li>尝试获取锁，最多等待 {@code waitTime} 毫秒，锁租期为 {@code leaseTime} 毫秒</li>
     *   <li>获取成功后执行操作函数，并将锁的到期时间戳作为参数传入</li>
     *   <li>若操作返回 true，则保持锁并返回成功</li>
     *   <li>若操作返回 false，则释放锁并重新尝试整个流程</li>
     * </ol>
     *
     * <p>注意事项：
     * <ul>
     *   <li>操作函数应自行根据锁到期时间处理超时逻辑</li>
     *   <li>操作函数返回 false 时会立即释放锁并重试，适合非原子性要求的可重试操作</li>
     *   <li>无全局超时控制，操作函数应实现自己的超时判断逻辑</li>
     * </ul>
     *
     * @param waitTime  获取锁的最大等待时间(毫秒)
     * @param leaseTime 锁的持有时间(毫秒)
     * @param action    需要执行的操作函数，接收锁到期时间戳参数，
     *                  返回 true 表示操作成功，false 表示需要释放锁并重试
     * @return true 表示操作成功完成且锁仍持有，false 表示获取锁失败
     * @throws NullPointerException 如果操作函数为 null
     */
    default boolean tryLockAndExecute(long waitTime, long leaseTime, Function<Long, Boolean> action) {
        do {
            long expireAt = CoreMetrics.currentTimeMillis() + leaseTime;
            if (!tryLockWithLease(waitTime, leaseTime)) {
                return false;
            }

            try {
                if (action.apply(expireAt)) {
                    return true;
                }
            } finally {
                unlock();
            }
        } while (true);
    }
}