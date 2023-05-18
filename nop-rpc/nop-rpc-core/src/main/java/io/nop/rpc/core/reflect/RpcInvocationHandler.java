/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.core.reflect;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.commons.util.DestroyHelper;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.aop.AopProxyHelper;
import io.nop.core.reflect.impl.MethodModelBuilder;
import io.nop.rpc.api.AopRpcServiceInvocation;
import io.nop.rpc.api.DefaultRpcServiceInvocation;
import io.nop.rpc.api.IRpcProxy;
import io.nop.rpc.api.IRpcService;
import io.nop.rpc.api.IRpcServiceInterceptor;
import io.nop.rpc.api.IRpcServiceInvocation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RpcInvocationHandler implements InvocationHandler {
    // static final Logger LOG = LoggerFactory.getLogger(RpcInvocationHandler.class);

    private final String serviceName;
    private final IRpcService rpcService;
    private final List<IRpcServiceInterceptor> interceptors;
    private final IRpcMessageTransformer transformer;
    private final Map<Method, IFunctionModel> methods = new ConcurrentHashMap<>();


    public RpcInvocationHandler(String serviceName, IRpcService rpcService,
                                List<IRpcServiceInterceptor> interceptors,
                                IRpcMessageTransformer transformer) {
        this.serviceName = serviceName;
        this.rpcService = rpcService;
        this.interceptors = interceptors;
        this.transformer = transformer;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return AopProxyHelper.invokeObjectMethod(this, proxy, method, args);
        }

        if (method.getDeclaringClass() == IRpcProxy.class && method.getName().equals("proxy_destroy")) {
            DestroyHelper.safeDestroy(rpcService);
            return null;
        }

        IFunctionModel methodModel = methods.computeIfAbsent(method, mtd -> MethodModelBuilder.from(mtd.getDeclaringClass(), mtd));

        String methodName = transformer.getMethodName(methodModel);

        ApiRequest<?> req = transformer.toRequest(serviceName, methodModel, args);
        IRpcServiceInvocation inv = new DefaultRpcServiceInvocation(serviceName, methodName, req, null, false,
                rpcService);

        if (interceptors != null && !interceptors.isEmpty()) {
            inv = new AopRpcServiceInvocation(inv, interceptors);
        }

        if (methodModel.isAsync()) {
            // async
            return inv.proceedAsync().thenApply(res -> transformer.fromResponse(serviceName, methodModel, res));
        } else {
            // sync
            ApiResponse<?> result = inv.proceed();
            return transformer.fromResponse(serviceName, methodModel, result);
        }
    }
}
