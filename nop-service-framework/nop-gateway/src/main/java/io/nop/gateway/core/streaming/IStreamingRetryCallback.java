/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.streaming;

import io.nop.api.core.beans.ApiRequest;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.http.api.client.HttpRequest;

/**
 * 流式重执行回调（W7 机制 A 落地，plan 2026-08-15-1116-3 Phase 1 GW-A7/M-3）。
 *
 * <p>由 {@link StreamingProcessor} 缓冲层在<b>窗口内</b>（尚未向订阅者转发任何数据）上游失败时
 * 调用：回调决定是否重试（返回新 {@link HttpRequest} = 重试；null = 不重试），fetch + 元素映射链
 * 的重跑留在 nop-gateway 缓冲层内部——回调<b>禁止</b>实现第二条映射链或反向调用 nop-gateway 内部。
 *
 * <p>回调实现方（如 nop-ai-gateway 的 failover 拦截器）负责按新候选重建请求（新账号/新 model/
 * 新 body/新 base），并同步更新 per-attempt 状态（经 {@link ApiRequest#setProperty} 通道，
 * 供反向转换读取当前 attempt 的 dialect）。
 */
public interface IStreamingRetryCallback {

    /**
     * @param error   窗口内上游失败异常（非 null）
     * @param route   当前流式路由
     * @param request 当前 API 请求（同一实例贯穿全部 attempt，见 Phase 1 F1 契约）
     * @param context 网关上下文
     * @return 新 {@link HttpRequest}（发起重订阅）；null = 不重试（原样转发错误断流）
     */
    HttpRequest retry(Throwable error, GatewayRouteModel route, ApiRequest<?> request, IGatewayContext context);
}
