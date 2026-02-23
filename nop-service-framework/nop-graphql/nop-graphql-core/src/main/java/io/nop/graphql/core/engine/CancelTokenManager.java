/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class CancelTokenManager implements ICancelTokenManger {
    static final Logger LOG = LoggerFactory.getLogger(CancelTokenManager.class);
    private final Map<String, ICancellable> cancelTokens = new ConcurrentHashMap<>();

    @Override
    public void register(String reqId, ICancellable cancelToken) {
        ICancellable oldToken = cancelTokens.put(reqId, cancelToken);
        if (oldToken != null && oldToken != cancelToken)
            oldToken.cancel("replace");
    }

    @Override
    public boolean cancel(String reqId) {
        ICancellable cancelToken = cancelTokens.remove(reqId);
        if (cancelToken == null)
            return false;
        cancelToken.cancel();
        return true;
    }

    @Override
    public IAsyncFunctionInvoker wrap(IAsyncFunctionInvoker invoker, IServiceContext ctx) {
        String reqId = ApiHeaders.getIdFromHeaders(ctx.getRequestHeaders());
        if (StringHelper.isEmpty(reqId))
            return invoker;

        //String idempotent = ApiHeaders.getIdempotentFromHeaders(ctx.getRequestHeaders());
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