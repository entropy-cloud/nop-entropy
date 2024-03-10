/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.core.reflect;

import io.nop.api.core.annotations.biz.BizSelection;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ApiInvokeHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.type.IGenericType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.rpc.core.RpcErrors.ARG_COUNT;
import static io.nop.rpc.core.RpcErrors.ARG_EXPECTED_COUNT;
import static io.nop.rpc.core.RpcErrors.ARG_SERVICE_METHOD;
import static io.nop.rpc.core.RpcErrors.ERR_RPC_REQUEST_ARGS_COUNT_MISMATCH;
import static io.nop.rpc.core.RpcErrors.ERR_RPC_REQUEST_BODY_NOT_LIST;

public class DefaultRpcMessageTransformer implements IRpcMessageTransformer {
    public static final DefaultRpcMessageTransformer INSTANCE = new DefaultRpcMessageTransformer();

    @Override
    public String getMethodName(IFunctionModel method) {
        String bizActionName = method.getBizActionName();
        if (bizActionName != null)
            return bizActionName;

        String methodName = method.getName();
        if (methodName.endsWith("Async") && method.isAsync())
            return methodName.substring(0, methodName.length() - "Async".length());
        return methodName;
    }

    @Override
    public ApiRequest<Object> toRequest(String serviceName, IFunctionModel method, Object[] args) {
        String methodName = getMethodName(method);


        ApiRequest<Object> req = null;
        Map<String, Object> params = null;

        for (int i = 0, n = method.getArgCount(); i < n; i++) {
            IFunctionArgument argModel = method.getArgs().get(i);
            if (argModel.getRawClass() == ApiRequest.class) {
                req = (ApiRequest<Object>) args[0];
                ApiHeaders.setSvcName(req, serviceName);
                ApiHeaders.setSvcAction(req, methodName);
            } else if (argModel.isAnnotationPresent(RequestBean.class)) {
                req = ApiRequest.build(args[0]);
                ApiHeaders.setSvcName(req, serviceName);
                ApiHeaders.setSvcAction(req, methodName);
            } else if (argModel.getType().isAssignableTo(ICancelToken.class)) {
                // 忽略当前参数，继续执行
            } else {
                String name = argModel.getName();
                if (params == null)
                    params = new HashMap<>();
                params.put(name, args[i]);
            }
        }

        if (req == null)
            req = new ApiRequest<>();

        ApiHeaders.setSvcName(req, serviceName);
        ApiHeaders.setSvcAction(req, methodName);
        initSelection(req, method);

        if (params != null && req.getData() == null) {
            req.setData(params);
        }
        return req;
    }

    void initSelection(ApiRequest<?> req, IFunctionModel method) {
        if (req.getSelection() == null) {
            BizSelection selection = method.getAnnotation(BizSelection.class);
            if (selection != null) {
                if (selection.value().length > 0) {
                    req.setSelection(FieldSelectionBean.fromProp(selection.value()));
                } else {
                    IBeanModel responseBeanModel = getResponseBeanModel(method);
                    FieldSelectionBean selectionBean = FieldSelectionBean.fromProps(responseBeanModel.getDefaultSelectionPropNames());
                    req.setSelection(selectionBean);
                }
            }
        }
    }

    @Override
    public Object fromResponse(String serviceName, IFunctionModel method, ApiResponse<?> res) {
        ApiResponse<Object> response = (ApiResponse<Object>) res;

        IGenericType returnType = method.getAsyncReturnType();

        if (ApiResponse.class == returnType.getRawClass()) {
            IGenericType bodyType = returnType.getTypeParameters().get(0);
            return normalizeResponse(bodyType, response);
        }

        return normalizeType(returnType, ApiInvokeHelper.getResponseData(response));
    }

    IBeanModel getResponseBeanModel(IFunctionModel method) {
        IGenericType returnType = method.getReturnType();
        if (ApiResponse.class == returnType.getRawClass())
            returnType = returnType.getTypeParameters().get(0);
        return ReflectionManager.instance().getBeanModelForType(returnType);
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
    public Object[] fromRequest(String serviceName, IFunctionModel method,
                                ApiRequest<?> request, ICancelToken cancelToken) {
        List<? extends IFunctionArgument> argModels = method.getArgs();
        if (argModels.size() == 1) {
            IFunctionArgument argModel = argModels.get(0);
            IGenericType type = argModel.getType();
            if (type.getRawClass() == ApiRequest.class) {
                IGenericType bodyType = type.getTypeParameters().get(0);
                ((ApiRequest<Object>) request).setData(normalizeType(bodyType, request.getData()));
                return new Object[]{request};
            } else if (argModel.isAnnotationPresent(RequestBean.class)) {
                ((ApiRequest) request).setData(normalizeType(type, request.getData()));
                return new Object[]{request.getData()};
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
            throw new NopException(ERR_RPC_REQUEST_ARGS_COUNT_MISMATCH).param(ARG_SERVICE_METHOD, method.getName())
                    .param(ARG_COUNT, list.size()).param(ARG_EXPECTED_COUNT, argModels.size());

        Object[] ret = new Object[list.size()];
        for (int i = 0, n = list.size(); i < n; i++) {
            ret[i] = normalizeType(argModels.get(i).getType(), list.get(i));
        }
        return ret;
    }

    @Override
    public ApiResponse<?> toResponse(String serviceName, IFunctionModel method, Object result) {
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