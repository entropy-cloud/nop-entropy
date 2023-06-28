/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.exceptions;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ApiStringHelper;

import java.util.Map;

public interface IErrorMessageManager {
    Throwable getRealCause(Throwable exception);

    ErrorBean buildErrorMessage(String locale, Throwable e, boolean includeStack, boolean onlyPublic, boolean logError);

    default ErrorBean buildErrorMessage(String locale, Throwable e, boolean includeStack, boolean onlyPublic) {
        return buildErrorMessage(locale, e, includeStack, onlyPublic, true);
    }

    String resolveDescription(String locale, String message, Map<String, ?> params);

    default void resolveErrorDescription(ErrorBean error) {
        if (ApiStringHelper.isEmpty(error.getDescription())) {
            error.setDescription(resolveDescription(null, error.getErrorCode(), error.getParams()));
        }
    }

    default ErrorBean buildErrorMessage(String locale, Throwable e) {
        return buildErrorMessage(locale, e, false, true);
    }

    default ApiResponse<?> buildResponse(ApiRequest<?> request, Throwable e) {
        ApiResponse<?> res = buildResponse(ApiHeaders.getLocale(request), e);
        return res;
    }

    ApiResponse<?> buildResponse(String locale, Throwable e);

    ApiResponse<?> buildResponse(String locale, ErrorBean error);
}
