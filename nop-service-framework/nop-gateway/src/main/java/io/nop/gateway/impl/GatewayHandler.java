/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.interceptor.DefaultGatewayInvocation;
import io.nop.gateway.core.interceptor.IGatewayInterceptor;
import io.nop.gateway.core.interceptor.GatewayInvocation;
import io.nop.gateway.model.GatewayModel;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.http.api.server.IAsyncBody;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.HttpRequest;
import io.nop.router.RouteValue;
import io.nop.router.trie.MatchResult;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.rpc.IRpcServiceLocator;
import io.nop.xlang.XLangConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.nop.gateway.GatewayErrors.*;

public class GatewayHandler {
    private final Supplier<GatewayModel> model;
    private final IRpcServiceLocator rpcServiceLocator;
    private final IHttpClient httpClient;

    public GatewayHandler(Supplier<GatewayModel> model, IRpcServiceLocator rpcServiceLocator, IHttpClient httpClient) {
        this.model = model;
        this.rpcServiceLocator = rpcServiceLocator;
        this.httpClient = httpClient;
    }


    public CompletionStage<ApiResponse<?>> handle(String path, ApiRequest<?> request, IServiceContext context) {
        // Create or get gateway context
        IGatewayContext gatewayCtx = createGatewayContext(context);
        gatewayCtx.setRequestPath(path);

        MatchResult<List<RouteValue<GatewayRouteModel>>> result = model.get().getRouter().matchPath(path);
        if (result != null) {
            List<RouteValue<GatewayRouteModel>> list = result.getValue();
            for (RouteValue<GatewayRouteModel> route : list) {
                CompletionStage<ApiResponse<?>> future = processRoute(route, result.getPath(), request, gatewayCtx);
                if (future != null)
                    return future;
            }
        }
        return null;
    }

    private IGatewayContext createGatewayContext(IServiceContext context) {
        if (context instanceof IGatewayContext) {
            return (IGatewayContext) context;
        }
        // Create new gateway context wrapping the existing service context
        GatewayContextImpl gatewayCtx = new GatewayContextImpl();
        gatewayCtx.setEvalScope(context.getEvalScope());
        return gatewayCtx;
    }

    private CompletionStage<ApiResponse<?>> processRoute(RouteValue<GatewayRouteModel> routeValue,
                                                          List<String> path,
                                                          ApiRequest<?> request,
                                                          IGatewayContext context) {
        GatewayRouteModel route = routeValue.getValue();
        IEvalScope scope = context.getEvalScope().newChildScope();
        initVars(scope, path, routeValue.getVarNames());

        // Set path variables in gateway context
        Map<String, Object> pathVars = buildPathVariables(scope, path, routeValue.getVarNames());
        context.setPathVariables(pathVars);
        context.setCurrentRoute(route);

        if (route.getMatch() != null) {
            if (!passMatchConditions(route, scope))
                return null;
        }

        try {
            // Check for streaming mode
            if (route.getStreaming() != null && isStreamingEnabled(route, scope)) {
                return handleStreamingRoute(route, request, context, scope);
            }

            // Load interceptors for this route
            List<IGatewayInterceptor> interceptors = loadInterceptors(route, context);

            // Create route execution function
            java.util.function.Function<ApiRequest<?>, CompletionStage<ApiResponse<?>>> routeExecution =
                req -> executeRouteLogic(route, req, context, scope);

            // Create invocation with interceptors
            DefaultGatewayInvocation invocation = new DefaultGatewayInvocation(route, interceptors, routeExecution);

            // Execute interceptor chain
            CompletionStage<ApiResponse<?>> promise = whenRequestReady(request).thenCompose(t -> {
                return invocation.proceed(request, context);
            });

            // Apply response and error handling
            CompletionStage<ApiResponse<?>> ret = promise.thenApply(v -> processResponse(route, v, scope));
            ret = ret.exceptionally(e2 -> buildResponse(route, request, e2, scope));
            return ret;
        } catch (Exception e) {
            return FutureHelper.toCompletionStage(buildResponse(route, request, e, scope));
        }
    }

    private boolean passMatchConditions(GatewayRouteModel route, IEvalScope scope) {
        // New API: use getMatch().getWhen() instead of passConditions()
        if (route.getMatch().getWhen() != null) {
            // call2 requires 4 parameters: (thisObj, arg1, arg2, scope)
            Boolean result = (Boolean) route.getMatch().getWhen().call2(null, null, null, scope);
            return result != null && result;
        }
        return true;
    }

    private boolean isStreamingEnabled(GatewayRouteModel route, IEvalScope scope) {
        // Check if streaming is enabled (may have a dynamic expression)
        io.nop.gateway.model.GatewayStreamingModel streaming = route.getStreaming();
        if (streaming.getEnabled() != null) {
            // IEvalAction has invoke(IEvalContext ctx) method
            Boolean result = (Boolean) streaming.getEnabled().invoke(scope);
            return result != null && result;
        }
        return true;
    }

    private CompletionStage<ApiResponse<?>> handleStreamingRoute(GatewayRouteModel route,
                                                               ApiRequest<?> request,
                                                               IGatewayContext context,
                                                               IEvalScope scope) {
        io.nop.gateway.model.GatewayStreamingModel streaming = route.getStreaming();

        // Set streaming mode in context
        context.setStreamingMode(true);

        try {
            // Call onStreamStart if configured
            if (streaming.getOnStreamStart() != null) {
                streaming.getOnStreamStart().call2(null, null, null, scope);
            }

            // Build HttpRequest for streaming
            HttpRequest httpRequest = buildHttpRequestForStreaming(route, request, scope);

            // Use IHttpClient to fetch streaming events
            java.util.concurrent.Flow.Publisher<io.nop.http.api.client.IServerEventResponse> eventPublisher =
                httpClient.fetchServerEventFlow(httpRequest, null);

            // Create a CompletableFuture for the streaming response
            // Note: For proper streaming support, this should return a Flow.Publisher directly
            // But for compatibility with current GatewayHandler interface, we wrap it
            java.util.concurrent.CompletableFuture<ApiResponse<?>> streamingFuture =
                new java.util.concurrent.CompletableFuture<>();

            eventPublisher.subscribe(new java.util.concurrent.Flow.Subscriber<io.nop.http.api.client.IServerEventResponse>() {
                private boolean hasError = false;

                @Override
                public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(io.nop.http.api.client.IServerEventResponse item) {
                    try {
                        // Call onStreamElement if configured
                        Object result = item.getData();
                        if (streaming.getOnStreamElement() != null) {
                            result = streaming.getOnStreamElement().call2(null, null, result, scope);
                        }
                        // For streaming, elements should be sent directly to client
                        // This implementation is simplified - in production, you'd need to
                        // stream each element to the HTTP response
                    } catch (Exception e) {
                        hasError = true;
                        onError(e);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    try {
                        // Call onStreamError if configured
                        Object errorResponse = throwable;
                        if (streaming.getOnStreamError() != null) {
                            try {
                                errorResponse = streaming.getOnStreamError().call2(null, null, throwable, scope);
                            } catch (Exception onErrorEx) {
                                // Use original throwable if onStreamError fails
                                errorResponse = throwable;
                            }
                        }
                        streamingFuture.completeExceptionally(NopException.adapt(throwable));
                    } catch (Exception e) {
                        streamingFuture.completeExceptionally(NopException.adapt(e));
                    }
                }

                @Override
                public void onComplete() {
                    try {
                        // Call onStreamComplete if configured
                        if (streaming.getOnStreamComplete() != null) {
                            streaming.getOnStreamComplete().call2(null, null, null, scope);
                        }
                        // Return an empty success response
                        streamingFuture.complete(ApiResponse.success(null));
                    } catch (Exception e) {
                        streamingFuture.completeExceptionally(NopException.adapt(e));
                    }
                }
            });

            return streamingFuture;

        } catch (Exception e) {
            // Call onStreamError if configured
            if (streaming.getOnStreamError() != null) {
                try {
                    Object errorResponse = streaming.getOnStreamError().call2(null, null, e, scope);
                    return FutureHelper.success(ApiResponse.wrap(errorResponse));
                } catch (Exception onErrorEx) {
                    // Fall through to throw the original exception
                }
            }
            throw NopException.adapt(e);
        }
    }

    /**
     * Build HttpRequest for streaming requests.
     * For URL-based invocation, the URL is configured in invoke.url.
     */
    private HttpRequest buildHttpRequestForStreaming(GatewayRouteModel route, ApiRequest<?> request, IEvalScope scope) {
        if (route.getInvoke() == null || route.getInvoke().getUrl() == null) {
            throw NopException.adapt(new UnsupportedOperationException(
                "Streaming requires URL-based invocation with invoke.url configured"));
        }

        // Evaluate URL expression if it's dynamic
        Object urlObj = route.getInvoke().getUrl().invoke(scope);
        if (urlObj == null) {
            throw new NopException(ERR_GATEWAY_NO_HTTP_CLIENT);
        }

        String url = urlObj.toString();
        HttpRequest httpRequest = new HttpRequest();
        httpRequest.setUrl(url);
        httpRequest.setMethod("POST");

        // Copy request headers
        if (request.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                httpRequest.header(entry.getKey(), entry.getValue());
            }
        }

        // Set request body
        httpRequest.setBody(request.getData());

        return httpRequest;
    }

    private List<IGatewayInterceptor> loadInterceptors(GatewayRouteModel route, IGatewayContext context) {
        // Load interceptors from gateway.xdef configuration
        List<IGatewayInterceptor> interceptors = new ArrayList<>();
        GatewayModel gatewayModel = model.get();

        if (gatewayModel.getInterceptors() != null) {
            // Filter and apply interceptors that match this route
            // For now, return all interceptors - more sophisticated filtering can be added later
            interceptors.addAll(gatewayModel.getInterceptors().stream()
                .filter(interceptorModel -> matchInterceptor(interceptorModel, route, context))
                .map(interceptorModel -> createInterceptor(interceptorModel))
                .collect(Collectors.toList()));
        }

        return interceptors;
    }

    private boolean matchInterceptor(io.nop.gateway.model.GatewayInterceptorModel interceptorModel,
                                    GatewayRouteModel route,
                                    IGatewayContext context) {
        // Check if interceptor's match conditions are satisfied
        if (interceptorModel.getMatch() != null) {
            if (interceptorModel.getMatch().getWhen() != null) {
                IEvalScope scope = context.getEvalScope().newChildScope();
                // call2 requires 4 parameters: (thisObj, arg1, arg2, scope)
                Boolean result = (Boolean) interceptorModel.getMatch().getWhen().call2(null, null, null, scope);
                return result != null && result;
            }
        }
        return true;
    }

    private IGatewayInterceptor createInterceptor(io.nop.gateway.model.GatewayInterceptorModel interceptorModel) {
        // Create interceptor from model
        // For now, create a simple wrapper - more sophisticated implementation needed
        return new IGatewayInterceptor() {
            @Override
            public void onRequest(ApiRequest<?> request, GatewayInvocation invocation, IServiceContext serviceContext)
                    throws Exception {
                // Execute interceptor source if configured
                if (interceptorModel.getSource() != null) {
                    IEvalScope scope = serviceContext.getEvalScope().newChildScope();
                    // call2 requires 4 parameters: (thisObj, arg1, arg2, scope)
                    interceptorModel.getSource().call2(null, null, null, scope);
                }
            }

            @Override
            public void onResponse(ApiResponse<?> response, GatewayInvocation invocation, IServiceContext serviceContext) {
                // Empty implementation
            }

            @Override
            public void onError(Throwable exception, GatewayInvocation invocation, IServiceContext serviceContext) {
                // Empty implementation
            }
        };
    }

    private CompletionStage<ApiResponse<?>> executeRouteLogic(GatewayRouteModel route,
                                                             ApiRequest<?> request,
                                                             IGatewayContext context,
                                                             IEvalScope scope) {
        Object result = null;

        // New API: use getInvoke().getSource() instead of getHandler()
        if (route.getInvoke() != null && route.getInvoke().getSource() != null) {
            result = route.getInvoke().getSource().call0(null, scope);
        }

        CompletionStage<ApiResponse<?>> future;
        // New API: check getInvoke() != null instead of isMock()
        // New API: use getInvoke().getServiceName() instead of getServiceName()
        boolean hasInvokeSource = route.getInvoke() != null && route.getInvoke().getSource() != null;
        String serviceName = route.getInvoke() != null ? route.getInvoke().getServiceName() : null;
        String url = route.getInvoke() != null ? (route.getInvoke().getUrl() != null ?
            route.getInvoke().getUrl().toString() : null) : null;

        if (hasInvokeSource) {
            // XPL invocation
            future = FutureHelper.toCompletionStage(result).thenApply(ApiResponse::wrap);
        } else if (serviceName != null) {
            // RPC invocation
            if (rpcServiceLocator == null)
                throw new NopException(ERR_GATEWAY_NO_RPC_SUPPORT);

            IRpcService rpcService = rpcServiceLocator.getService(serviceName);
            String svcMethod = ApiHeaders.getSvcAction(request);
            future = invokeRpc(rpcService, svcMethod, request, null);
        } else if (url != null) {
            // URL-based invocation using IHttpClient
            if (httpClient == null)
                throw new NopException(ERR_GATEWAY_NO_HTTP_CLIENT);

            // Evaluate URL expression if it's dynamic
            Object urlObj = route.getInvoke().getUrl().invoke(scope);
            if (urlObj == null) {
                throw new NopException(ERR_GATEWAY_NO_HTTP_CLIENT);
            }

            String evaluatedUrl = urlObj.toString();
            HttpRequest httpRequest = buildHttpRequest(route, evaluatedUrl, request);
            future = httpClient.fetchAsync(httpRequest, null).thenApply(httpResponse -> {
                ApiResponse<String> apiResponse = new ApiResponse<>();
                apiResponse.setHttpStatus(httpResponse.getHttpStatus());
                apiResponse.setData(httpResponse.getBodyAsString());
                return apiResponse;
            });
        } else {
            throw new NopException(ERR_GATEWAY_NO_RPC_SUPPORT).param("reason",
                "Neither source, serviceName, nor url is configured for invoke");
        }
        return future;
    }

    /**
     * Build HttpRequest for URL-based invocations.
     */
    private HttpRequest buildHttpRequest(GatewayRouteModel route, String url, ApiRequest<?> request) {
        HttpRequest httpRequest = new HttpRequest();
        httpRequest.setUrl(url);
        httpRequest.setMethod("POST");

        // Copy request headers
        if (request.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                httpRequest.header(entry.getKey(), entry.getValue());
            }
        }

        // Set request body
        httpRequest.setBody(request.getData());

        return httpRequest;
    }

    private Map<String, Object> buildPathVariables(IEvalScope scope, List<String> path, List<String> varNames) {
        // Build path variables map for gateway context
        java.util.Map<String, Object> pathVars = new java.util.HashMap<>();
        for (int i = 0, n = varNames.size(); i < n; i++) {
            String varName = varNames.get(i);
            if (varName != null) {
                String value = path.get(i);
                boolean tillEnd = varName.startsWith("*");
                if (tillEnd) {
                    varName = varName.substring(1);
                }

                if (i == n - 1 && tillEnd) {
                    value = StringHelper.join(path.subList(i, path.size()), "/");
                }
                pathVars.put(varName, value);
            }
        }
        return pathVars;
    }

    private CompletionStage<?> whenRequestReady(ApiRequest<?> request) {
        Object data = request.getData();
        if (data instanceof IAsyncBody) {
            return ((IAsyncBody) data).getTextAsync().thenApply(text -> {
                ((ApiRequest) request).setData(text);
                return text;
            });
        }
        return FutureHelper.success(request.getData());
    }

    private CompletionStage<ApiResponse<?>> invokeRpc(IRpcService service, String svcMethod,
                                                      ApiRequest<?> request, ICancelToken cancelToken) {
        Object data = request.getData();
        if (data instanceof IAsyncBody) {
            return ((IAsyncBody) data).getTextAsync().thenCompose(text -> {
                ((ApiRequest) request).setData(text);
                return service.callAsync(svcMethod, request, cancelToken);
            });
        }
        return service.callAsync(svcMethod, request, cancelToken);
    }

    private ApiResponse<?> buildResponse(GatewayRouteModel route, ApiRequest<?> request,
                                         Throwable e, IEvalScope scope) {
        if (route.getOnError() != null) {
            scope.setLocalValue(XLangConstants.SYS_VAR_EXCEPTION, e);
            Object response = route.getOnError().call0(null, scope);
            return ApiResponse.wrap(response);
        } else {
            return ErrorMessageManager.instance().buildResponse(request, e);
        }
    }

    private ApiResponse<?> processResponse(GatewayRouteModel route, Object response, IEvalScope scope) {
        Object ret;
        if (route.getOnResponse() == null) {
            ret = response;
        } else {
            scope.setLocalValue("response", response);
            ret = route.getOnResponse().call0(null, scope);
        }

        ApiResponse<?> res = ApiResponse.wrap(ret);

        if (route.isRawResponse()) {
            res.setWrapper(true);
        }
        return res;
    }

    private void initVars(IEvalScope scope, List<String> path, List<String> varNames) {
        for (int i = 0, n = varNames.size(); i < n; i++) {
            String varName = varNames.get(i);
            if (varName != null) {
                String value = path.get(i);
                boolean tillEnd = varName.startsWith("*");
                if (tillEnd) {
                    varName = varName.substring(1);
                }

                if (i == n - 1 && tillEnd) {
                    // 最后一个部分需要特殊识别
                    value = StringHelper.join(path.subList(i, path.size()), "/");
                }
                scope.setLocalValue(varName, value);
            }
        }
    }
}
