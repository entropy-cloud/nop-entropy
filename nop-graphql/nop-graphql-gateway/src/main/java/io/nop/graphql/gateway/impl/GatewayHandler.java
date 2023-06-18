package io.nop.graphql.gateway.impl;

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
import io.nop.graphql.gateway.model.GatewayModel;
import io.nop.graphql.gateway.model.GatewayRouteModel;
import io.nop.http.api.server.IAsyncBody;
import io.nop.router.RouteValue;
import io.nop.router.trie.MatchResult;
import io.nop.rpc.api.IRpcService;
import io.nop.rpc.api.IRpcServiceLocator;
import io.nop.xlang.XLangConstants;

import java.util.List;
import java.util.concurrent.CompletionStage;

import static io.nop.graphql.gateway.GraphqlGatewayErrors.ERR_GATEWAY_NO_RPC_SUPPORT;

public class GatewayHandler {
    private final GatewayModel model;
    private final IRpcServiceLocator rpcServiceLocator;

    public GatewayHandler(GatewayModel model, IRpcServiceLocator rpcServiceLocator) {
        this.model = model;
        this.rpcServiceLocator = rpcServiceLocator;
    }


    public CompletionStage<ApiResponse<?>> handle(String path, ApiRequest<?> request, IServiceContext context) {
        MatchResult<List<RouteValue<GatewayRouteModel>>> result = model.getRouter().matchPath(path);
        if (result != null) {
            List<RouteValue<GatewayRouteModel>> list = result.getValue();
            for (RouteValue<GatewayRouteModel> route : list) {
                CompletionStage<ApiResponse<?>> future = processRoute(route, result.getPath(), request, context);
                if (future != null)
                    return future;
            }
        }
        return null;
    }

    private CompletionStage<ApiResponse<?>> processRoute(RouteValue<GatewayRouteModel> routeValue,
                                                         List<String> path,
                                                         ApiRequest<?> request,
                                                         IServiceContext context) {
        GatewayRouteModel route = routeValue.getValue();
        IEvalScope scope = context.getEvalScope().newChildScope();
        initVars(scope, path, routeValue.getVarNames());

        if (route.getMatch() != null) {
            if (!route.getMatch().passConditions(scope))
                return null;
        }

        try {
            CompletionStage<ApiResponse<?>> promise = whenRequestReady(request).thenCompose(t -> {
                Object result = null;

                if (route.getHandler() != null) {
                    result = route.getHandler().invoke(scope);
                }

                CompletionStage<ApiResponse<?>> future;
                if (route.isMock() || route.getServiceName() == null) {
                    future = FutureHelper.toCompletionStage(result).thenApply(ApiResponse::wrap);
                } else {
                    if (rpcServiceLocator == null)
                        throw new NopException(ERR_GATEWAY_NO_RPC_SUPPORT);

                    IRpcService rpcService = rpcServiceLocator.getService(route.getServiceName());
                    String svcMethod = ApiHeaders.getSvcAction(request);
                    future = invokeRpc(rpcService, svcMethod, request, context);
                }
                return future;
            });

            CompletionStage<ApiResponse<?>> ret = promise.thenApply(v -> processResponse(route, v, scope));
            ret = ret.exceptionally(e2 -> buildResponse(route, request, e2, scope));
            return ret;
        } catch (Exception e) {
            return FutureHelper.toCompletionStage(buildResponse(route, request, e, scope));
        }
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
            Object response = route.getOnError().invoke(scope);
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
            ret = route.getOnResponse().invoke(scope);
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