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
import io.nop.api.core.util.ICancelToken;
import io.nop.core.reflect.IFunctionModel;

public interface IRpcMessageTransformer {
    String getMethodName(IFunctionModel method);

    ApiRequest<?> toRequest(String serviceName, IFunctionModel method, Object[] args);

    Object fromResponse(String serviceName, IFunctionModel method, ApiResponse<?> res);

    Object[] fromRequest(String serviceName, IFunctionModel method, ApiRequest<?> request, ICancelToken cancelToken);

    ApiResponse<?> toResponse(String serviceName, IFunctionModel method, Object result);

    void enrichResponse(ApiRequest<?> request, ApiResponse<?> response);
}