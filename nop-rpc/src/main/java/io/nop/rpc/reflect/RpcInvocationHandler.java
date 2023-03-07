/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.reflect;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.AopRpcServiceInvocation;
import io.nop.api.core.rpc.DefaultRpcServiceInvocation;
import io.nop.api.core.rpc.IRpcProxy;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.rpc.IRpcServiceInterceptor;
import io.nop.api.core.rpc.IRpcServiceInvocation;
import io.nop.commons.util.DestroyHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.aop.AopProxyHelper;
import io.nop.core.type.IGenericType;
import io.nop.core.type.utils.JavaGenericTypeHelper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class RpcInvocationHandler implements InvocationHandler {
    // static final Logger LOG = LoggerFactory.getLogger(RpcInvocationHandler.class);

    private final String serviceName;
    private final IRpcService rpcService;
    private final List<IRpcServiceInterceptor> interceptors;
    private final IRpcMessageTransformer transformer;

    public RpcInvocationHandler(String serviceName, IRpcService rpcService, List<IRpcServiceInterceptor> interceptors,
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

        String methodName = AopProxyHelper.getServiceMethod(method);

        ApiRequest<?> req = transformer.toRequest(serviceName, methodName, args);
        IRpcServiceInvocation inv = new DefaultRpcServiceInvocation(serviceName, methodName, req, null, false,
                rpcService);

        if (interceptors != null && !interceptors.isEmpty()) {
            inv = new AopRpcServiceInvocation(inv, interceptors);
        }

        Class<?> resultType = method.getReturnType();
        if (CompletionStage.class.isAssignableFrom(resultType)) {
            // async
            Type paramType = JavaGenericTypeHelper.getSuperParamType(method.getGenericReturnType(), resultType,
                    CompletionStage.class);
            IGenericType resType = ReflectionManager.instance().buildGenericType(paramType);
            return inv.proceedAsync().thenApply(res -> transformer.fromResponse(serviceName, methodName, resType, res));
        } else {
            // sync
            ApiResponse<?> result = inv.proceed();
            IGenericType resType = ReflectionManager.instance().buildGenericType(method.getGenericReturnType());
            return transformer.fromResponse(serviceName, methodName, resType, result);
        }
    }
}
