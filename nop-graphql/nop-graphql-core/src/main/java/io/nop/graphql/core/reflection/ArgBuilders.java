/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.reflection;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.converter.FunctionalInterfaceConverter;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.graphql.core.IDataFetchingEnvironment;

import java.util.function.Function;

import static io.nop.graphql.core.GraphQLErrors.ARG_ARG_NAME;

public class ArgBuilders {
    static final SourceLocation s_loc = SourceLocation.fromClass(ArgBuilders.class);

    public static IServiceActionArgBuilder getActionArgFromRequest(String name, IGenericType type) {
        return (req, selection, ctx) -> {
            if (req == null)
                return null;
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
            if (value instanceof IEvalFunction) {
                return FunctionalInterfaceConverter.convertToFunctional(s_loc, (IEvalFunction) value,
                        type.getRawClass(), ctx.getEvalScope(), err -> new NopException(err).param(ARG_ARG_NAME, name));
            }
            return BeanTool.castBeanToType(value, type);
        };
    }

    public static IServiceActionArgBuilder getSelection() {
        return (req, selection, ctx) -> selection;
    }

    public static IServiceActionArgBuilder getContext() {
        return (req, selection, ctx) -> ctx;
    }

    public static IServiceActionArgBuilder getActionRequest(IGenericType type) {
        return (req, selection, ctx) -> {
            if (req == null)
                return null;

            if (type == null)
                return req;

            if (req instanceof String && type.isDataBean()) {
                req = JsonTool.parse(req.toString());
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
            req.setHeaders(env.getGraphQLExecutionContext().getResponseHeaders());
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
