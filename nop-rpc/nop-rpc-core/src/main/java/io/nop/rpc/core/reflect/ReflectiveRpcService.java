/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.core.reflect;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.IMethodModelCollection;
import io.nop.core.reflect.ReflectionManager;
import io.nop.rpc.api.IRpcService;

import java.util.concurrent.CompletionStage;

import static io.nop.rpc.core.RpcErrors.ARG_CLASS_NAME;
import static io.nop.rpc.core.RpcErrors.ARG_SERVICE_METHOD;
import static io.nop.rpc.core.RpcErrors.ARG_SERVICE_NAME;
import static io.nop.rpc.core.RpcErrors.ERR_RPC_NOT_ALLOW_METHOD_OVERLOAD;
import static io.nop.rpc.core.RpcErrors.ERR_RPC_NO_HANDLER;

public class ReflectiveRpcService implements IRpcService {
    private final String serviceName;
    private final Object serviceImpl;
    private final IClassModel classModel;
    private final IRpcMessageTransformer transformer;

    public ReflectiveRpcService(String serviceName, Class<?> ifs, Object serviceImpl,
                                IRpcMessageTransformer transformer) {
        this.serviceName = serviceName;
        this.serviceImpl = serviceImpl;
        this.classModel = ReflectionManager.instance().getClassModel(ifs);
        this.transformer = transformer;
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                     ICancelToken cancelToken) {
        // 优先查找Async方法
        IMethodModelCollection methods = classModel.getMethodsByName(serviceMethod + "Async");
        if (methods == null)
            methods = classModel.getMethodsByName(serviceMethod);

        if (methods == null) {
            throw new NopException(ERR_RPC_NO_HANDLER).param(ARG_SERVICE_NAME, serviceName)
                    .param(ARG_SERVICE_METHOD, serviceMethod).param(ARG_CLASS_NAME, classModel.getClassName());
        }

        IFunctionModel method = methods.getUniqueMethod();
        if (method == null) {
            throw new NopException(ERR_RPC_NOT_ALLOW_METHOD_OVERLOAD).param(ARG_SERVICE_NAME, serviceName)
                    .param(ARG_SERVICE_METHOD, serviceMethod);
        }

        String serviceName = ApiHeaders.getSvcName(request);
        Object[] args = transformer.fromRequest(serviceName, method, request, cancelToken);
        try {
            Object result = method.invoke(serviceImpl, args, DisabledEvalScope.INSTANCE);
            if (result instanceof CompletionStage) {
                return ((CompletionStage<?>) result)
                        .thenApply(v -> transformer.toResponse(serviceName, method, v));
            } else {
                return FutureHelper.success(transformer.toResponse(serviceName, method, result));
            }
        } catch (Exception e) {
            String locale = ApiHeaders.getLocale(request);
            return FutureHelper.success(ErrorMessageManager.instance().buildResponse(locale, e));
        }
    }
}
