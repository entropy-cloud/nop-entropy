/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.web;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.json.JSON;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.commons.functional.ITriFunction;
import io.nop.commons.functional.Lazy;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.core.resource.IResource;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.IGraphQLLogger;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.ICancelTokenManger;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.graphql.core.jsonrpc.JsonRpcService;
import io.nop.graphql.core.utils.GraphQLArgsHelper;
import io.nop.graphql.core.utils.GraphQLResponseHelper;
import io.nop.rpc.api.ContextBinder;
import io.nop.rpc.api.IRpcServiceInvoker;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_MAKER_CHECKER_ENABLED;

public abstract class GraphQLWebService {
    static final Logger LOG = LoggerFactory.getLogger(GraphQLWebService.class);

    protected final Lazy<IGraphQLLogger> graphQLLogger = Lazy.of(() -> {
        return BeanContainer.instance().tryGetBeanByType(IGraphQLLogger.class);
    });

    public GraphQLWebService() {

    }

    protected <T> CompletionStage<T> runGraphQL(String body,
                                                BiFunction<GraphQLResponseBean, IGraphQLExecutionContext, T> responseBuilder) {
        IGraphQLEngine engine = BeanContainer.instance().getBeanByType(IGraphQLEngine.class);
        long beginTime = CoreMetrics.currentTimeMillis();

        ContextBinder binder = new ContextBinder();
        IGraphQLExecutionContext context = null;
        try {

            GraphQLRequestBean request = (GraphQLRequestBean) JSON.parseToBean(null, body, GraphQLRequestBean.class);
            if (request == null)
                throw new IllegalArgumentException("null request");
            LOG.debug("nop.graphql.parse:vars={},document=\n{}", request.getVariables(), request.getQuery());

            context = engine.newGraphQLContext(request);
            context.setMakerCheckerEnabled(CFG_GRAPHQL_MAKER_CHECKER_ENABLED.get());
            context.setRequestHeaders(getHeaders());
            prepareContext(context);

            ApiRequest<GraphQLRequestBean> req = ApiRequest.build(request);
            req.setHeaders(context.getRequestHeaders());
            binder.init(req);

            IGraphQLExecutionContext ctx = context;
            return engine.executeGraphQLAsync(context).thenApply(res -> {
                LOG.info("nop.graphql.end-graphql-request:usedTime={},query={},errorCode={},msg={}",
                        CoreMetrics.currentTimeMillis() - beginTime, request.getQuery(), res.getErrorCode(),
                        res.getMsg());
                logGraphQLResult(beginTime, res, null, ctx);
                return responseBuilder.apply(res, ctx);
            }).exceptionally(e -> {
                if (e != null) {
                    logGraphQLResult(beginTime, null, e, ctx);
                }
                return responseBuilder.apply(engine.buildGraphQLResponse(null, e, ctx), ctx);
            }).whenComplete((v, e) -> {
                binder.close();
            });
        } catch (Exception e) {
            try {
                return FutureHelper.success(responseBuilder.apply(engine.buildGraphQLResponse(null, e, context), context));
            } finally {
                binder.close();
                logGraphQLResult(beginTime, null, e, context);
            }
        }
    }

    /**
     * 在 {@link #runGraphQL(String, BiFunction)}
     * 的基础上对响应状态和响应体数据进行统一转换，在处理 GraphQL 风格请求时，仅需要在回调函数
     * `responseBuilder` 中根据 header、body、status 处理响应即可，无需关注对 header、body 等的转换。
     * <p/>
     * 默认响应体数据的构造逻辑为：<per>
     * String body = JsonTool.serialize(response, false);
     * </per>
     *
     * @param responseBuilder 响应处理函数，其有以下入参：<ul>
     *                        <li>headers: 响应头 Map 集合，来自于 {@link IServiceContext#getResponseHeaders()}。不为 `null`；</li>
     *                        <li>body: 响应体数据；</li>
     *                        <li>status: 响应状态，始终为 `200`；</li>
     *                        </ul>
     */
    protected <T> CompletionStage<T> runGraphQL(String body,
                                                ITriFunction<Map<String, Object>, String, Integer, T> responseBuilder) {
        return runGraphQL(body, (response, gqlContext) -> {
            String json = JsonTool.serialize(response, false);
            Map<String, Object> headers = gqlContext != null ? gqlContext.getResponseHeaders() : null;

            return responseBuilder.apply(headers != null ? new HashMap<>(headers) : new HashMap<>(), json, 200);
        });
    }

    protected void logRpcResult(long beginTime, ApiResponse<?> result, Throwable exception, IGraphQLExecutionContext context) {
        graphQLLogger.runIfPresent(logger -> {
            logger.onRpcExecute(context, beginTime, result, exception);
        });
    }

    protected void logGraphQLResult(long beginTime, GraphQLResponseBean result, Throwable exception, IGraphQLExecutionContext context) {
        graphQLLogger.runIfPresent(logger -> {
            logger.onGraphQLExecute(context, beginTime, result, exception);
        });
    }

    protected IGraphQLExecutionContext newGraphQLContext(IGraphQLEngine engine) {
        return engine.newGraphQLContextFromContext(null);
    }

    protected Response buildJaxrsGraphQLResponse(GraphQLResponseBean res, IGraphQLExecutionContext context) {
        String body = JSON.stringify(res);
        LOG.debug("nop.graphql.response:{}", body);

        return JaxrsHelper.buildJaxrsResponse(context != null ? context.getResponseHeaders() : null, body, 200);
    }

    protected <T> CompletionStage<T> runProxy(String serviceName,
                                              String serviceMethod,
                                              Supplier<ApiRequest<?>> requestBuilder,
                                              ITriFunction<Map<String, Object>, String, Integer, T> responseBuilder) {
        return runProxy(serviceName, serviceMethod, requestBuilder, (response, gqlContext) -> {
            return buildRestResponse(response, responseBuilder);
        });
    }

    protected <T> CompletionStage<T> runProxy(String serviceName,
                                              String serviceMethod,
                                              Supplier<ApiRequest<?>> requestBuilder,
                                              BiFunction<ApiResponse<?>, IGraphQLExecutionContext, T> responseBuilder) {
        IRpcServiceInvoker invoker = (IRpcServiceInvoker) BeanContainer.instance()
                .getBean(GraphQLConstants.BEAN_NOP_PROXY_RPC_SERVICE_INVOKER);
        IGraphQLEngine graphqlEngine = BeanContainer.getBeanByType(IGraphQLEngine.class);

        long beginTime = CoreMetrics.currentTimeMillis();
        ApiRequest<?> request = requestBuilder.get();

        ContextBinder binder = new ContextBinder();

        if (LOG.isDebugEnabled()) {
            LOG.debug("nop.graphql.rpc-proxy:serviceName={}, serviceMethod={},request={}", serviceName,
                    serviceMethod, JSON.serialize(request, true));
        }

        IGraphQLExecutionContext ctx = newGraphQLContext(graphqlEngine);
        ICancelTokenManger cancelTokenManger = graphqlEngine.getCancelTokenManager();

        binder.init(request);
        IAsyncFunctionInvoker wrapped = cancelTokenManger.buildInvoker(ctx.getServiceContext());

        try {
            CompletionStage<ApiResponse<?>> future = wrapped.invokeAsync(req -> {
                return invoker.invokeAsync(serviceName, serviceMethod, req, ctx.getServiceContext());
            }, request);

            return future.thenApply(res -> {
                LOG.info("nop.graphql.end-rpc-proxy:usedTime={},serviceName={},serviceMethod={},errorCode={},msg={}",
                        CoreMetrics.currentTimeMillis() - beginTime, serviceName, serviceMethod,
                        res.getCode(), res.getMsg());
                logRpcResult(beginTime, res, null, ctx);
                return responseBuilder.apply(res, ctx);
            }).exceptionally(e -> {
                if (e != null) {
                    logRpcResult(beginTime, null, e, ctx);
                }
                return responseBuilder.apply(graphqlEngine.buildRpcResponse(null, e, ctx), ctx);
            }).whenComplete((r, e) -> {
                binder.close();
            });
        } catch (Exception e) {
            try {
                return FutureHelper.success(responseBuilder.apply(
                        graphqlEngine.buildRpcResponse(null, e, ctx), ctx));
            } finally {
                binder.close();
                logRpcResult(beginTime, null, e, ctx);
            }
        }
    }

    protected <T> CompletionStage<T> runRest(GraphQLOperationType expectedOpType, String operationName,
                                             Supplier<ApiRequest<?>> requestBuilder,
                                             BiFunction<ApiResponse<?>, IGraphQLExecutionContext, T> responseBuilder
    ) {
        IGraphQLEngine engine = BeanContainer.instance().getBeanByType(IGraphQLEngine.class);

        long beginTime = CoreMetrics.currentTimeMillis();

        ContextBinder binder = new ContextBinder();
        IGraphQLExecutionContext context = null;
        try {
            context = newGraphQLContext(engine);
            ApiRequest<?> request = requestBuilder.get();

            if (LOG.isDebugEnabled()) {
                LOG.debug("nop.graphql.rpc-request:operationName={},request={}", operationName,
                        JSON.serialize(request, true));
            }
            engine.initRpcContext(context, expectedOpType, operationName, request);
            context.setMakerCheckerEnabled(CFG_GRAPHQL_MAKER_CHECKER_ENABLED.get());
            prepareContext(context);

            binder.init(request);

            IGraphQLExecutionContext ctx = context;
            return engine.executeRpcAsync(context).thenApply(res -> {
                LOG.info("nop.graphql.end-rpc-request:usedTime={},operationName={},errorCode={},msg={}",
                        CoreMetrics.currentTimeMillis() - beginTime, operationName, res.getCode(), res.getMsg());
                logRpcResult(beginTime, res, null, ctx);
                return responseBuilder.apply(res, ctx);
            }).exceptionally(e -> {
                if (e != null) {
                    logRpcResult(beginTime, null, e, ctx);
                }
                return responseBuilder.apply(engine.buildRpcResponse(null, e, ctx), ctx);
            }).whenComplete((r, e) -> {
                binder.close();
            });
        } catch (Exception e) {
            try {
                return FutureHelper.success(responseBuilder.apply(engine.buildRpcResponse(null, e, context), context));
            } finally {
                binder.close();
                logRpcResult(beginTime, null, e, context);
            }
        }
    }

    /**
     * 在 {@link #runRest(GraphQLOperationType, String, Supplier, BiFunction)}
     * 的基础上对响应状态和响应体数据进行统一转换，在处理 Rest 风格请求时，仅需要在回调函数
     * `responseBuilder` 中根据 header、body、status 处理响应即可，无需关注对 header、body 等的转换。
     * <p/>
     * 默认响应体数据的构造逻辑为：<per>
     * String body = JSON.stringify(response.cloneInstance(false));
     * </per>
     *
     * @param responseBuilder 响应的处理函数，其有以下入参：<ul>
     *                        <li>headers: 响应头 Map 集合，来自于 {@link ApiMessage#getHeaders()}。不为 `null`；</li>
     *                        <li>body: 响应体数据；</li>
     *                        <li>status: 响应状态，来自于 {@link ApiResponse#getHttpStatus()}，在原值为 `0` 时，该入参实际传入 `200`；</li>
     *                        </ul>
     */
    protected <T> CompletionStage<T> runRest(GraphQLOperationType expectedOpType, String operationName,
                                             Supplier<ApiRequest<?>> requestBuilder,
                                             ITriFunction<Map<String, Object>, String, Integer, T> responseBuilder
    ) {
        return runRest(expectedOpType, operationName, requestBuilder, (response, gqlContext) -> {
            return buildRestResponse(response, responseBuilder);
        });
    }


    protected <T> T buildRestResponse(ApiResponse<?> response, ITriFunction<Map<String, Object>, String, Integer, T> responseBuilder) {
        int status = response.getHttpStatus();
        if (status == 0) {
            status = 200;
        }

        String body = JSON.stringify(response.cloneInstance(false));
        Map<String, Object> headers = response.getHeaders();

        return responseBuilder.apply(headers != null ? new HashMap<>(headers) : new HashMap<>(), body, status);
    }

    protected Response buildJaxrsRestResponse(ApiResponse<?> res, IGraphQLExecutionContext context) {
        Response response = JaxrsHelper.buildJaxrsResponse(res);
        LOG.debug("nop.graphql.response:{}", response.getEntity());
        return response;
    }


    protected void prepareContext(IGraphQLExecutionContext context) {

    }

    protected Map<String, String> getParams() {
        return Collections.emptyMap();
    }

    protected Map<String, Object> getHeaders() {
        return Collections.emptyMap();
    }


    protected ApiRequest<Map<String, Object>> buildRequest(String body, String selection, boolean addParams) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        Map<String, Object> map = null;
        if (!StringHelper.isEmpty(body)) {
            map = (Map<String, Object>) JSON.parse(body);
        }
        if (map == null)
            map = new LinkedHashMap<>();

        if (addParams) {
            Map<String, String> params = getParams();
            for (String name : params.keySet()) {
                if (name.startsWith(GraphQLConstants.SYS_PARAM_PREFIX))
                    continue;
                map.put(name, params.get(name));
            }
        }

        request.setHeaders(getHeaders());
        if (!StringHelper.isEmpty(selection)) {
            request.setSelection(parseSelection(selection));
        }

        GraphQLArgsHelper.normalizeSubArgs(request.getSelection(), map);
        request.setData(map);
        return request;
    }

    protected FieldSelectionBean parseSelection(String selection) {
        return new FieldSelectionBeanParser().parseFromText(null, selection);
    }

    /**
     * 统一的分页查询处理和响应转换函数
     * <p/>
     * 该函数对请求数据进行统一解析，并在完成分页处理后，向回调函数
     * `responseBuilder` 提供响应对象 {@link ApiResponse}
     * 和 GraphQL 上下文 {@link IGraphQLExecutionContext}，
     * 可以在该回调函数中调用 {@link #consumeWebContent(ApiResponse, WebContentBean, ITriFunction)}，
     * 并根据 {@link WebContentBean#getContent() 响应内容} 的类型做响应处理。
     */
    protected <T> CompletionStage<T> doPageQuery(GraphQLOperationType operationType,
                                                 String query, String selection, String args,
                                                 BiFunction<ApiResponse<?>, IGraphQLExecutionContext, T> responseBuilder) {
        int pos = query.indexOf('/');
        String operationName = query;
        String path = pos > 0 ? query.substring(pos) : null;
        if (pos > 0) {
            operationName = query.substring(0, pos);
        }

        return runRest(operationType, operationName, () -> {
            ApiRequest<Map<String, Object>> req = buildRequest(args, selection, true);

            if (path != null) {
                req.getData().put(GraphQLConstants.PARAM_PATH, path);
            }
            return req;
        }, responseBuilder);
    }

    protected CompletionStage<ApiResponse<String>> runJsonRpc(String body, Map<String, Object> headers) {
        JsonRpcService service = BeanContainer.getBeanByType(JsonRpcService.class);
        return service.executeAsync(body, headers);
    }

    protected Response buildJaxrsPageResponse(ApiResponse<?> response, IGraphQLExecutionContext context) {
        WebContentBean contentBean = buildWebContent(response);

        return consumeWebContent(response, contentBean, (headers, content, status) -> {
            if (content instanceof IResource) {
                IResource resource = (IResource) content;

                File file = resource.toFile();
                if (file == null) {
                    content = (StreamingOutput) resource::writeToStream;
                } else {
                    content = file;
                }
            }

            return JaxrsHelper.buildJaxrsResponse(headers, content, status);
        });
    }

    /**
     * 统一构造响应状态和响应头，并调用回调函数 `contentConsumer` 完成对响应的处理
     * <p/>
     * 具体使用方式见 {@link #buildJaxrsPageResponse(ApiResponse, IGraphQLExecutionContext)}
     *
     * @param contentBean     通过 {@link #buildWebContent(ApiResponse)} 构造
     * @param contentConsumer 对 {@link WebContentBean#getContent()} 的处理函数，其有如下参数：<ul>
     *                        <li>headers: 响应头 Map 集合，来自于 {@link ApiMessage#getHeaders()}，并已根据
     *                        `contentBean.getContentType()` 和 `contentBean.getFileName()`
     *                        设置 {@link ApiConstants#HEADER_CONTENT_TYPE} 等响应头。不为 `null`；</li>
     *                        <li>content: 响应体数据，来自于 `contentBean.getContent()`；</li>
     *                        <li>status: 响应状态，来自于 {@link ApiResponse#getHttpStatus()}，在原值为 `0` 时，该入参实际传入 `200`；</li>
     *                        </ul>
     */
    protected <T> T consumeWebContent(
            ApiResponse<?> response, WebContentBean contentBean,
            ITriFunction<Map<String, Object>, Object, Integer, T> contentConsumer
    ) {
        return GraphQLResponseHelper.consumeWebContent(response, contentBean, (invokeHeaderSet, content, status) -> {
            Map<String, Object> headers = new HashMap<>();
            invokeHeaderSet.accept(headers::put);

            return contentConsumer.apply(headers, contentBean.getContent(), status);
        });
    }

    /**
     * 根据 {@link ApiResponse#getData()} 的实际类型构造 {@link WebContentBean}
     * <p/>
     * 构造过程中将会对响应数据做 JSON 序列化等转换处理，并同时确定响应头 `Content-Type` 的值
     */
    protected WebContentBean buildWebContent(ApiResponse<?> response) {
        WebContentBean contentBean;

        Object data = response.getData();
        if (data instanceof String) {
            contentBean = new WebContentBean(WebContentBean.CONTENT_TYPE_TEXT, data);
        } else if (data instanceof WebContentBean) {
            contentBean = (WebContentBean) data;
        } else if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;

            if (map.containsKey("contentType") && map.containsKey("content") && map.size() >= 2) {
                String contentType = ConvertHelper.toString(map.get("contentType"));
                Object content = map.get("content");
                String fileName = (String) map.get("fileName");

                if (!(content instanceof String //
                        || content instanceof InputStream //
                        || content instanceof File //
                        || content instanceof byte[] //
                        || content instanceof IResource) //
                ) {
                    content = JSON.stringify(content);
                }

                contentBean = new WebContentBean(contentType, content, fileName);
            } else {
                contentBean = buildWebJsonContent(response);
            }
        } else {
            contentBean = buildWebJsonContent(response);
        }

        if (contentBean != null && contentBean.getContent() instanceof String) {
            LOG.debug("nop.graphql.response:{}", contentBean.getContent());
        }

        return contentBean;
    }

    protected WebContentBean buildWebJsonContent(ApiResponse<?> response) {
        String content;
        if (response.isOk()) {
            content = JSON.stringify(response.getData());
        } else {
            content = JSON.stringify(response.cloneInstance(false));
        }

        return new WebContentBean(WebContentBean.CONTENT_TYPE_JSON + ";charset=UTF-8", content);
    }
}
