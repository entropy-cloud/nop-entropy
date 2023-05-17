/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.reflection;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.graphql.core.IDataFetchingEnvironment;

import java.util.function.Function;

public class ArgBuilders {

    public static IServiceActionArgBuilder getActionArgFromRequest(String name, IGenericType type) {
        return (req, selection, ctx) -> {
            Object value = BeanTool.instance().getProperty(req, name, ctx.getEvalScope());
            if (value == null)
                return null;

            if (type == null)
                return value;

            if (value instanceof String) {
                if (type.isCollectionLike() && type.getComponentType() == PredefinedGenericTypes.STRING_TYPE) {
                    if (type.isSetLike())
                        return ConvertHelper.toCsvSet(value);
                    return ConvertHelper.toCsvList(value, NopException::new);
                }
                if (type.isDataBean()) {
                    return JsonTool.parseBeanFromText(value.toString(), type);
                }
            }
            return BeanTool.castBeanToType(value, type);
        };
    }

    public static IServiceActionArgBuilder getActionRequest(IGenericType type) {
        return (req, selection, ctx) -> {
            if (req == null)
                return null;

            if (type == null)
                return req;

            if (req instanceof String && type.isDataBean()) {
                if (type.getRawClass() == ApiRequest.class) {
                    IGenericType bodyType = type.getTypeParameters().get(0);
                    Object data = JsonTool.parseBeanFromText(req.toString(), bodyType);
                    ApiRequest<Object> ret = new ApiRequest<>();
                    ret.setSelection(selection);
                    ret.setHeaders(ctx.getRequestHeaders());
                    ret.setData(data);
                    return ret;
                }
                return JsonTool.parseBeanFromText(req.toString(), type);
            }

            if (type.getRawClass() == ApiRequest.class) {
                IGenericType bodyType = type.getTypeParameters().get(0);
                Object data = BeanTool.castBeanToType(req, bodyType);
                ApiRequest<Object> ret = new ApiRequest<>();
                ret.setSelection(selection);
                ret.setHeaders(ctx.getRequestHeaders());
                ret.setData(data);
                return ret;
            }
            return BeanTool.castBeanToType(req, type);
        };
    }

    public static Function<IDataFetchingEnvironment, Object> getArgsAsRequest(IGenericType requestBodyType) {
        return env -> {
            Object args = env.getArgs();
            Object data = BeanTool.castBeanToType(args, requestBodyType);
            ApiRequest<Object> req = new ApiRequest<>();
            req.setSelection(env.getSelectionBean());
            req.setHeaders(env.getExecutionContext().getResponseHeaders());
            req.setData(data);
            return req;
        };
    }

    public static Function<IDataFetchingEnvironment, Object> getEnv() {
        return env -> env;
    }

    public static Function<IDataFetchingEnvironment, Object> getArg(String argName, IGenericType argType) {
        boolean dataBean = argType.isDataBean();

        return env -> {
            Object value = env.getArg(argName);
            if (value instanceof String && dataBean) {
                return JsonTool.parseBeanFromText(value.toString(), argType);
            }
            return value;
        };
    }

    public static Function<IDataFetchingEnvironment, Object> getArgsAsBean(IGenericType beanType) {
        return env -> {
            Object args = env.getArgs();
            return BeanTool.castBeanToType(args, beanType);
        };
    }
}
