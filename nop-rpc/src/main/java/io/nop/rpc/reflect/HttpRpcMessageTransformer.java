package io.nop.rpc.reflect;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ApiHeaders;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;

public class HttpRpcMessageTransformer extends DefaultRpcMessageTransformer {
    public static HttpRpcMessageTransformer INSTANCE = new HttpRpcMessageTransformer();

    @Override
    public ApiRequest<Object> toRequest(String serviceName, IFunctionModel method, Object[] args) {
        ApiRequest<Object> req = super.toRequest(serviceName, method, args);
        String path = getPath(method);
        if (path != null) {
            ApiHeaders.setHttpUrl(req, path);
        }

        String httpMethod = getHttpMethod(method);
        if (httpMethod != null) {
            ApiHeaders.setHttpMethod(req, httpMethod);
        }

        req.setResponseNormalizer(res -> buildResponse(method, res));
        return req;
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
