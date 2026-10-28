/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.executor;

import io.nop.commons.concurrent.semaphore.DefaultSemaphore;
import io.nop.commons.concurrent.semaphore.ISemaphore;
import io.nop.gateway.GatewayConfigs;

/**
 * 2.3-C8-002（plan 2508 裁定②）：url 路由腿与 fallback 腿**共享**的上游在途许可池持有者。
 * 主腿由 {@code GatewayHandler} 直构 {@link InvokeProcessor}，fallback 腿由 IoC 装配的
 * {@code AiFailoverGatewayInterceptor} 发起——两条装配路径经本持有者取同一池，使
 * 「主腿（含重试挂起）+ 活跃 fallback 腿 ≤ permits」的在途不变式成立。
 *
 * <p>配置键 {@code nop.gateway.upstream-max-inflight} 为启动期读取；≤0 = 不设上界档（返回 null，
 * 调用方视为无准入）。
 */
public final class UpstreamAdmission {
    private static final class Holder {
        static final ISemaphore SHARED = build();
    }

    private UpstreamAdmission() {
    }

    /** 共享许可池；配置 ≤0 时为 null（不设上界档）。 */
    public static ISemaphore shared() {
        return Holder.SHARED;
    }

    static ISemaphore build() {
        Integer max = GatewayConfigs.CFG_GATEWAY_UPSTREAM_MAX_INFLIGHT.get();
        if (max == null || max <= 0)
            return null;
        return new DefaultSemaphore(max);
    }
}
