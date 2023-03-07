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
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ApiInvokeHelper;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;

import java.util.List;
import java.util.Map;

import static io.nop.rpc.RpcErrors.ARG_COUNT;
import static io.nop.rpc.RpcErrors.ARG_EXPECTED_COUNT;
import static io.nop.rpc.RpcErrors.ARG_SERVICE_METHOD;
import static io.nop.rpc.RpcErrors.ERR_RPC_REQUEST_ARGS_COUNT_MISMATCH;
import static io.nop.rpc.RpcErrors.ERR_RPC_REQUEST_BODY_NOT_LIST;

public class DefaultRpcMessageTransformer implements IRpcMessageTransformer {
    public static final DefaultRpcMessageTransformer INSTANCE = new DefaultRpcMessageTransformer();

    @Override
    public ApiRequest<Object> toRequest(String serviceName, String methodName, Object[] args) {
        if (args != null) {
            // 只有一个参数，且参数类型为ApiRequest时，直接传递request
            if (args.length == 1 && args[0] instanceof ApiRequest) {
                ApiRequest<Object> req = (ApiRequest<Object>) args[0];
                ApiHeaders.setSvcName(req, serviceName);
                ApiHeaders.setSvcAction(req, methodName);
                return req;
            }
        }

        ApiRequest<Object> req = new ApiRequest<>();
        ApiHeaders.setSvcName(req, serviceName);
        ApiHeaders.setSvcAction(req, methodName);
        req.setData(args);
        return req;
    }

    @Override
    public Object fromResponse(String serviceName, String methodName, IGenericType returnType, ApiResponse<?> res) {
        ApiResponse<Object> response = (ApiResponse<Object>) res;

        if (ApiResponse.class == returnType.getRawClass()) {
            IGenericType bodyType = returnType.getTypeParameters().get(0);
            return normalizeResponse(bodyType, response);
        }

        return normalizeType(returnType, ApiInvokeHelper.getResponseData(response));
    }

    ApiResponse<Object> normalizeResponse(IGenericType bodyType, ApiResponse<Object> res) {
        res.setData(normalizeType(bodyType, res.getData()));
        return res;
    }

    Object normalizeType(IGenericType bodyType, Object data) {
        if (data != null) {
            return BeanTool.castBeanToType(data, bodyType);
        }
        return data;
    }

    @Override
    public Object[] fromRequest(String serviceName, String methodName, List<? extends IFunctionArgument> argModels,
                                ApiRequest<?> request) {
        if (argModels.size() == 1) {
            IGenericType type = argModels.get(0).getType();
            if (type.getRawClass() == ApiRequest.class) {
                IGenericType bodyType = type.getTypeParameters().get(0);
                ((ApiRequest<Object>) request).setData(normalizeType(bodyType, request.getData()));
                return new Object[]{request};
            }
        }

        Object data = request.getData();
        if (data == null) {
            return new Object[argModels.size()];
        }

        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            Object[] ret = new Object[argModels.size()];
            for (int i = 0, n = argModels.size(); i < n; i++) {
                IFunctionArgument argModel = argModels.get(i);
                ret[i] = normalizeType(argModel.getType(), map.get(argModel.getName()));
            }
            return ret;
        }

        if (!(data instanceof List))
            throw new NopException(ERR_RPC_REQUEST_BODY_NOT_LIST);

        List<?> list = (List<?>) data;
        if (list.size() != argModels.size())
            throw new NopException(ERR_RPC_REQUEST_ARGS_COUNT_MISMATCH).param(ARG_SERVICE_METHOD, methodName)
                    .param(ARG_COUNT, list.size()).param(ARG_EXPECTED_COUNT, argModels.size());

        Object[] ret = new Object[list.size()];
        for (int i = 0, n = list.size(); i < n; i++) {
            ret[i] = normalizeType(argModels.get(i).getType(), list.get(i));
        }
        return ret;
    }

    @Override
    public ApiResponse<?> toResponse(String serviceName, String methodName, Object result) {
        if (result instanceof ApiResponse)
            return (ApiResponse<?>) result;
        return ApiResponse.buildSuccess(result);
    }

    @Override
    public void enrichResponse(ApiRequest<?> request, ApiResponse<?> response) {
        if (request != null) {
            String id = ApiHeaders.getId(request);
            if (id != null) {
                ApiHeaders.setRelId(response, id);
            }
        }
    }
}