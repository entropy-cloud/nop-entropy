/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.partial;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.exceptions.NopMissingHandlerException;
import io.nop.api.core.util.ApiHeaders;

import java.util.Map;
import java.util.function.Function;

import static io.nop.api.core.util.Guard.notNull;
import static io.nop.commons.CommonErrors.ARG_ACTION;
import static io.nop.commons.CommonErrors.ERR_UNDEFINED_HANDLER_FOR_REQUEST_ACTION;

public class RequestActionDispatchFunction implements IPartialFunction<ApiRequest<?>, Object> {
    private final Map<String, Function<ApiRequest<?>, ?>> functions;
    private final IPartialFunction<ApiRequest<?>, Object> defaultHandler;

    public RequestActionDispatchFunction(Map<String, Function<ApiRequest<?>, ?>> functions,
                                         IPartialFunction<ApiRequest<?>, Object> defaultHandler) {
        this.functions = notNull(functions, "functions is null");
        this.defaultHandler = defaultHandler;
    }

    public RequestActionDispatchFunction(Map<String, Function<ApiRequest<?>, ?>> functions) {
        this(functions, null);
    }

    @Override
    public boolean isDefinedAt(ApiRequest<?> apiRequest) {
        String action = getAction(apiRequest);
        if (action != null) {
            if (functions.containsKey(action))
                return true;
        }
        if (defaultHandler != null)
            return defaultHandler.isDefinedAt(apiRequest);
        return false;
    }

    String getAction(ApiRequest<?> request) {
        if (request == null)
            return null;
        return ApiHeaders.getSvcAction(request);
    }

    @Override
    public Object apply(ApiRequest<?> apiRequest) {
        String action = getAction(apiRequest);
        if (action != null) {
            Function<ApiRequest<?>, ?> fn = functions.get(action);
            if (fn != null)
                return fn.apply(apiRequest);
        }

        if (defaultHandler != null)
            return defaultHandler.apply(apiRequest);

        throw new NopMissingHandlerException(ERR_UNDEFINED_HANDLER_FOR_REQUEST_ACTION).param(ARG_ACTION, action);
    }
}