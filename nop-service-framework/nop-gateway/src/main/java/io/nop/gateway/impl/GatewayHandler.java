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
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.executor.*;
import io.nop.gateway.core.interceptor.IGatewayInterceptor;
import io.nop.gateway.model.GatewayInterceptorModel;
import io.nop.gateway.model.GatewayMatchModel;
import io.nop.gateway.model.GatewayModel;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.http.api.client.IHttpClient;
import io.nop.record_mapping.IRecordMappingManager;
import io.nop.router.RouteValue;
import io.nop.router.trie.MatchResult;
import io.nop.rpc.core.utils.RpcHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Gateway核心处理器
 *
 * <p>负责路由匹配和请求处理编排，使用RouteExecutor执行实际的路由逻辑。</p>
 *
 * <p>支持的特性：</p>
 * <ul>
 *   <li>httpMethod匹配 - 根据HTTP方法过滤路由</li>
 *   <li>requestMapping/responseMapping - 请求响应消息映射</li>
 *   <li>onRequest/onResponse回调 - 请求响应处理回调</li>
 *   <li>invoke/forward - RPC调用或路由转发</li>
 *   <li>streaming - 流式响应</li>
 *   <li>errorRouteId - 错误路由降级</li>
 *   <li>拦截器缓存 - 提升拦截器获取性能</li>
 * </ul>
 */
public class GatewayHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GatewayHandler.class);

    private final GatewayModel model;
    private final IRpcServiceInvoker rpcServiceInvoker;
    private final IHttpClient httpClient;
    private final IRecordMappingManager mappingManager;

    // 处理器组件
    private final RouteExecutor routeExecutor;
    private final MappingProcessor mappingProcessor;
    private final InvokeProcessor invokeProcessor;
    private final ForwardProcessor forwardProcessor;
    private final StreamingProcessor streamingProcessor;

    public GatewayHandler(GatewayModel model, IRpcServiceInvoker rpcServiceInvoker,
                          IHttpClient httpClient, IRecordMappingManager mappingManager) {
        this.model = model;
        this.rpcServiceInvoker = rpcServiceInvoker;
        this.httpClient = httpClient;
        this.mappingManager = mappingManager;

        // 初始化处理器组件
        this.mappingProcessor = new MappingProcessor(mappingManager);
        this.invokeProcessor = new InvokeProcessor(rpcServiceInvoker, httpClient);
        this.forwardProcessor = new ForwardProcessor(model);
        this.streamingProcessor = new StreamingProcessor(httpClient, mappingProcessor);
        this.routeExecutor = new RouteExecutor(
                mappingProcessor, invokeProcessor, forwardProcessor, streamingProcessor);
    }

    public GatewayModel getModel() {
        return model;
    }

    /**
     * 处理网关请求的主入口
     */
    public CompletionStage<ApiResponse<?>> handle(ApiRequest<?> request, IGatewayContext svcCtx) {
        // 匹配路由
        MatchResult<List<RouteValue<GatewayRouteModel>>> result = model.getRouter().matchPath(svcCtx.getRequestPath());
        if (result != null) {
            List<RouteValue<GatewayRouteModel>> list = result.getValue();
            for (RouteValue<GatewayRouteModel> routeValue : list) {
                CompletionStage<ApiResponse<?>> future = processRoute(routeValue, result.getPath(), request, svcCtx);
                if (future != null) {
                    return future;
                }
            }
        }
        return null;
    }

    /**
     * 处理单个路由
     */
    private CompletionStage<ApiResponse<?>> processRoute(RouteValue<GatewayRouteModel> routeValue,
                                                         List<String> path,
                                                         ApiRequest<?> request,
                                                         IGatewayContext context) {
        GatewayRouteModel route = routeValue.getValue();

        // 初始化路径变量
        initPathVariables(context, path, routeValue.getVarNames());
        context.setCurrentRoute(route);

        // 检查匹配条件
        if (!matchRoute(route, request, context)) {
            return null;
        }

        // 加载拦截器
        List<IGatewayInterceptor> interceptors = loadInterceptors(context);

        // 使用RouteExecutor执行路由
        CompletionStage<ApiResponse<?>> promise = routeExecutor.execute(route, request, context, interceptors);
        promise = promise.exceptionally(err -> {
            LOG.error("nop.gateway.process-route-fail:routeId={}", route.getId(), err);
            String locale = ContextProvider.currentLocale();
            return ErrorMessageManager.instance().buildResponseForException(locale, err);
        });
        promise = promise.thenApply(res -> {
            res.setWrapper(Boolean.TRUE.equals(route.getUnwrapResponse()));
            return res;
        });

        // 应用错误处理
        return promise;
    }

    /**
     * 匹配路由条件
     *
     * <p>包括httpMethod匹配和when条件匹配。</p>
     */
    private boolean matchRoute(GatewayRouteModel route, ApiRequest<?> request, IGatewayContext svcCtx) {
        GatewayMatchModel match = route.getMatch();
        if (match == null) {
            return true;
        }

        // 1. 检查httpMethod匹配
        Set<String> httpMethods = match.getHttpMethod();
        if (httpMethods != null && !httpMethods.isEmpty()) {
            String requestMethod = getRequestMethod(request);
            if (!httpMethods.contains(requestMethod)) {
                return false;
            }
        }

        // 2. 检查when条件
        if (match.getWhen() != null) {
            Object result = match.getWhen().call2(null, request, svcCtx, svcCtx.getEvalScope());
            return ConvertHelper.toBoolean(result);
        }

        return true;
    }

    /**
     * 获取请求的HTTP方法
     *
     * <p>优先级：RpcHelper property > X-HTTP-Method头 > X-Http-Method-Override头 > POST默认</p>
     */
    private String getRequestMethod(ApiRequest<?> request) {
        // 1. 优先从RpcHelper获取（标准方式）
        String method = RpcHelper.getHttpMethod(request);
        if (method != null) {
            return method.toUpperCase();
        }
        // 默认返回POST（兼容RPC调用）
        return "POST";
    }

    /**
     * 加载匹配的拦截器
     */
    private List<IGatewayInterceptor> loadInterceptors(IGatewayContext svcCtx) {
        if (model.getInterceptors() == null || model.getInterceptors().isEmpty())
            return Collections.emptyList();

        Set<GatewayInterceptorModel> matches = model.getInterceptorRouter().matchAllPathValues(svcCtx.getRequestPath());

        // 必须保持interceptor 的顺序
        return model.getInterceptors().stream()
                .filter(interceptorModel -> matches.contains(interceptorModel) && matchInterceptor(interceptorModel, svcCtx))
                .map(interceptorModel -> interceptorModel.getOrCreateInterceptor(svcCtx))
                .collect(Collectors.toList());
    }

    /**
     * 检查拦截器是否匹配当前路由
     */
    private boolean matchInterceptor(GatewayInterceptorModel interceptorModel,
                                     IGatewayContext svcCtx) {
        if (interceptorModel.getMatch() != null) {
            GatewayMatchModel match = interceptorModel.getMatch();
            if (match.getWhen() != null) {
                Object result = match.getWhen().call2(null, svcCtx.getRequest(), svcCtx, svcCtx.getEvalScope());
                return ConvertHelper.toBoolean(result);
            }
        }
        return true;
    }


    /**
     * 初始化路径变量：同时设置到执行作用域和上下文中
     */
    private void initPathVariables(IGatewayContext context,
                                   List<String> path, List<String> varNames) {
        Map<String, Object> pathVars = new java.util.HashMap<>();
        for (int i = 0, n = varNames.size(); i < n; i++) {
            String varName = varNames.get(i);
            if (varName != null) {
                String value = path.get(i);
                boolean tillEnd = varName.startsWith("*");
                if (tillEnd) {
                    varName = varName.substring(1);
                }

                if (i == n - 1 && tillEnd) {
                    value = io.nop.commons.util.StringHelper.join(path.subList(i, path.size()), "/");
                }
                pathVars.put(varName, value);
            }
        }
        context.setPathVariables(pathVars);
    }

}
