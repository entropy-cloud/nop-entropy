/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.makerchecker;

import io.nop.api.core.annotations.biz.BizMakerCheckerMeta;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.DateHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;

import java.util.concurrent.CompletionStage;

public class MakerCheckerTryServiceAction implements IServiceAction {
    private final IMakerCheckerProvider makerCheckerProvider;
    private final IServiceAction action;
    private final BizMakerCheckerMeta makerCheckerMeta;
    private final String bizMethod;

    public MakerCheckerTryServiceAction(IMakerCheckerProvider makerCheckerProvider, IServiceAction action,
                                        BizMakerCheckerMeta makerCheckerMeta, String bizMethod) {
        this.makerCheckerProvider = makerCheckerProvider;
        this.action = action;
        this.makerCheckerMeta = makerCheckerMeta;
        this.bizMethod = bizMethod;
    }

    @Override
    public Object invoke(Object request, FieldSelectionBean selection, IServiceContext context) {
        Object result = action.invoke(request, selection, context);
        if (result instanceof CompletionStage<?>) {
            return ((CompletionStage<?>) result).thenCompose(v -> sendForCheck(v, request, selection, context));
        } else {
            return sendForCheck(result, request, selection, context);
        }
    }

    CompletionStage<Object> sendForCheck(Object result, Object request, FieldSelectionBean selection,
                                         IServiceContext context) {
        IUserContext userContext = context.getUserContext();
        SendForCheckRequest req = new SendForCheckRequest();
        if (userContext != null) {
            req.setMakerId(userContext.getUserId());
            req.setMakerName(userContext.getUserName());
        }
        req.setMakeTime(DateHelper.millisToDateTime(CoreMetrics.currentTimeMillis()));
        req.setRequest(toApiRequest(request, selection, context));
        req.setTryMethod(makerCheckerMeta.getTryMethod());
        req.setCancelMethod(makerCheckerMeta.getCancelMethod());
        req.setBizMethod(bizMethod);
        req.setTryResult(toTryResponse(result));
        return makerCheckerProvider.sendForCheckAsync(req).thenApply(reqId -> {
            return ApiResponse.success(reqId);
        });
    }

    private ApiRequest<?> toApiRequest(Object request, FieldSelectionBean selection, IServiceContext context) {
        if (request instanceof ApiRequest<?>)
            return (ApiRequest<?>) request;

        ApiRequest<Object> ret = new ApiRequest<>();
        ret.setSelection(selection);
        ret.setData(request);
        ret.setHeaders(context.getRequestHeaders());
        return ret;
    }

    private ApiResponse<?> toTryResponse(Object response) {
        ApiResponse<Object> ret = new ApiResponse<>();
        if (response instanceof ApiResponse<?>) {
            ret.setTryResponse(((ApiResponse<?>) response).getData());
        } else {
            ret.setTryResponse(response);
        }

        return ret;
    }
}