/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.core.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ApiHeaders;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.rpc.api.IRpcMessageAdapter;

public class DefaultRpcMessageAdapter implements IRpcMessageAdapter<ApiRequest<?>, ApiResponse<?>> {
    public static final DefaultRpcMessageAdapter INSTANCE = new DefaultRpcMessageAdapter();

    @Override
    public String getRpcAction(ApiRequest<?> request) {
        return ApiHeaders.getSvcAction(request);
    }

    @Override
    public void setRpcAction(ApiRequest<?> request, String action) {
        ApiHeaders.setSvcAction(request, action);
    }

    @Override
    public ApiResponse<?> getErrorResponse(Throwable e, ApiRequest<?> request) {
        String locale = ApiHeaders.getLocale(request);
        return ErrorMessageManager.instance().buildResponse(locale, e);
    }

    @Override
    public long getTimeout(ApiRequest<?> msg) {
        return ApiHeaders.getTimeout(msg, -1);
    }

    @Override
    public String getMessageId(ApiRequest<?> request) {
        return ApiHeaders.getId(request);
    }

    @Override
    public boolean isOneWay(ApiRequest<?> request) {
        return ApiHeaders.isOneWay(request);
    }

    @Override
    public String getCorrelationId(ApiResponse<?> response) {
        return ApiHeaders.getRelId(response);
    }
}