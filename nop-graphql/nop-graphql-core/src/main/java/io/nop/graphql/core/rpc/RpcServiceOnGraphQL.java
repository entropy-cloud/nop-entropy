/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.rpc;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.rpc.AopRpcServiceInvocation;
import io.nop.api.core.rpc.DefaultRpcServiceInvocation;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.rpc.IRpcServiceInterceptor;
import io.nop.api.core.rpc.IRpcServiceInvocation;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.aop.AopProxyHelper;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.graphql.core.utils.GraphQLNameHelper;
import io.nop.rpc.reflect.HttpRpcMessageTransformer;
import io.nop.rpc.reflect.RpcInvocationHandler;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class RpcServiceOnGraphQL implements IRpcService {
    private final String serviceName;
    private final IGraphQLEngine engine;
    private final List<IRpcServiceInterceptor> interceptors;

    public RpcServiceOnGraphQL(IGraphQLEngine engine, String serviceName, List<IRpcServiceInterceptor> interceptors) {
        this.engine = engine;
        this.serviceName = serviceName;
        this.interceptors = interceptors;
    }

    public <T> T asProxy(Class<T> clazz) {
        RpcInvocationHandler handler = new RpcInvocationHandler(serviceName, this, Collections.emptyList(),
                HttpRpcMessageTransformer.INSTANCE);
        Class[] inf = new Class[]{clazz};
        return (T) ReflectionManager.instance().newProxyInstance(inf, handler);
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                     ICancelToken cancelToken) {
        String operationName = GraphQLNameHelper.getOperationName(serviceName, serviceMethod);
        IGraphQLExecutionContext ctx = engine.newRpcContext(null, operationName, request);
        if (cancelToken != null) {
            cancelToken.appendOnCancel(ctx::cancel);
        }

        IContext context = ContextProvider.getOrCreateContext();
        context.setAttribute(ApiConstants.ATTR_SERVICE_CONTEXT, ctx);

        Object oldCtx = context.getAttribute(ApiConstants.ATTR_SERVICE_CONTEXT);

        return invokeAsync(ctx, serviceMethod, request, cancelToken).whenComplete((ret, e) -> {
            context.setAttribute(ApiConstants.ATTR_SERVICE_CONTEXT, oldCtx);
        });
    }

    CompletionStage<ApiResponse<?>> invokeAsync(IGraphQLExecutionContext ctx, String serviceMethod,
                                                ApiRequest<?> request, ICancelToken cancelToken) {
        if (interceptors == null || interceptors.isEmpty())
            return invokeAsync0(ctx);
        IRpcServiceInvocation inv = new DefaultRpcServiceInvocation(serviceName, serviceMethod, request, cancelToken,
                true, (serviceMethod1, request1, cancelToken1) -> invokeAsync0(ctx));
        return new AopRpcServiceInvocation(inv, interceptors).proceedAsync();
    }

    CompletionStage<ApiResponse<?>> invokeAsync0(IGraphQLExecutionContext ctx) {
        return FutureHelper.thenCompleteAsync(engine.executeRpcAsync(ctx),
                (ret, e) -> AopProxyHelper.buildResponse(ret, e, ctx));
    }
}