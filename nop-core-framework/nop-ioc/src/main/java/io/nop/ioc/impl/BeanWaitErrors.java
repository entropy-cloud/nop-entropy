/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

import static io.nop.ioc.IocErrors.ARG_WAIT_CHAIN;

/**
 * bean 等待域（BD 锁获取 / PBI 初始化等待）环/超时类型化错误构造守卫。
 *
 * <p>沿 {@code ResourceCacheEntry.ringError}（plan 2504 LOCK-001）先例同构：NopException 构造期会
 * 解析本地化描述，可能重入资源装载与等待图登记。构造期间置位
 * {@link #IN_RING_ERROR_CONSTRUCTION}，使 BD/PBI 等待入口在守卫置位时快速失败而非再次进入等待，
 * 阻断错误构造引发的递归等待。</p>
 */
final class BeanWaitErrors {
    /**
     * 环/等待超时错误构造期间置位。等待入口检测到置位即快速失败，不再登记等待边。
     */
    static final ThreadLocal<Boolean> IN_RING_ERROR_CONSTRUCTION = new ThreadLocal<>();

    private BeanWaitErrors() {
    }

    /**
     * 环/等待类型化错误构造（携带 describeChain 等待链快照）。
     */
    static NopException ringError(ErrorCode code, String waitChain) {
        IN_RING_ERROR_CONSTRUCTION.set(Boolean.TRUE);
        try {
            NopException e = new NopException(code);
            if (waitChain != null)
                e.param(ARG_WAIT_CHAIN, waitChain);
            return e;
        } finally {
            IN_RING_ERROR_CONSTRUCTION.set(Boolean.FALSE);
        }
    }

    /**
     * 错误构造守卫置位时等待入口快速失败（不登记等待边、不进入等待）。
     */
    static boolean isConstructingRingError() {
        return Boolean.TRUE.equals(IN_RING_ERROR_CONSTRUCTION.get());
    }
}
