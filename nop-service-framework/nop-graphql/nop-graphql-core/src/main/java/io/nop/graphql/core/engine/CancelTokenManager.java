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
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static io.nop.graphql.core.GraphQLErrors.ARG_REQ_ID;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_CANCEL_NOT_LOGIN;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_CANCEL_NOT_OWNER;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_CANCEL_REQ_ID_CONFLICT;

/**
 * 在途请求的取消令牌登记表。[G5-13-02]归属语义：
 * <ul>
 *   <li>注册时绑定发起请求的用户（{@link IServiceContext#getUserId()}），未登录为null</li>
 *   <li>带用户身份的{@link #cancel(String, String)}仅允许取消本人注册的请求；
 *       未登录注册的请求不开放该公开取消通道</li>
 *   <li>同一用户携同reqId重试视为幂等重绑定：保留先到请求的登记，不取消、不替换；
 *       不同用户复用同一reqId则响亮拒绝（ERR_GRAPHQL_CANCEL_REQ_ID_CONFLICT）</li>
 *   <li>无用户的{@link #cancel(String)}保留为框架内部系统级取消通道（超时/终止等），
 *       不受归属约束</li>
 * </ul>
 */
public class CancelTokenManager implements ICancelTokenManger {
    static final Logger LOG = LoggerFactory.getLogger(CancelTokenManager.class);

    /**
     * reqId的登记记录：token为可取消对象，userId为注册请求的归属用户（未登录为null）。
     */
    record RegisteredRequest(String userId, ICancellable token) {
    }

    private final Map<String, RegisteredRequest> cancelTokens = new ConcurrentHashMap<>();

    @Override
    public void register(String reqId, ICancellable cancelToken) {
        register(reqId, cancelToken, null);
    }

    @Override
    public ICancellable register(String reqId, ICancellable cancelToken, String userId) {
        return registerInternal(reqId, cancelToken, userId).token();
    }

    private RegisteredRequest registerInternal(String reqId, ICancellable cancelToken, String userId) {
        for (; ; ) {
            RegisteredRequest old = cancelTokens.get(reqId);
            if (old == null) {
                RegisteredRequest reg = new RegisteredRequest(userId, cancelToken);
                if (cancelTokens.putIfAbsent(reqId, reg) == null)
                    return reg;
                continue;
            }
            if (old.token() == cancelToken)
                return old;
            if (!Objects.equals(old.userId(), userId)) {
                LOG.warn("nop.graphql.cancel-req-id-conflict:reqId={},registeredUser={},requestUser={}",
                        reqId, old.userId(), userId);
                throw new NopException(ERR_GRAPHQL_CANCEL_REQ_ID_CONFLICT).param(ARG_REQ_ID, reqId);
            }
            // 同一用户携同reqId重试在途请求：幂等重绑定，保留先到请求的token，不取消
            return old;
        }
    }

    @Override
    public boolean cancel(String reqId) {
        RegisteredRequest reg = cancelTokens.remove(reqId);
        if (reg == null)
            return false;
        reg.token().cancel();
        return true;
    }

    @Override
    public boolean cancel(String reqId, String userId) {
        RegisteredRequest reg = cancelTokens.get(reqId);
        if (reg == null)
            return false;
        if (StringHelper.isEmpty(userId))
            throw new NopException(ERR_GRAPHQL_CANCEL_NOT_LOGIN);
        if (reg.userId() == null || !reg.userId().equals(userId)) {
            LOG.warn("nop.graphql.cancel-not-owner:reqId={},registeredUser={},requestUser={}",
                    reqId, reg.userId(), userId);
            throw new NopException(ERR_GRAPHQL_CANCEL_NOT_OWNER).param(ARG_REQ_ID, reqId);
        }
        // compareAndRemove按登记记录比对：并发下请求已完成并被清理时幂等返回false
        if (!cancelTokens.remove(reqId, reg))
            return false;
        reg.token().cancel();
        return true;
    }

    @Override
    public IAsyncFunctionInvoker wrap(IAsyncFunctionInvoker invoker, IServiceContext ctx) {
        String reqId = ApiHeaders.getIdFromHeaders(ctx.getRequestHeaders());
        if (StringHelper.isEmpty(reqId))
            return invoker;

        // G5-13-02: 注册时绑定请求归属用户；同用户重试返回既有token，仅本请求实际持有时才在完成时清理
        RegisteredRequest reg = registerInternal(reqId, ctx, ctx.getUserId());
        final boolean boundSelf = reg.token() == ctx;

        IAsyncFunctionInvoker wrapped = new IAsyncFunctionInvoker() {
            @Override
            public <R, T> CompletionStage<T> invokeAsync(Function<R, CompletionStage<T>> task, R request) {
                try {
                    CompletionStage<T> future = invoker == null ? task.apply(request) : invoker.invokeAsync(task, request);
                    return future.whenComplete((ret, err) -> {
                        if (boundSelf)
                            cancelTokens.remove(reqId, reg);
                    });
                } catch (Exception e) {
                    if (boundSelf)
                        cancelTokens.remove(reqId, reg);
                    throw NopException.adapt(e);
                }
            }
        };
        return wrapped;
    }
}
