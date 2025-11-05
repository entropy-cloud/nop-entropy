/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.core.reflect;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.rpc.RpcMethod;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.rpc.core.utils.RpcHelper;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

import java.util.LinkedHashMap;
import java.util.Map;

public class HttpRpcMessageTransformer extends DefaultRpcMessageTransformer {
    public static HttpRpcMessageTransformer INSTANCE = new HttpRpcMessageTransformer();

    @Override
    public ApiRequest<Object> toRequest(String serviceName, IFunctionModel method, Object[] args) {
        ApiRequest<Object> req;
        String path = getPath(method);
        if (path != null) {
            req = buildRestRequest(method, path, args);
        } else {
            String bizObjName = getBizObjName(method);

            req = super.toRequest(serviceName, method, args);
            RpcHelper.setHttpUrl(req, "/r/" + bizObjName + "__" + method.getName());
            initRpcMethod(req, method, bizObjName);
        }

        String httpMethod = getHttpMethod(method);
        if (httpMethod != null) {
            RpcHelper.setHttpMethod(req, httpMethod);
        }

        RpcHelper.setResponseNormalizer(req, res -> buildResponse(method, res));
        return req;
    }

    private void initRpcMethod(ApiRequest<?> req, IFunctionModel method, String bizObjName) {
        RpcMethod rpcMethod = method.getAnnotation(RpcMethod.class);
        if (rpcMethod != null) {
            String cancelMethod = rpcMethod.cancelMethod();
            if (!cancelMethod.isEmpty()) {
                if (cancelMethod.indexOf("__") < 0)
                    cancelMethod = bizObjName + "__" + cancelMethod;
                RpcHelper.setCancelMethod(req, cancelMethod);
            }

            String pollMethod = rpcMethod.pollingMethod();
            if (!pollMethod.isEmpty()) {
                if (pollMethod.indexOf("__") < 0) {
                    pollMethod = bizObjName + "__" + pollMethod;
                }
                RpcHelper.setPollingMethod(req, pollMethod);
            }

            if (rpcMethod.pollInterval() > 0) {
                RpcHelper.setPollInterval(req, rpcMethod.pollInterval());
            }
        }
    }

    ApiRequest<Object> buildRestRequest(IFunctionModel method, String path, Object[] args) {
        if (args.length == 0) {
            return new ApiRequest<>();
        }

        ApiRequest<Object> req;
        int start = 0;
        if (args[0] instanceof ApiRequest) {
            req = (ApiRequest<Object>) args[0];
            start = 1;
        } else {
            req = new ApiRequest<>();
        }

        String url = path;

        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = start, n = method.getArgCount(); i < n; i++) {
            IFunctionArgument argModel = method.getArgs().get(i);
            QueryParam queryParam = argModel.getAnnotation(QueryParam.class);
            if (queryParam != null) {
                params.put(queryParam.value(), args[i]);
                continue;
            }
            PathParam pathParam = argModel.getAnnotation(PathParam.class);
            if (pathParam != null) {
                String value = ConvertHelper.toString(args[i]);
                url = StringHelper.replace(url, "{" + pathParam.value() + "}", StringHelper.encodeURL(value));
                continue;
            }

            if (argModel.getRawClass() == ICancelToken.class) {
                continue;
            }

            if (req.getData() != null)
                throw new IllegalArgumentException("invalid request arg:" + argModel.getName());
            req.setData(args[i]);
        }

        if (!params.isEmpty()) {
            url = StringHelper.appendQuery(url, StringHelper.encodeQuery(params));
        }

        RpcHelper.setHttpUrl(req, url);
        return req;
    }

    String getBizObjName(IFunctionModel method) {
        BizModel bizModel = method.getDeclaringClass().getAnnotation(BizModel.class);
        if (bizModel == null)
            return method.getDeclaringClass().getSimpleName();
        return bizModel.value();
    }

    String getPath(IFunctionModel method) {
        Path path = method.getAnnotation(Path.class);
        if (path == null)
            return null;

        Class<?> clazz = method.getDeclaringClass();
        Path rootPath = clazz.getAnnotation(Path.class);
        if (rootPath == null) {
            return path.value();
        } else {
            return StringHelper.appendPath(rootPath.value(), path.value());
        }
    }

    String getHttpMethod(IFunctionModel method) {
        if (method.isAnnotationPresent(POST.class))
            return "POST";
        if (method.isAnnotationPresent(GET.class))
            return "GET";
        if (method.isAnnotationPresent(DELETE.class))
            return "DELETE";
        if (method.isAnnotationPresent(PUT.class))
            return "PUT";
        return null;
    }

    ApiResponse<?> buildResponse(IFunctionModel method, Object res) {
        if (res == null)
            return ApiResponse.buildSuccess(null);

        IGenericType type = method.getAsyncReturnType();
        if (res instanceof String) {
            if (type != PredefinedGenericTypes.STRING_TYPE) {
                res = JsonTool.parseBeanFromText(res.toString(), type);
            }
        } else {
            res = BeanTool.castBeanToType(res, type);
        }
        if (res instanceof ApiResponse)
            return (ApiResponse<?>) res;
        return ApiResponse.buildSuccess(res);
    }
}
