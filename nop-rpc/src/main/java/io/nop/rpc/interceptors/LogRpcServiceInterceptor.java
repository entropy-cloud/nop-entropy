/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.interceptors;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.json.JSON;
import io.nop.api.core.rpc.IRpcServiceInterceptor;
import io.nop.api.core.rpc.IRpcServiceInvocation;
import io.nop.api.core.util.ApiHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

public class LogRpcServiceInterceptor implements IRpcServiceInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(LogRpcServiceInterceptor.class);

    public static final LogRpcServiceInterceptor INSTANCE = new LogRpcServiceInterceptor();

    @Override
    public CompletionStage<ApiResponse<?>> interceptAsync(IRpcServiceInvocation inv) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("nop.rpc.invoke:request={}", JSON.serialize(inv.getRequest(), true));
        }
        return inv.proceedAsync().whenComplete((res, err) -> {
            if (err != null) {
                String reqId = ApiHeaders.getId(inv.getRequest());
                LOG.error("nop.err.rpc.invoke-error:serviceName={},serviceMethod={},reqId={}", inv.getServiceName(),
                        inv.getServiceMethod(), reqId, err);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("nop.rpc.return-result:response={}", JSON.serialize(res, true));
            }
        });
    }
}
