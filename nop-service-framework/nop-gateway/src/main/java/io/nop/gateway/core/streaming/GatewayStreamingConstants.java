/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.streaming;

/**
 * 流式扩展常量（W7 机制 A，plan 2026-08-15-1116-3）。
 *
 * <p>nop-gateway 保持零 nop-ai 依赖——重执行回调与生命周期监听器经
 * {@link io.nop.gateway.core.context.IGatewayContext} attribute 注入（Phase 1 GW-A6/B-12），
 * base-url 覆盖经 {@link io.nop.api.core.beans.ApiRequest} properties 通道（Phase 1 GW-A7/B-14）。
 */
public final class GatewayStreamingConstants {

    /**
     * context attribute 键：重执行回调（{@link IStreamingRetryCallback}）。
     * 拦截器 onRequest 写入，StreamingProcessor 缓冲层读取（缺省 null = 不重订阅，零回归）。
     */
    public static final String ATTR_RETRY_CALLBACK = IStreamingRetryCallback.class.getName();

    /**
     * context attribute 键：流式生命周期监听器（{@link IStreamingLifecycleListener}）。
     * 拦截器 onRequest 写入，StreamingProcessor 缓冲层读取（缺省 null = 不计数，零回归）。
     */
    public static final String ATTR_LIFECYCLE_LISTENER = IStreamingLifecycleListener.class.getName();

    /**
     * ApiRequest property 键：base-url 覆盖（base 替换语义，Phase 1 GW-A7/B-14）。
     * 值 = 目标 base（如 {@code https://gw-b.example.com}，非全 URL）；buildStreamingHttpRequest
     * 读取后替换求值 URL 的 scheme://authority 之后的部分（保留 path+query）；
     * 缺省 = 既有 route URL 表达式求值（零回归）。属性不序列化、客户端不可注入（ApiRequest
     * properties @JsonIgnore 通道）。
     */
    public static final String PROP_BASE_URL = "nop.gateway.streaming.baseUrl";

    private GatewayStreamingConstants() {
    }
}
