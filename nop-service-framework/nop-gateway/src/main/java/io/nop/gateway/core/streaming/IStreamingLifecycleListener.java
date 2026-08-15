/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.streaming;

/**
 * 流式生命周期回调（W7 机制 A 落地，plan 2026-08-15-1116-3 Phase 1 GW-A6/B-2）。
 *
 * <p>由 {@link StreamingProcessor} 缓冲层在 fetch 发起与流终止时调用，实现方（如 nop-ai-gateway
 * 的并发计数）经 {@link IGatewayContext} attribute 注入。语义契约（spike §6.4 + Phase 1 裁定）：
 * <ul>
 *   <li>{@link #onFetchStarted()}：每次 attempt 的 fetch 发起时调用（重订阅 = 新 attempt，再次调用）；
 *       挂钩点 = 缓冲层发起 attempt（fetch 惰性发起于映射链订阅期首次 request，同步在订阅调用栈内完成）。</li>
 *   <li>{@link #onStreamTerminated(Throwable)}：每次 attempt 终止时调用，exactly-once 去重——
 *       onComplete/onError/静默取消（经 wrapper cancel）任一触发后不再重复；{@code cause} null =
 *       正常完成，非 null = 失败或取消原因。实现方须保证每次 onFetchStarted 恰配一次
 *       onStreamTerminated（重订阅 = -1 旧 attempt +1 新 attempt）。</li>
 * </ul>
 *
 * <p><b>与缓冲开关解耦</b>（Phase 1 Minor 修复）：监听器独立于缓冲开关——缓冲关闭时生命周期
 * 回调仍须触发（"缓冲关闭 ⇒ 计数静默消失"被禁止）。
 */
public interface IStreamingLifecycleListener {

    void onFetchStarted();

    void onStreamTerminated(Throwable cause);
}
