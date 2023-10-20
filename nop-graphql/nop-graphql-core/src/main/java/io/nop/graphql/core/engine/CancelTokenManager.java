/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class CancelTokenManager {
    private final Map<String, ICancellable> cancelTokens = new ConcurrentHashMap<>();

    public void register(String reqId, ICancellable cancelToken) {
        ICancellable oldToken = cancelTokens.put(reqId, cancelToken);
        if (oldToken != null && oldToken != cancelToken)
            oldToken.cancel("replace");
    }

    public boolean cancel(String reqId) {
        ICancellable cancelToken = cancelTokens.remove(reqId);
        if (cancelToken == null)
            return false;
        cancelToken.cancel();
        return true;
    }

    public IAsyncFunctionInvoker wrap(IAsyncFunctionInvoker invoker, IServiceContext ctx) {
        String reqId = ApiHeaders.getIdFromHeaders(ctx.getRequestHeaders());
        if (StringHelper.isEmpty(reqId))
            return invoker;
        register(reqId, ctx);

        IAsyncFunctionInvoker wrapped = new IAsyncFunctionInvoker() {
            @Override
            public <R, T> CompletionStage<T> invokeAsync(Function<R, CompletionStage<T>> task, R request) {
                try {
                    CompletionStage<T> future = invoker == null ? task.apply(request) : invoker.invokeAsync(task, request);
                    return future.whenComplete((ret, err) -> cancelTokens.remove(reqId, ctx));
                } catch (Exception e) {
                    cancelTokens.remove(reqId, ctx);
                    throw NopException.adapt(e);
                }
            }
        };
        return wrapped;
    }
}