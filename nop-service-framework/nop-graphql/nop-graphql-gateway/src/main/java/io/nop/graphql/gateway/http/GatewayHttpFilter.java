/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.gateway.http;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.graphql.gateway.impl.GatewayHandler;
import io.nop.graphql.gateway.model.GatewayModel;
import io.nop.http.api.HttpStatus;
import io.nop.http.api.server.IAsyncBody;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.http.api.server.IHttpServerFilter;
import io.nop.api.core.rpc.IRpcServiceLocator;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static io.nop.graphql.gateway.GraphqlGatewayConfigs.CFG_GATEWAY_MODEL_PATH;

public class GatewayHttpFilter implements IHttpServerFilter {

    private IRpcServiceLocator rpcServiceLocator;

    @Inject
    public void setRpcServiceLocator(@Nullable IRpcServiceLocator rpcServiceLocator) {
        this.rpcServiceLocator = rpcServiceLocator;
    }

    @Override
    public int order() {
        return NORMAL_PRIORITY - 50;
    }

    @Override
    public CompletionStage<Void> filterAsync(IHttpServerContext context, Supplier<CompletionStage<Void>> next) {
        GatewayModel model;
        try {
            model = loadGatewayModel();
            if (model == null) {
                return next.get();
            }
        } catch (Exception e) {
            String locale = ContextProvider.currentLocale();
            ApiResponse<?> res = ErrorMessageManager.instance().buildResponseForException(locale, e);
            write(context, res);
            return FutureHelper.success(null);
        }

        GatewayHandler handler = new GatewayHandler(() -> model, rpcServiceLocator);
        String path = context.getRequestPath();

        IServiceContext ctx = new ServiceContextImpl();

        ApiRequest<?> request = buildRequest(context);
        CompletionStage<ApiResponse<?>> future = handler.handle(path, request, ctx);
        if (future == null)
            return next.get();

        context.resumeRequest();

        return future.thenApply(ret -> {
            write(context, ret);
            return null;
        });
    }

    protected void write(IHttpServerContext context, ApiResponse<?> response) {
        int status = response.getHttpStatus();
        if (status == 0)
            status = HttpStatus.SC_OK;

        boolean wrapper = response.isWrapper();
        String body;
        if (wrapper) {
            body = JsonTool.serialize(response.getData(), false);
        } else {
            body = JsonTool.serialize(response, false);
        }

        if (response.hasHeaders()) {
            writeHeaders(context, response.getHeaders());
        }
        context.sendResponse(status, body);
    }

    protected void writeHeaders(IHttpServerContext context, Map<String, Object> headers) {
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            context.setResponseHeader(entry.getKey(), entry.getValue());
        }
    }

    protected ApiRequest<?> buildRequest(IHttpServerContext context) {
        ApiRequest<IAsyncBody> request = new ApiRequest<>();
        request.setHeaders(context.getRequestHeaders());
        request.setData(context.getRequestBody());
        return request;
    }

    private GatewayModel loadGatewayModel() {
        String modelPath = CFG_GATEWAY_MODEL_PATH.get();
        if (StringHelper.isEmpty(modelPath))
            return null;
        return (GatewayModel) ResourceComponentManager.instance().loadComponentModel(modelPath);
    }
}
