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
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.type.IGenericType;

import java.util.List;

public interface IRpcMessageTransformer {
    ApiRequest<?> toRequest(String serviceName, String methodName, Object[] args);

    Object fromResponse(String serviceName, String methodName, IGenericType returnType, ApiResponse<?> res);

    Object[] fromRequest(String serviceName, String methodName, List<? extends IFunctionArgument> argModels,
                         ApiRequest<?> request);

    ApiResponse<?> toResponse(String serviceName, String methodName, Object result);

    void enrichResponse(ApiRequest<?> request, ApiResponse<?> response);
}