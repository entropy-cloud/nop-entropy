/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.core.composite;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.CancelRequestBean;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.StringHelper;
import io.nop.api.core.rpc.IRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 发出请求后允许通过cancelMethod来取消执行
 */
public class CancellableRpcClient implements IRpcService {
    static final Logger LOG = LoggerFactory.getLogger(CancellableRpcClient.class);
    private final IRpcService rpcService;
    private final String cancelMethod;

    public CancellableRpcClient(IRpcService rpcService, String cancelMethod) {
        this.rpcService = rpcService;
        this.cancelMethod = Guard.notEmpty(cancelMethod, "cancelMethod");
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                     ICancelToken cancelToken) {
        // 为了便于取消，需要为消息定义一个唯一id
        String reqId = makeId(request);

        CompletionStage<ApiResponse<?>> future;
        try {
            future = rpcService.callAsync(serviceMethod, request, cancelToken);
        } catch (Throwable e) {
            // 4.3-MA-15（plan 2560 裁定 H·调用方防御面）：底层服务同步抛（如 failed-future
            // 包络未覆盖的残留站点）不再直接穿透到本 CompletionStage 方法，防御性收敛为
            // failed future 交付
            return FutureHelper.reject(e);
        }
        if (cancelToken == null)
            return future;

        AtomicBoolean done = new AtomicBoolean();
        future.whenComplete((r, e) -> done.set(true));

        if (done.get())
            return future;

        // 4.3-C3-012：取消监听器经平台单点 FutureHelper.bindCancelToken 收敛——注册与 remove
        // 按 future 终态配对，长生命周期 cancelToken 的监听器不再随请求次数无界累积
        FutureHelper.bindCancelToken(cancelToken, reason -> {
            // 只有当任务尚未结束，才会执行cancelMethod去尝试主动取消
            if (!done.get()) {
                ApiRequest<Object> copy = new ApiRequest<>();
                copy.setHeaders(request.copyHeaders());
                ApiHeaders.setId(copy, null);
                CancelRequestBean bean = new CancelRequestBean();
                bean.setReqId(reqId);
                bean.setData(request.getData());
                copy.setData(bean);
                rpcService.callAsync(cancelMethod, copy, null).whenComplete((ret, err) -> {
                    if (err != null) {
                        LOG.error("nop.rpc.cancel-request-fail:cancelMethod={},reqId={}", cancelMethod, reqId, err);
                    } else {
                        LOG.info("nop.rpc.cancel-request-ok:cancelMethod={},reqId={},cancelResult={}", cancelMethod, reqId, ret);
                    }
                });
            }
        }, future);
        return future;
    }

    private String makeId(ApiRequest<?> request) {
        String reqId = ApiHeaders.getId(request);
        if (StringHelper.isEmpty(reqId)) {
            reqId = StringHelper.generateUUID();
            ApiHeaders.setId(request, reqId);
        }
        return reqId;
    }
}