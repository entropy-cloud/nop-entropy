/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.functional.partial;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.exceptions.NopMissingHandlerException;

import java.util.Map;
import java.util.function.Function;

import static io.nop.api.core.util.Guard.notNull;
import static io.nop.commons.CommonErrors.ARG_CLASS;
import static io.nop.commons.CommonErrors.ERR_UNDEFINED_HANDLER_FOR_REQUEST_TYPE;

public class RequestTypeDispatchFunction implements IPartialFunction<ApiRequest<?>, Object> {
    private final Map<Class<?>, Function<ApiRequest<?>, ?>> functions;
    private final IPartialFunction<ApiRequest<?>, Object> defaultHandler;

    public RequestTypeDispatchFunction(Map<Class<?>, Function<ApiRequest<?>, ?>> functions,
                                       IPartialFunction<ApiRequest<?>, Object> defaultHandler) {
        this.functions = notNull(functions, "functions is null");
        this.defaultHandler = defaultHandler;
    }

    public RequestTypeDispatchFunction(Map<Class<?>, Function<ApiRequest<?>, ?>> functions) {
        this(functions, null);
    }

    @Override
    public boolean isDefinedAt(ApiRequest<?> apiRequest) {
        Object o = apiRequest.getData();
        if (o != null) {
            if (functions.containsKey(o.getClass()))
                return true;
        }
        if (defaultHandler != null)
            return defaultHandler.isDefinedAt(apiRequest);
        return false;
    }

    @Override
    public Object apply(ApiRequest<?> apiRequest) {
        Object o = apiRequest.getData();
        if (o != null) {
            Function<ApiRequest<?>, ?> fn = functions.get(o.getClass());
            if (fn != null)
                return fn.apply(apiRequest);
        }

        if (defaultHandler != null)
            return defaultHandler.apply(apiRequest);

        throw new NopMissingHandlerException(ERR_UNDEFINED_HANDLER_FOR_REQUEST_TYPE).param(ARG_CLASS,
                o == null ? null : o.getClass());
    }
}